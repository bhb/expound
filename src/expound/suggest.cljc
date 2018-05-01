(ns expound.suggest
  (:require [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [expound.problems :as problems]
            [clojure.test.check.random :as random]
            [clojure.test.check.rose-tree :as rose]
            [clojure.test.check.generators :as gen]
            #?(:cljs [expound.js.util]))
  #?(:clj (:import [expound.java Util])))

(def init-seed 0)
(def generation-rounds 5)
(def simplify-rounds 20)
(def max-suggestions 100)
(def good-enough-score 0) ;; TODO - this seem to have no impact whether at zero or twenty
(def num-samples 5)
(def good-enough-simplifed-score 0) ;; TODO - this seems to have no impact whether at 0 or 20
(def max-simplified-suggestions 100)

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

    (and (vector? original)
         (or (seq? replacement) (list? replacement)))
    (list* original)

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

(defn levenshtein [w1 w2]
  #?(:clj (Util/distance w1 w2)
     :cljs (expound.js.util/levenshtein w1 w2)))

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
  (let [{:keys [::form]} suggestion
        ;; Even if a form is invalid, it may have no problems
        ;; See https://dev.clojure.org/jira/browse/CLJ-2336
        failure-constant 200
        problem-multiplier 100
        problem-depth-multiplier 1
        total-failure 1000000000]
    (if (step-failed? form)
      total-failure
      (let [valid? (s/valid? spec form)
            ed (if valid?
                 nil
                 (s/explain-data spec form))
            problem-count (or (some->
                               ed
                               ::s/problems
                               count) 0)
            problem-depth   (some->>
                             ed
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
                                           ::swapped 5
                                           ::deleted 6
                                           ::inserted 7
                                           ::base 8
                                           ::converted-then-simplified 0
                                           ::simplified 10
                                           ::generated 11
                                           ::init 100)
                                        (::types suggestion)))
            init-form-str (pr-str init-form)
            form-str (pr-str form)
            temp-todo-replace (+
                               (levenshtein init-form-str form-str)
                               (- (count init-form-str) (longest-substring-length init-form-str form-str))
                               types-penalty)]
        (+ (if valid?
             0
             failure-constant)
           (if (pos? problem-count)
             (+ temp-todo-replace
                (/ (* problem-multiplier problem-count)
                   (* problem-depth-multiplier problem-depth)))
             temp-todo-replace))))))

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

(defn form->suggestion [init-suggestion form form-type]
  {::form form
   ::types (conj (::types init-suggestion) form-type)})

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
          (for [val base-values]
            (form->suggestion suggestion
                              (combine form in val)
                              ::base))
          (for [seed-val seed-vals]
            (form->suggestion suggestion
                              (combine form in (convert (:val problem) seed-val))
                              ::converted))
          (for [seed-val seed-vals]
            (form->suggestion suggestion
                              (combine form in seed-val)
                              ::generated))
          (map
           (fn [form]
             (form->suggestion suggestion
                               form
                               ::swapped))
           (swaps (::form suggestion)))
          (if (= "Extra input" (:reason problem))
            (map
             (fn [form]
               (form->suggestion suggestion
                                 form
                                 ::deleted))
             (deletions (::form suggestion)))
            [])
          (if (= "Insufficient input" (:reason problem))
            (map
             (fn [form]
               (form->suggestion suggestion
                                 form
                                 ::inserted))
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
              (some #(< (::score %) good-enough-score) suggestions)
              (< max-suggestions (count suggestions)))
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
                    (into []
                          (comp (map
                                 (fn [s]
                                   (assoc s ::score (score spec init-form s))))
                                (filter
                                 (fn [s]
                                   (include? generation-rounds spec init-form round suggestion s))))
                          (suggestions* !example-cache spec suggestion))))
                 invalid-suggestions)))))))

(defn map-entry-ish? [x]
  ;; Older versions of CLJS don't have map-entry?
  #?(:clj (map-entry? x)
     :cljs (or (instance? RedNode x)
               (instance? BlackNode x))))

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
         (if (map? x)
           ;; Force maps to be sorted-maps so we can check if
           ;; key-value pairs are RedNode or BlackNode, since
           ;; older versions of CLJS don't support map-entry?
           #?(:cljs (into (sorted-map) x)
              :clj x)
           x)))
     form)))

(defn simplified-suggestions [spec init-form suggestions]
  (let [!seed (atom init-seed)]
    (loop [round simplify-rounds
           suggestions' (set suggestions)]
      (if (or (zero? round)
              (some #(< (::score %) good-enough-simplifed-score) suggestions')
              (< max-simplified-suggestions (count suggestions')))
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
                       (filter
                        (fn [s]
                          (include? simplify-rounds spec init-form round suggestion s)))))
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
