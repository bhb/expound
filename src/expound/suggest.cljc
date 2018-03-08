(ns expound.suggest
  (:require [clojure.spec.alpha :as s]
            [expound.problems :as problems]
            [clojure.test.check.random :as random]
            [clojure.test.check.rose-tree :as rose]
            [clojure.test.check.generators :as gen]))

(def seed 0)
(def rounds 5)
(def good-enough-score 30)
(def num-samples 5)
;; TODO - this will grow without bound. LRU?
;; keep a normal map and keep a persist queue
;; #queue in CLJS, clojure.lang.PersistentQueue/EMPTY in CLJ
;; every write should (if above limit), delete element from
;; map? use priority queue/heap?
(def !example-cache (atom {}))
(def example-values
  ["sally@example.com"
   "http://www.example.com"
   0
   []
   {}])

(defn convert [original replacement]
  (cond
    (and (qualified-symbol? original)
         (simple-symbol? replacement))
    (symbol (name original))

    (and (string? original)
         (simple-symbol? replacement))
    (symbol original)

    (and (keyword? original)
         (simple-keyword? replacement))
    (keyword (name original))

    (and (keyword? original)
         (string? replacement))
    (name original)

    (and (string? original)
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

    ;;;;;;;;;;;;;;; defaults
    (keyword? replacement)
    :keyword

    (string? replacement)
    "string"

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

(defn simplify [seed-vals]
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
(s/def ::type #{::converted ::simplified ::init ::example ::deleted})
(s/def ::types (s/coll-of ::type))
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
                                           ::deleted 2
                                           ::example 3
                                           ::simplified 4
                                           ::init 5)
                                        (::types suggestion)))]
        (if (pos? problem-count)
          (/ (* failure-multiplier problem-count)
             (* problem-depth-multiplier problem-depth))
          (+
           (levenshtein (pr-str init-form) (pr-str form))
           (- (count (pr-str init-form)) (longest-substring-length (pr-str init-form) (pr-str form)))
           types-penalty))))))

(defn sample-seq
  "Return a sequence of realized values from `generator`."
  [generator seed]
  (let [max-size 200
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
        (let [xs (doall (take n (sample-seq (s/gen spec) seed)))]
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
    (if (vector? coll)
      (vec new-coll)
      new-coll)))

(defn deletions [form]
  (if (sequential? form)
    (for [n (range 0 (count form))]
      (drop-idx n form))
    [form]))

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
             ;; TODO - this is a hack that won't work if we have nested specs
              ;; the generated spec could potentially be half-way up the "path" path
             seed-vals (map #(if-let [r (get-in (s/conform most-specific-spec %)
                                                (:path problem))]
                               r
                               %)
                            gen-values)]
         (concat
          (map
           ;; TODO - rename sugg -> form
           (fn [sugg]
             {::form  sugg
              ::types (conj (::types suggestion) ::example)})
           (for [val example-values]
             (combine form in val)))
          (map
           (fn [sugg]
             {::form  sugg
              ::types (conj (::types suggestion) ::converted)})
           (for [seed-val seed-vals]
             (combine form in (convert (:val problem) seed-val))))
          (map
           (fn [sugg]
             {::form  sugg
              ::types (conj (::types suggestion) ::simplified)})
           [(combine form in
                     (simplify seed-vals))])
          (if (= "Extra input" (:reason problem))
            (map
             (fn [sugg]
               {::form sugg
                ::types (conj (::types suggestion) ::deleted)})
             (deletions (::form suggestion)))
            []))))
     problems)))

(defn include? [spec init-form round old-suggestion new-suggestion]
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
                             ::types '(::init)}
                            ((fn [s]
                               (assoc s ::score (score spec init-form s)))))]
    (loop [round rounds
           suggestions #{init-suggestion}]
      (s/assert (s/coll-of ::suggestion) suggestions)
      (s/assert set? suggestions)
      (if (or (zero? round)
              (some #(< (::score %) good-enough-score) suggestions))
        ;; TODO - no need to build vector now that score is included
        (sort-by
         ::score
         ;; Don't depend on ordering of suggestions
              ;; TODO - remove
         (shuffle suggestions))
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
                            (include? spec init-form round suggestion s)))))
                  invalid-suggestions))))))))

(defn suggestion [spec form]
  (let [best-form (::form (first (suggestions spec form)))]
    (if (s/valid? spec best-form)
      best-form
      ::no-suggestion)))

(defn valid-args [form]
  (if-let [spec (s/get-spec (first form))]
    (do
      (let [args-spec (:args spec)
            args (next form)
            sugg (suggestion args-spec args)]
        (if (= ::no-suggestion sugg)
          sugg
          (list* (first form)
                 sugg))))
    ::no-spec-found))
