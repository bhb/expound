(ns expound.alpha
  "Drop-in replacement for clojure.spec.alpha, with
  human-readable `explain` function"
  (:require [expound.paths :as paths]
            [expound.problems :as problems]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            #?(:cljs [goog.string.format])
            #?(:cljs [goog.string])
            [expound.printer :as printer]))

;;;;;; specs   ;;;;;;

(s/def ::singleton (s/coll-of any? :count 1))

(s/def :spec/spec keyword?)
(s/def :spec/specs (s/coll-of :spec/spec))
(s/def :spec.problem/via (s/coll-of :spec/spec :kind vector?))
(s/def :spec/problem (s/keys :req-un [:spec.problem/via]))
(s/def :spec/problems (s/coll-of :spec/problem))

;;;;;; private ;;;;;;

(def header-size 35)
(def section-size 25)

(def ^:dynamic *value-str-fn* (fn [_ _ _ _] "NOT IMPLEMENTED"))

(s/fdef value-in-context
        :args (s/cat
               :opts map?
               :spec-name (s/nilable #{:args :fn :ret ::s/pred})
               :form any?
               :path :expound/path
               :value any?)
        :ret string?)
(defn value-in-context
  "Given a form and a path into that form, returns a string
   that helps the user understand where that path is located
   in the form"
  [opts spec-name form path value]
  (if (= :fn spec-name)
    (binding [*print-namespace-maps* false] (pr-str form))
    (if (= form value)
      (binding [*print-namespace-maps* false] (printer/pprint-str value))
      ;; It's silly to reconstruct a fake "problem"
      ;; after I've deconstructed it, but I'm not yet ready
      ;; to break the API for value-in-context BUT
      ;; I do want to test that a problems-based API
      ;; is useful.
      ;; See https://github.com/bhb/expound#configuring-the-printer
      (problems/highlighted-value opts
                                  {:expound/form form
                                   :expound/in path}))))

(defn spec-str [spec]
  (if (keyword? spec)
    (printer/format
     "%s:\n%s"
     spec
     (printer/indent (printer/pprint-str (s/form spec))))
    (printer/pprint-str (s/form spec))))

;; via is different when using asserts
(defn spec+via [problem]
  (let [{:keys [via spec]} problem]
    (if (keyword? spec)
      (into [spec] via)
      via)))

(s/fdef specs
        :args (s/cat :problems :spec/problems)
        :ret :spec/specs)
(defn specs
  "Given a collection of problems, returns the specs for those problems, with duplicates removed"
  [problems]
  (->> problems
       (map spec+via)
       flatten
       distinct))

(defn specs-str [problems]
  (->> problems
       specs
       reverse
       (map spec-str)
       (string/join "\n")))

(defn named? [x]
  #?(:clj (instance? clojure.lang.Named x)
     :cljs (implements? cljs.core.INamed x)))

(defn pr-pred* [pred]
  (cond
    (or (symbol? pred) (named? pred))
    (name pred)

    (fn? pred)
    (printer/pprint-fn pred)

    :else
    (printer/elide-core-ns (binding [*print-namespace-maps* false] (printer/pprint-str pred)))))

(defn pr-pred [pred spec]
  (if (= ::s/unknown pred)
    (pr-pred* spec)
    (pr-pred* pred)))

(defn show-spec-name [spec-name value]
  (if spec-name
    (str
     (case spec-name
       ::s/pred "" ; Used in s/assert
       :args "Function arguments\n\n"
       :ret "Return value\n\n"
       :fn "Function arguments and return value\n\n")
     value)
    value))

(defn preds [problems]
  (string/join "\n\nor\n\n" (distinct (map (fn [problem]
                                             (printer/indent
                                              (pr-pred (:pred problem)
                                                       (:spec problem)))) problems))))

(defn insufficient-input [spec-name val path problem]
  (printer/format
   "%s

should have additional elements. The next element is named `%s` and satisfies

%s"
   (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))
   (pr-str (first (:expound/path problem)))
   (printer/indent (pr-pred (:pred problem) (:spec problem)))))

(defn extra-input [spec-name val path]
  (printer/format
   "Value has extra input

%s"
   (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))))

