(ns expound.suggest
  (:require [clojure.spec.alpha :as s]
            [expound.problems :as problems]))

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

(defn replacement [spec args in val seed-vals]
  (let [r1 (convert val seed-vals)
        opt1 (combine args in r1)]
    (if (and (not= ::no-value r1) (s/valid? spec opt1))
      opt1
      (let [opt2 (combine args in (simplify val seed-vals))]
        (if (s/valid? spec opt2)
          opt2
          (combine args in (first seed-vals)))))))

(defn suggestion [spec form]
  ;; TODO - avoid double checking by inspecting explain-data
  (if (s/valid? spec form)
    form
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
      (replacement spec form in (:val problem) seed-vals))))

;; TODO - this could be more general than only function specs
(defn valid-args [form]
  (if-let [spec (s/get-spec (first form))]
    (let [args-spec (:args spec)
          args (rest form)]
      (list* (first form)
             (suggestion args-spec args)))
    ::no-spec-found))
