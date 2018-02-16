(ns expound.suggest
  (:require [clojure.spec.alpha :as s]
            [expound.problems :as problems]))

(defn generate [form]
  (let [args-spec (:args (s/spec (first form)))]
    (map first (s/exercise args-spec 5))))

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

    (nat-int? replacement)
    0

    (neg-int? replacement)
    -1

    :else
    replacement))

;; TODO - this could be more general than only function specs
(defn valid-args [form]
  (if-let [spec (s/get-spec (first form))]
    (let [args-spec (:args spec)
          args (rest form)]
      ;; TODO - avoid double checking by inspecting explain-data
      (if (s/valid? args-spec args)
        "Success!!!!"
        (let [ed (problems/annotate (s/explain-data args-spec args))
              problem (first (:expound/problems ed))
              most-specific-spec (last (:expound/via problem))
              in (:expound/in problem)
              gen-value (ffirst (s/exercise most-specific-spec 1))
              ;; TODO - this is a hack that won't work if we have nested specs
              ;; the generated spec could potentially be half-way up the "path" path
              replacement (if-let [r (get-in (s/conform most-specific-spec gen-value)
                                             (:path problem))]
                            r
                            gen-value)]
          (list* (first form)
                 (problems/assoc-in1
                  args
                  in
                  (convert (:val problem) replacement))))))
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
