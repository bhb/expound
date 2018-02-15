(ns expound.suggest
  (:require [clojure.spec.alpha :as s]
            [expound.problems :as problems]))

(defn generate [form]
  (let [args-spec (:args (s/spec (first form)))]
    (map first (s/exercise args-spec 5))))

(comment
  (require '[clojure.core.specs.alpha :as specs])

  (s/form (:args (s/spec 'clojure.core/let)))

  (s/explain-data
   (:args (s/spec 'clojure.core/let))
   `[foo/bar 1])

  (s/exercise :clojure.core.specs.alpha/bindings 10) (generate `(let [foo/bar 1]))

  (+ 1 1))

(defn convert [original replacement]
  (cond
    (and (qualified-symbol? original)
         (simple-symbol? replacement))
    (symbol (name original))

    (and (string? original)
         (simple-symbol? replacement))
    (symbol original)

    ;; defaults
    (string? replacement)
    "string"

    (simple-symbol? replacement)
    'symbol

    (qualified-symbol? replacement)
    'ns/symbol

    (nat-int? replacement)
    0

    (pos-int? replacement)
    1

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

(comment
  ;; Hmm.. we want to generate the most narrow spec to increase chances of generation
  ;; but then we don't really know exactly how to connect the generated data to the
  ;; value because the last spec could be anywhere in the "path". Right now I have
  ;; cases where it is top first or last part of path, but it could be middle, I think.

  (s/form (:spec problem))

  (type (:pred problem))

  (ffirst (s/exercise (last (:expound/via problem))))

  (s/conform (last (:expound/via problem)) (ffirst (s/exercise (last (:expound/via problem)))))

  (s/form (last (:expound/via problem)))

  (s/fdef example-fn
          :args (s/cat :a simple-symbol?
                       :b string?
                       :c keyword?
                       :d int?
                       :e qualified-symbol?))
  (defn example-fn [a b c d])

  (valid-args `(example-fn a/b "b" :c 1 a/b))

  (valid-args `(example-fn ~'b "b" :c "foo" a/b))

  (require '[sc.api])
  (sc.api/defsc 9)

  (valid-args `(let [foo/bar 1]))

  (problems/assoc-in1))

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