(defn missing-key [form]
  #?(:cljs (let [[contains _arg key-keyword] form]
             (if (contains? #{'cljs.core/contains? 'contains?} contains)
               key-keyword
               (let [[fn _ [contains _arg key-keyword] & rst] form]
                 (s/assert #{'cljs.core/contains? 'contains?} contains)
                 key-keyword)))
     ;; FIXME - this duplicates the structure of how
     ;; spec builds the 'contains?' function. Extract this into spec
     ;; and use conform instead of this ad-hoc validation.
     :clj (let [[_fn _ [contains _arg key-keyword] & _rst] form]
            (s/assert #{'clojure.core/contains?} contains)
            key-keyword)))

(defn label
  ([size]
   (apply str (repeat size "-")))
  ([size s]
   (let [prefix (str "-- " s " ")
         chars-left (- size (count prefix))]
     (str prefix (apply str (repeat chars-left "-"))))))

(def header-label (partial label header-size))
(def section-label (partial label section-size))

(defn relevant-specs [problems]
  (let [sp-str (specs-str problems)]
    (if (string/blank? sp-str)
      ""
      (printer/format
       "%s

%s"
       (section-label "Relevant specs")
       sp-str))))

(defn multi-spec-parts [spec]
  (let [[_multi-spec mm retag]  (s/form spec)]
    {:mm mm :retag retag}))

(defn missing-spec? [_failure problem]
  (= "no method" (:reason problem)))

(defn not-in-set? [_failure problem]
  (set? (:pred problem)))

(defn fspec-exception-failure? [failure problem]
  (and (not= :instrument failure)
       (= '(apply fn) (:pred problem))))

(defn fspec-ret-failure? [failure problem]
  (and
   (not= :instrument failure)
   (= :ret (first (:path problem)))))

(defn fspec-fn-failure? [failure problem]
  (and
   (not= :instrument failure)
   (= :fn (first (:path problem)))))

(defn missing-key? [_failure problem]
  #?(:cljs
     (let [pred (:pred problem)]
       (and (list? pred)
            (map? (:val problem))
            (or (= 'contains? (first pred))
                (let [[fn _ [contains _] & rst] pred]
                  (and
                   (= 'cljs.core/fn fn)
                   (= 'cljs.core/contains? contains))))))
     :clj
     (let [pred (:pred problem)]
       (and (seq? pred)
            (map? (:val problem))
            (let [[fn _ [contains _] & _rst] pred]
              (and
               (= 'clojure.core/fn fn)
               (= 'clojure.core/contains? contains)))))))

(defn regex-failure? [_problem problem]
  (contains? #{"Insufficient input" "Extra input"} (:reason problem)))

(defn no-method [spec-name val path problem]
  (let [sp (s/spec (last (:expound/via problem)))
        {:keys [mm retag]} (multi-spec-parts sp)]
    (printer/format
     "Cannot find spec for

 %s

 Spec multimethod:      `%s`
 Dispatch function:     `%s`
 Dispatch value:        `%s`
 "
     (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))
     (pr-str mm)
     (pr-str retag)
     (pr-str (if retag (retag (problems/value-in val path)) nil)))))

(defmulti problem-group-str (fn [type spec-name _val _path _problems _opts] type))

(defmethod problem-group-str :problem/missing-key [_type spec-name val path problems opts]
  (assert (apply = (map :val problems)) (str "All values should be the same, but they are " problems))
  (printer/format
   "%s

%s

should contain keys: %s

%s"
   (header-label "Spec failed")
   (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))
   (string/join "," (map #(str "`" (missing-key (:pred %)) "`") problems))
   (if (:print-specs? opts) (relevant-specs problems) "")))

(defmethod problem-group-str :problem/not-in-set [_type spec-name val path problems opts]
  (assert (apply = (map :val problems)) (str "All values should be the same, but they are " problems))
  (printer/format
   "%s

%s

should be%s: %s

%s"
   (header-label "Spec failed")
   (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))
   (if (= 1 (count (:pred (first problems)))) "" " one of")
   (string/join "," (map #(str "`" % "`") (:pred (first problems))))
   (if (:print-specs? opts) (relevant-specs problems) "")))

(defmethod problem-group-str :problem/missing-spec [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (printer/format
   "%s

%s

%s"
   (header-label "Missing spec")
   (no-method spec-name val path (first problems))
   (if (:print-specs? opts) (relevant-specs problems) "")))

(defmethod problem-group-str :problem/regex-failure [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "%s

%s

%s"
     (header-label "Syntax error")
     (case (:reason problem)
       "Insufficient input" (insufficient-input spec-name val path problem)
       "Extra input" (extra-input spec-name val path))
     (if (:print-specs? opts) (relevant-specs problems) ""))))

(defmethod problem-group-str :problem/fspec-exception-failure [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "%s

%s

threw exception 

%s

with args:

%s

%s"
     (header-label "Exception")
     (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path)))
     (printer/indent (pr-str (:reason problem)))
     (printer/indent (string/join ", " (:val problem)))
     (if (:print-specs? opts) (relevant-specs problems) ""))))

(defmethod problem-group-str :problem/fspec-ret-failure [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "%s

%s

returned an invalid value

%s

should satisfy

%s

%s"
     (header-label "Function spec failed")
     (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path)))
     (printer/indent (pr-str (:val problem)))
     (printer/indent (pr-pred (:pred problem) (:spec problem)))
     (if (:print-specs? opts) (relevant-specs problems) ""))))

(defmethod problem-group-str :problem/fspec-fn-failure [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "%s

%s

failed spec. Function arguments and return value

%s

should satisfy

%s

%s"
     (header-label "Function spec failed")
     (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path)))
     (printer/indent (pr-str (:val problem)))
     (printer/indent (pr-pred (:pred problem) (:spec problem)))
     (if (:print-specs? opts) (relevant-specs problems) ""))))

(defmethod problem-group-str :problem/unknown [_type spec-name val path problems opts]
  (assert (apply = (map :val problems)) (str "All values should be the same, but they are " problems))
  (printer/format
   "%s

%s

should satisfy

%s

%s"
   (header-label "Spec failed")
   (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))
   (preds problems)
   (if (:print-specs? opts) (relevant-specs problems) "")))

(defn problem-type [failure problem]
  (cond
    (not-in-set? failure problem)
    :problem/not-in-set

    (missing-key? failure problem)
    :problem/missing-key

    (missing-spec? failure problem)
    :problem/missing-spec

    (regex-failure? failure problem)
    :problem/regex-failure

    (fspec-exception-failure? failure problem)
    :problem/fspec-exception-failure

    (fspec-ret-failure? failure problem)
    :problem/fspec-ret-failure

    (fspec-fn-failure? failure problem)
    :problem/fspec-fn-failure

    :else
    :problem/unknown))

(defn safe-sort-by
  "Same as sort-by, but if an error is raised, returns the original unsorted collection"
  [key-fn comp coll]
  (try
    (sort-by key-fn comp coll)
    (catch #?(:cljs :default
              :clj Exception) e coll)))

(defn instrumentation-info [failure caller]
  ;; As of version 1.9.562, Clojurescript does
  ;; not include failure or caller info, so
  ;; if these are null, print a placeholder
  (if (= :instrument failure)
    (printer/format "%s:%s
\n"
                    (:file caller "<filename missing>")
                    (:line caller "<line number missing>"))
    ""))

(defn spec-name [ed]
  (if (#{:instrument} (::s/failure ed))
    (-> ed ::s/problems first :path first)
    nil))

(defn printer-str [opts explain-data]
  (let [opts' (merge {:show-valid-values? false
                      :print-specs? true}
                     opts)]
    (if-not explain-data
      "Success!\n"
      (binding [*value-str-fn* (get opts :value-str-fn (partial value-in-context opts'))]
        (let [{:keys [::s/problems ::s/fn ::s/failure]} explain-data
              explain-data' (problems/annotate explain-data) caller (:expound/caller explain-data')
              form (:expound/form explain-data')

              grouped-problems (->> explain-data'
                                    :expound/problems
                                    (problems/leaf-only)
                                    (group-by (juxt :expound/in (partial problem-type (::s/failure explain-data))))
                                    ;; We attempt to sort the problems by path, but it's not feasible to sort in
                                    ;; all cases, since paths could contain arbitrary user-defined data structures.
                                    ;; If there is an error, we just give up on sorting.
                                    (safe-sort-by first paths/compare-paths))]

          (printer/no-trailing-whitespace
           (str
            (instrumentation-info failure caller)
            (printer/format
             "%s

%s
Detected %s %s\n"
             (string/join "\n\n" (for [[[in type] problems] grouped-problems]
                                   (problem-group-str type (spec-name explain-data) form in problems opts')))
             (section-label)
             (count grouped-problems)
             (if (= 1 (count grouped-problems)) "error" "errors")))))))))

;;;;;; public ;;;;;;

(defn custom-printer
  "Returns a printer, configured via opts"
  [opts]
  (fn [explain-data]
    (print (printer-str opts explain-data))))

(defn printer
  "Prints explain-data in a human-readable format"
  [explain-data]
  ((custom-printer {}) explain-data))

(defn expound-str
  "Given a spec and a value, either returns success message or returns a human-readable explanation as a string."
  [spec form]
  ;; expound was initially released with support
  ;; for CLJS 1.9.542 which did not include
  ;; the value in the explain data, so we patch it
  ;; in to avoid breaking back compat (at least for now)
  (let [explain-data (s/explain-data spec form)]
    (printer-str {}
                 (if explain-data
                   (assoc explain-data
                          ::s/value form)
                   nil))))

(defn expound
  "Given a spec and a value, either prints a success message or prints a human-readable explanation as a string."
  [spec form]
  (print (expound-str spec form)))
