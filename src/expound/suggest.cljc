(ns expound.suggest
  (:require [clojure.spec.alpha :as s]
            [expound.problems :as problems]
            [clojure.data]))

(defn generate [form]
  (let [args-spec (:args (s/spec (first form)))]
    (map first (s/exercise args-spec 5))))

(defn convert [original replacements]
  (let [replacement (first replacements)]
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

      ;;(neg-int? replacement)
      ;;-1

      ;;(pos-int? replacement)
      ;;1

      :else
      ::no-value)))

(defn abs [x]
  (if (neg? x)
    (* -1 x)
    x))

(defn simplify [val seed-vals]
  (if (every? number? seed-vals)
    (first (sort-by #(abs %) seed-vals))
    (first (sort seed-vals))))

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

(defn score [spec form suggestion]
  (let [failure-multiplier 20
        total-failure 1000000000]
    (if (step-failed? suggestion)
      total-failure
      (let [problem-count (or (some->
                               (s/explain-data spec suggestion)
                               ::s/problems
                               count) 0)]
        (if (pos? problem-count)
          (* failure-multiplier problem-count)
          (levenshtein (pr-str form) (pr-str suggestion)))))))

(defn suggestion1 [spec form]
  (let [ed (problems/annotate (s/explain-data spec form))
        problem (first (:expound/problems ed))
        most-specific-spec (last (:expound/via problem))
        in (:expound/in problem)
        gen-values (map first (s/exercise most-specific-spec 10))
        ;; TODO - this is a hack that won't work if we have nested specs
        ;; the generated spec could potentially be half-way up the "path" path
        seed-vals (map #(if-let [r (get-in (s/conform most-specific-spec %)
                                           (:path problem))]
                          r
                          %)
                       gen-values)]
    (combine form in (convert (:val problem) seed-vals))))

(defn suggestion2 [spec form]
  (let [ed (problems/annotate (s/explain-data spec form))
        problem (first (:expound/problems ed))
        most-specific-spec (last (:expound/via problem))
        in (:expound/in problem)
        gen-values (map first (s/exercise most-specific-spec 10))
        ;; TODO - this is a hack that won't work if we have nested specs
        ;; the generated spec could potentially be half-way up the "path" path
        seed-vals (map #(if-let [r (get-in (s/conform most-specific-spec %)
                                           (:path problem))]
                          r
                          %)
                       gen-values)]
    (combine spec in (simplify (:val problem) seed-vals))))

(defn suggestion3 [spec form]
  (let [ed (problems/annotate (s/explain-data spec form))
        problem (first (:expound/problems ed))
        most-specific-spec (last (:expound/via problem))
        in (:expound/in problem)
        gen-values (map first (s/exercise most-specific-spec 10))
        ;; TODO - this is a hack that won't work if we have nested specs
        ;; the generated spec could potentially be half-way up the "path" path
        seed-vals (map #(if-let [r (get-in (s/conform most-specific-spec %)
                                           (:path problem))]
                          r
                          %)
                       gen-values)]
    (combine form in (first seed-vals))))

(defn suggestion [spec form]
  ;; TODO - avoid double checking by inspecting explain-data
  (if (s/valid? spec form)
    form
    (let [sug1 (suggestion1 spec form)
          sug2 (suggestion2 spec form)
          sug3 (suggestion3 spec form)]
      (first (sort-by
              #(score spec form %)
              [sug1
               sug2
               sug3])))));; TODO - this could be more general than only function specs
(defn valid-args [form]
  (if-let [spec (s/get-spec (first form))]
    (let [args-spec (:args spec)
          args (rest form)]
      (list* (first form)
             (suggestion args-spec args)))
    ::no-spec-found))
