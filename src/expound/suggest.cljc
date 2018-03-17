(ns expound.suggest
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [expound.problems :as problems]
            [clojure.test.check.random :as random]
            [clojure.test.check.rose-tree :as rose]
            [clojure.test.check.generators :as gen]))

(def init-seed 0)
(def generation-rounds 5)
(def simplify-rounds 20)
(def good-enough-score 20)
(def num-samples 5)
(def base-values
  [0
   nil
   ""
   []
   #{}])

;; TODO - this will grow without bound. LRU?
;; keep a normal map and keep a persist queue
;; #queue in CLJS, clojure.lang.PersistentQueue/EMPTY in CLJ
;; every write should (if above limit), delete element from
;; map? use priority queue/heap?
(def !example-cache (atom {}))

(defn convert [original replacement]
  (cond
    (and (qualified-symbol? original)
         (simple-symbol? replacement))
    (symbol (name original))

    (and (string? original)
         (not (re-find #"\s" original))
         (simple-symbol? replacement))
    (symbol original)

    (and (keyword? original)
         (simple-keyword? replacement))
    (keyword (name original))

    (and (keyword? original)
         (string? replacement))
    (name original)

    (and (string? original)
         (not (re-find #"\s" original))
         (simple-keyword? replacement))
    (keyword original)

    (and (simple-symbol? original)
         (simple-keyword? replacement))
    (keyword original)

    (and (symbol? original)
         (string? replacement))
    (name original)

    (and (symbol? original)
         (simple-keyword? replacement))
    (keyword (name original))

    (and (and (not (map? original))
              (coll? original)
              (= 1 (count original))
              (not (coll? replacement))))
    (first original)

    (and (and (not (map? replacement))
              (coll? replacement)
              (not (coll? original))))
    (conj (empty replacement) original)

    (and (or (seq? original) (list? original))
         (vector? replacement))
    (vec original)

    ;;;;;;;;;;;;;;; defaults
    (keyword? replacement)
    :keyword

    (string? replacement)
    (str original)

    (simple-symbol? replacement)
    'symbol

    (qualified-symbol? replacement)
    'ns/symbol

    :else
    ::no-value))

(defn abs [x]
  (if (neg? x)
    (* -1 x)
    x))

(defn pick-simplest [seed-vals]
  (if (every? number? seed-vals)
    (first (sort-by abs seed-vals))
    (first (sort-by pr-str seed-vals))))

(defn combine [args in replacement]
  (problems/assoc-in1
   args
   in
   replacement))

;; https://rosettacode.org/wiki/Levenshtein_distance#Iterative_version
(defn levenshtein [w1 w2]
  (letfn [(cell-value [same-char? prev-row cur-row col-idx]
            (min (inc (nth prev-row col-idx))
                 (inc (last cur-row))
                 (+ (nth prev-row (dec col-idx)) (if same-char?
                                                   0
                                                   1))))]
    (loop [row-idx  1
           max-rows (inc (count w2))
           prev-row (range (inc (count w1)))]
      (if (= row-idx max-rows)
        (last prev-row)
        (let [ch2           (nth w2 (dec row-idx))
              next-prev-row (reduce (fn [cur-row i]
                                      (let [same-char? (= (nth w1 (dec i)) ch2)]
                                        (conj cur-row (cell-value same-char?
                                                                  prev-row
                                                                  cur-row
                                                                  i))))
                                    [row-idx] (range 1 (count prev-row)))]
          (recur (inc row-idx) max-rows next-prev-row))))))

(defn step-failed? [suggestion]
  (some #(= ::no-value %)
        (tree-seq coll? seq suggestion)))

;; TODO - move specs to the top
(s/def ::type #{::base ::converted ::simplified ::init ::deleted ::inserted ::converted-then-simplified ::generated ::swapped})
(s/def ::types (s/coll-of ::type :kind vector?))
(s/def ::form any?)
(s/def ::score pos?)
(s/def ::suggestion (s/keys
                     :req [::types ::form]
                     :opt [::score]))

(defn longest-substring-length [s1 s2]
  (count (take-while true? (map = s1 s2))))

;; Lower score is better
(defn score [spec init-form suggestion]
  (s/assert
   ::suggestion
   suggestion)
  (let [{:keys [::form]} suggestion
        failure-multiplier 100
        problem-depth-multiplier 1
        total-failure 1000000000]
    (if (step-failed? form)
      total-failure
      (let [problem-count (or (some->
                               (s/explain-data spec form)
                               ::s/problems
                               count) 0)
            problem-depth   (some->>
                             (s/explain-data spec form)
                             ::s/problems
                             (mapcat
                              :in)
                             (filter int?)
                             (apply +)
                             inc
                             ;; TODO - need to use real path record shere
)
            types-penalty (apply + (map #(case %
                                           ::converted 1
                                           ::swapped 2
                                           ::deleted 3
                                           ::inserted 4
                                           ::base 5
                                           ::converted-then-simplified 6
                                           ::simplified 7
                                           ::generated 8
                                           ::init 100)
                                        (::types suggestion)))
            temp-todo-replace (+
                               (levenshtein (pr-str init-form) (pr-str form))
                               (- (count (pr-str init-form)) (longest-substring-length (pr-str init-form) (pr-str form)))
                               types-penalty)]
        (if (pos? problem-count)
          (+ temp-todo-replace
             (/ (* failure-multiplier problem-count)
                (* problem-depth-multiplier problem-depth)))
          temp-todo-replace)))))

(defn sample-seq
  "Return a sequence of realized values from `generator`."
  [generator seed]
  (let [max-size 1
        r (if seed
            (random/make-random seed)
            (random/make-random))
        size-seq (gen/make-size-range-seq max-size)]
    (map #(rose/root (gen/call-gen generator %1 %2))
         (gen/lazy-random-states r)
         size-seq)))

(defn safe-generate [!cache spec n]
  (try
    (let [frm (s/form spec)]
      (if-let [xs (get @!cache frm)]
        xs
        (let [xs (sort-by (comp count pr-str) (take n (sample-seq (s/gen spec) init-seed)))]
          (when-not (= ::s/unknown frm)
            (swap! !cache assoc frm xs))
          xs)))
    (catch #?(:cljs :default
              :clj Exception) e
      (if (= #?(:cljs (.-message e)
                :clj (.getMessage e))
             "Couldn't satisfy such-that predicate after 100 tries.")
        (safe-generate !cache spec (dec n))
        (throw e)))))

(defn drop-idx [n coll]
  (let [new-coll (concat
                  (take n coll)
                  (drop (inc n) coll))]
    (cond
      (string? coll)
      (apply str new-coll)

      (vector? coll)
      (vec new-coll)

      :else
      new-coll)))

(defn deletions [form]
  (if (sequential? form)
    (for [n (range 0 (count form))]
      (drop-idx n form))
    [form]))

(defn insert-idx [n x coll]
  (let [new-coll (concat
                  (take n coll)
                  [x]
                  (drop n coll))]
    (if (vector? coll)
      (vec new-coll)
      new-coll)))

(defn insertions [val form]
  (if (sequential? form)
    (for [n (range 0 (inc (count form)))]
      (insert-idx n val form))
    [form]))

(defn swap-idx [n coll]
  (let [[x y & rst] (drop n coll)
        new-coll (concat
                  (take n coll)
                  [y]
                  [x]
                  rst)]
    (if (vector? coll)
      (vec new-coll)
      new-coll)))

(defn swaps [form]
  (if (sequential? form)
    (for [n (range 0 (dec (count form)))]
      (swap-idx n form))
    [form]))

(defn all-values-in [v in-path vals]
  (if (empty? in-path)
    vals
    (try
      (all-values-in
       v
       (rest in-path)
       (conj vals (problems/value-in v in-path)))
      (catch #?(:cljs :default
                :clj Exception) e
        (all-values-in
         v
         (rest in-path)
         vals)))))

(defn suggestions* [!cache spec suggestion]
  (s/assert ::suggestion suggestion)
  (let [form (::form suggestion)
        ed (problems/annotate (s/explain-data spec form))
        problems (:expound/problems ed)]
    (mapcat
     (fn [problem]
       (let [most-specific-spec (last (:expound/via problem))
             in (:expound/in problem)
             gen-values (if (set? most-specific-spec)
                          most-specific-spec
                          (safe-generate !cache most-specific-spec num-samples))
             ;; TODO - this dupes check below
             seed-vals (if (= "Extra input" (:reason problem))
                         []
                         (mapcat
                          (fn [v]
                            ;; Not sure how to determine if we should look in
                            ;; generated value or not, so just generate both.
                            ;; TODO: We could also generate for every subpath, since
                            ;; we don't know the depth of the spec
                            (all-values-in v (:expound/in problem) [v]))
                          gen-values))]
         (concat
          (map
           (fn [form]
             {::form form
              ::types (conj (::types suggestion) ::base)})
           (for [val base-values]
             (combine form in val)))
          (map
           (fn [form]
             {::form form
              ::types (conj (::types suggestion) ::converted)})
           (for [seed-val seed-vals]
             (combine form in (convert (:val problem) seed-val))))
          (map
           (fn [form]
             {::form form
              ::types (conj (::types suggestion) ::generated)})
           (for [seed-val seed-vals]
             (combine form in seed-val)))
          (map
           (fn [form]
             {::form form
              ::types (conj (::types suggestion) ::swapped)})
           (swaps (::form suggestion)))
          (if (= "Extra input" (:reason problem))
            (map
             (fn [form]
               {::form form
                ::types (conj (::types suggestion) ::deleted)})
             (deletions (::form suggestion)))
            [])
          (if (= "Insufficient input" (:reason problem))
            (map
             (fn [form]
               {::form form
                ::types (conj (::types suggestion) ::inserted)})
             (insertions (first seed-vals) (::form suggestion)))
            []))))
     problems)))

(defn include? [rounds spec init-form round old-suggestion new-suggestion]
  (let [old-score (::score old-suggestion)
        new-score (::score new-suggestion)
        strict-improvement-round (* rounds (/ 1 3))]
    (cond
      (< 0 round strict-improvement-round)
      (< new-score old-score)

      (< strict-improvement-round round rounds)
      (< (- new-score (* round 1.1))
         old-score)

      :else
      (< (- new-score (* round 2))
         old-score))))

(defn suggestions [spec init-form]
  (let [init-suggestion (-> {::form  init-form
                             ::types [::init]}
                            ((fn [s]
                               (assoc s ::score (score spec init-form s)))))]
    (loop [round generation-rounds
           suggestions #{init-suggestion}]
      (s/assert (s/coll-of ::suggestion) suggestions)
      (s/assert set? suggestions)
      (if (or (zero? round)
              (some #(< (::score %) good-enough-score) suggestions))
        ;; TODO - no need to build vector now that score is included
        ;; TODO - don't sort twice? Or maybe do, for debugging
        (sort-by
         ::score
         suggestions)
        (let [invalid-suggestions (remove (fn [sg] (s/valid? spec (::form sg)))
                                          suggestions)]
          (recur
           (dec round)
           (into suggestions
                 (mapcat
                  (fn [suggestion]
                    (->> (suggestions* !example-cache spec suggestion)
                         (map
                          (fn [s]
                            (assoc s ::score (score spec init-form s))))
                         (filter
                          (fn [s]
                            (include? generation-rounds spec init-form round suggestion s)))))
                  invalid-suggestions))))))))

(defn map-entry-ish? [x]
  ;; TODO - obviously, this is a terrible hack
  ;; apparently new CLJS gets a real implementation of map-entry
  #?(:clj (map-entry? x)
     :cljs (and (vector? x)
                (= 2 (count x)))))

(defn simplify1 [seed form]
  ;; TODO - won't work in CLJS, because map entries aren't distinct
  (cond
    (string? form)
    (drop-idx (first (sample-seq
                      (gen/large-integer* {:min 0 :max (count form)})
                      seed)) form)

    (and
     ;; TODO - won't work in CLJS, so either upgrade to latest
     ;; CLJS or workaround
     (not (map-entry-ish? form))
     (sequential? form))
    ;; TODO - control seed here
    (drop-idx (first (sample-seq
                      (gen/large-integer* {:min 0 :max (count form)})
                      seed)) form)

    (pos-int? form)
    (dec form)

    (neg-int? form)
    (inc form)

    :else
    form))

(defn simplify [!seed form]
  (swap! !seed inc)
  (let [total-elements (count (tree-seq coll? seq form))
        !items-left (atom
                     (first
                      (sample-seq
                       (gen/frequency
                        ;; bias towards earlier elements
                        [[10 (gen/return 0)]
                         [3 (gen/large-integer* {:min 0
                                                 :max (* total-elements (/ 1 3))})]
                         [2 (gen/large-integer* {:min 0
                                                 :max (* total-elements (/ 2 3))})]
                         [1 (gen/large-integer* {:min 0
                                                 :max (* total-elements (/ 3 3))})]])
                       @!seed)))]
    (walk/prewalk
     (fn [x]
       (swap! !items-left dec)
       (if (zero? @!items-left)
         (simplify1 @!seed x)
         x))
     form)))

(defn simplified-suggestions [spec init-form suggestions]
  (let [!seed (atom init-seed)]
    (loop [round simplify-rounds
           suggestions' (set suggestions)]
      (s/assert set? suggestions')
      (if (or (zero? round)
              ;; TODO - make 20 a constant
              (some #(< (::score %) 20) suggestions')
              (< 10000 (count suggestions')))
        (sort-by ::score suggestions')
        (recur
         (dec round)
         (into suggestions'
               (mapcat
                (fn [suggestion]
                  ;; TODO - no need to wrap in vector and then map
                  (->> [(update suggestion ::form (partial simplify !seed))]
                       ;; If we've simplified this, it's no longer
                       ;; a conversion from their data type
                       (map
                        (fn [s]
                          (if (= suggestion s)
                            s
                            (assoc s ::types
                                   (let [types (mapv
                                                (fn [t]
                                                  (if (= t ::converted)
                                                    ::converted-then-simplified
                                                    t))
                                                (::types s))]
                                     (if (some #{::simplified} types)
                                       types
                                       (conj types ::simplified)))))))

                       (map
                        (fn [s]
                          (assoc s ::score (score spec init-form s))))
                       ;; TODO - restore filter
                       #_(filter
                          (fn [s]
                            (include? spec init-form round suggestion s)))))
                suggestions')))))))

(defn suggestion [spec form]
  (if-let [best-sugg (->> (suggestions spec form)
                          (simplified-suggestions spec form)
                          (filter #(s/valid? spec (::form %)))
                          first)]
    (::form best-sugg)
    ::no-suggestion))

(defn valid-args [form]
  (if-let [spec (s/get-spec (first form))]
    (let [args-spec (:args spec)
          args (next form)
          sugg (suggestion args-spec args)]
      (if (= ::no-suggestion sugg)
        sugg
        (list* (first form)
               sugg)))
    ::no-spec-found))
