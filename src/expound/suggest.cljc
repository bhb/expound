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
  (prn [:bhb seed-vals])
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
          (first seed-vals))))))

(defn suggest [args-spec args]
  ;; TODO - avoid double checking by inspecting explain-data
  (if (s/valid? args-spec args)
    "Success!!!!" ;; TODO - return something more useful here
    (let [ed (problems/annotate (s/explain-data args-spec args))
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
      (replacement args-spec args in (:val problem) seed-vals))))

;; TODO - this could be more general than only function specs
(defn valid-args [form]
  (if-let [spec (s/get-spec (first form))]
    (let [args-spec (:args spec)
          args (rest form)]
      (list* (first form)
             (suggest args-spec args)))
    ::no-spec-found))

#_(defn suggest [form]
    (let [args-spec (:args (s/spec (first form)))
          args (rest form)]
    ;; TODO - avoid double checking by inspecting explain-data
      (if (s/valid? args-spec args)
        "Success!!!!"
        (let [ed (s/explain-data args-spec args)
              valid-forms (map first (s/exercise args-spec 5))
              merged-forms (flatten (map
                                     n                               #(candidates % args)
                                     valid-forms))
              valid-forms (filter #(s/valid? args-spec %) merged-forms)
              suggestion
              (if (seq valid-forms)
                (apply list (first form) (first (sort-by
                                                 #(count (tree-seq coll? seq %))
                                                 valid-forms)))
                ::none)]
          {:ed ed
           :suggestion suggestion
           :merged-forms merged-forms
           :valid-forms valid-forms
           :valid? (if (= ::none suggestion)
                     ::na
                     (s/valid? args-spec (rest suggestion)))}))))
