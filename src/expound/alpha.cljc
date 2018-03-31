(ns expound.alpha
  "Drop-in replacement for clojure.spec.alpha, with
  human-readable `expound` function"
  (:require [expound.paths :as paths]
            [expound.problems :as problems]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.set :as set]
            #?(:cljs [goog.string.format])
            #?(:cljs [goog.string])
            [expound.printer :as printer]
            [expound.util :as util]
            [expound.ansi :as ansi]))

;;;;;; registry ;;;;;;

(defonce ^:private registry-ref (atom {}))

;;;;;; internal specs ;;;;;;

(s/def ::singleton (s/coll-of any? :count 1))
(s/def :spec/spec keyword?)
(s/def :spec/specs (s/coll-of :spec/spec))
(s/def :spec.problem/via (s/coll-of :spec/spec :kind vector?))
(s/def :spec/problem (s/keys :req-un [:spec.problem/via]))
(s/def :spec/problems (s/coll-of :spec/problem))

(s/def :expound.printer/show-valid-values? boolean?)
(s/def :expound.printer/value-str-fn ifn?)
(s/def :expound.printer/print-specs? boolean?)
(s/def :expound.printer/theme #{:figwheel-theme :none})
(s/def :expound.printer/opts (s/keys
                              :opt-un [:expound.printer/show-valid-values?
                                       :expound.printer/value-str-fn
                                       :expound.printer/print-specs?
                                       :expound.printer/theme]))

;;;;;; themes ;;;;;;

(def figwheel-theme
  {:highlight   [:bold]
   :good        [:green]
   :good-pred   [:green]
   :good-key    [:green]
   :bad         [:red]
   :bad-value   [:red]
   :error-key   [:red]
   :focus-key   [:bold]
   :correct-key [:green]
   :header      [:cyan]
   :footer      [:cyan]
   :warning-key [:bold]
   :focus-path  [:magenta]
   :message     [:magenta]
   :pointer     [:magenta]
   :none        [:none]})

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
    (binding [*print-namespace-maps* false] (ansi/color (pr-str form) :bad-value))
    (if (= form value)
      (binding [*print-namespace-maps* false] (ansi/color (printer/pprint-str value) :bad-value))
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
                                              (ansi/color
                                               (pr-pred (:pred problem)
                                                        (:spec problem))
                                               :good-pred))) problems))))

(defn error-message [k]
  [k]
  (get @registry-ref k))

(defn spec-w-error-message? [via pred]
  (boolean (let [last-spec (last via)]
             (and (not= ::s/unknown pred)
                  (error-message last-spec)
                  (s/get-spec last-spec)))))

(defn predicate-errors [problems]
  (let [[with-msg no-msgs] ((juxt filter remove)
                            (fn [{:keys [expound/via pred]}]
                              (spec-w-error-message? via pred))
                            problems)]
    (string/join
     "\n\nor\n\n"
     (remove nil?
             (conj (keep
                    (fn [{:keys [expound/via]}]
                      (ansi/color (error-message (last via)) :good))
                    with-msg)
                   (when (seq no-msgs)
                     (printer/format
                      "should satisfy

%s"
                      (preds no-msgs))))))))

(defn label
  ([size]
   (apply str (repeat size "-")))
  ([size s]
   (ansi/color
    (let [prefix (str "-- " s " ")
          chars-left (- size (count prefix))]
      (str prefix (apply str (repeat chars-left "-"))))
    :header)))

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

(defn multi-spec-parts [spec-form]
  (let [[_multi-spec mm retag] spec-form]
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
  (let [pred (:pred problem)]
    (and (seq? pred)
         (< 2 (count pred))
         (s/valid?
          :spec/contains-key-pred
          (nth pred 2)))))

(defn insufficient-input? [_failure problem]
  (contains? #{"Insufficient input"} (:reason problem)))

(defn extra-input? [_failure problem]
  (contains? #{"Extra input"} (:reason problem)))

(defn multi-spec [pred spec]
  (->> (s/form spec)
       (tree-seq coll? seq)
       (filter #(and (sequential? %)
                     (<= 2 (count %))
                     (= ::s/multi-spec (keyword (first %)))
                     (= pred (second %))))
       first))

(defn no-method [spec-name val path problem]
  (let [sp (s/spec (last (:expound/via problem)))
        {:keys [mm retag]} (multi-spec-parts
                            (multi-spec (:pred problem) sp))]
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
(defmulti expected-str (fn [type  spec-name _val _path _problems _opts] type))

(defn explain-missing-keys [problems]
  (let [missing-keys (map #(printer/missing-key (:pred %)) problems)]
    (str (printer/format
          "should contain %s: %s"
          (if (and (= 1 (count missing-keys))
                   (every? keyword missing-keys))
            "key"
            "keys")
          (printer/print-missing-keys problems))
         (if-let [table (printer/print-spec-keys problems)]
           (str "\n\n" table)
           nil))))

(defmethod expected-str :problem/missing-key [_type spec-name val path problems opts]
  (explain-missing-keys problems))

(defmethod problem-group-str :problem/missing-key [_type spec-name val path problems opts]
  (assert (apply = (map :val problems)) (str util/assert-message ": All values should be the same, but they are " problems))
  (printer/format
   "%s

%s

%s"
   (header-label "Spec failed")
   (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))
   (expected-str _type spec-name val path problems opts)))

(defmethod expected-str :problem/not-in-set [_type _spec-name _val _path problems _opts]
  (let [combined-set (apply set/union (map :pred problems))]
    (printer/format
     "should be%s: %s"
     (if (= 1 (count combined-set)) "" " one of")
     (ansi/color (string/join ", " (map #(ansi/color % :good) (sort (map #(str "" (pr-str %) "") combined-set))))
                 :good))))

(defmethod problem-group-str :problem/not-in-set [_type spec-name val path problems opts]
  (assert (apply = (map :val problems)) (str util/assert-message ": All values should be the same, but they are " problems))
  (printer/format
   "%s

%s

%s"
   (header-label "Spec failed")
   (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))
   (expected-str _type spec-name val path problems opts)))

(defmethod expected-str :problem/missing-spec [_type spec-name val path problems opts]
  (->> problems
       (map #(no-method spec-name val path %))
       (string/join "\n")))

(defmethod problem-group-str :problem/missing-spec [_type spec-name val path problems opts]
  (printer/format
   "%s

%s"
   (header-label "Missing spec")
   (expected-str _type spec-name val path problems opts)))

(defn safe-sort-by
  "Same as sort-by, but if an error is raised, returns the original unsorted collection"
  [key-fn comp coll]
  (try
    (sort-by key-fn comp coll)
    (catch #?(:cljs :default
              :clj Exception) e coll)))

(defn problem-type [failure problem]
  (cond
    (insufficient-input? failure problem)
    :problem/insufficient-input

    (extra-input? failure problem)
    :problem/extra-input

    (not-in-set? failure problem)
    :problem/not-in-set

    (missing-key? failure problem)
    :problem/missing-key

    (missing-spec? failure problem)
    :problem/missing-spec

    (fspec-exception-failure? failure problem)
    :problem/fspec-exception-failure

    (fspec-ret-failure? failure problem)
    :problem/fspec-ret-failure

    (fspec-fn-failure? failure problem)
    :problem/fspec-fn-failure

    :else
    :problem/unknown))

(defn grouped-and-sorted-problems [failure problems]
  (->> problems
       (group-by (juxt :expound/in (partial problem-type failure)))
       ;; We attempt to sort the problems by path, but it's not feasible to sort in
       ;; all cases, since paths could contain arbitrary user-defined data structures.
       ;; If there is an error, we just give up on sorting.
       (safe-sort-by first paths/compare-paths)))

(defmethod expected-str :problem/insufficient-input [_type spec-name val path problems opts]
  (let [problem (first problems)]
    (printer/format
     "should have additional elements. The next element%s %s"
     (if-some [el-name (first (:expound/path problem))]
       (str " \"" (pr-str el-name) "\"")
       "")
     (let [failure nil
           non-matching-value [:expound/value-that-should-never-match]
           new-problems (grouped-and-sorted-problems failure (map #(dissoc % :reason) problems))]
       (string/join "\n\nor "
                    (for [[[in type] problems'] new-problems]
                      (expected-str type :expound/no-spec-name non-matching-value in problems' opts)))))))

(defmethod problem-group-str :problem/insufficient-input [_type spec-name val path problems opts]
  (printer/format
   "%s

%s

%s"
   (header-label "Syntax error")
   (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))
   (expected-str _type spec-name val path problems opts)))

(defmethod expected-str :problem/extra-input [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    "has extra input"))

(defmethod problem-group-str :problem/extra-input [_type spec-name val path problems opts]
  (printer/format
   "%s

%s

%s"
   (header-label "Syntax error")
   (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))
   (expected-str _type spec-name val path problems opts)))

(defmethod expected-str :problem/fspec-exception-failure [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "threw exception 

%s

with args:

%s"
     (printer/indent (pr-str (:reason problem)))
     (printer/indent (string/join ", " (:val problem))))))

(defmethod problem-group-str :problem/fspec-exception-failure [_type spec-name val path problems opts]
  (printer/format
   "%s

%s

%s"
   (header-label "Exception")
   (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path)))
   (expected-str _type spec-name val path problems opts)))

(defmethod expected-str :problem/fspec-ret-failure [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "returned an invalid value

%s

%s"
     (ansi/color (printer/indent (pr-str (:val problem))) :bad-value)
     (predicate-errors problems))))

(defmethod problem-group-str :problem/fspec-ret-failure [_type spec-name val path problems opts]
  (printer/format
   "%s

%s

%s"
   (header-label "Function spec failed")
   (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path)))
   (expected-str _type spec-name val path problems opts)))

(defmethod expected-str :problem/fspec-fn-failure [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "failed spec. Function arguments and return value

%s

should satisfy

%s"
     (printer/indent (ansi/color (pr-str (:val problem)) :bad-value))
     (printer/indent (ansi/color (pr-pred (:pred problem) (:spec problem)) :good-pred)))))

(defmethod problem-group-str :problem/fspec-fn-failure [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (printer/format
   "%s

%s

%s"
   (header-label "Function spec failed")
   (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path)))
   (expected-str _type spec-name val path problems opts)))

(defmethod expected-str :problem/unknown [_type spec-name val path problems opts]
  (predicate-errors problems))

(defmethod problem-group-str :problem/unknown [_type spec-name val path problems opts]
  (assert (apply = (map :val problems)) (str util/assert-message ": All values should be the same, but they are " problems))
  (printer/format
   "%s

%s

%s"
   (header-label (str "Spec failed"))
   (show-spec-name spec-name (printer/indent (*value-str-fn* spec-name val path (problems/value-in val path))))
   (expected-str _type spec-name val path problems opts)))

(defn problem-group-str1 [type spec-name val path problems opts]
  (str
   (problem-group-str type spec-name val path problems opts)
   "\n\n"
   (if (:print-specs? opts) (relevant-specs problems) "")))

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
      (binding [*value-str-fn* (get opts :value-str-fn (partial value-in-context opts'))
                ansi/*enable-color* (not= :none (get opts :theme :none))
                ansi/*print-styles* (case (get opts :theme :none)
                                      :figwheel-theme
                                      figwheel-theme

                                      :none
                                      {})]
        (let [{:keys [::s/fn ::s/failure]} explain-data
              explain-data' (problems/annotate explain-data)
              caller (:expound/caller explain-data')
              form (:expound/form explain-data')
              problems (->> explain-data'
                            :expound/problems
                            (problems/leaf-only)
                            (grouped-and-sorted-problems (::s/failure explain-data)))]

          (printer/no-trailing-whitespace
           (str
            (ansi/color (instrumentation-info failure caller) :none)
            (printer/format
             "%s

%s
%s %s %s\n"
             (string/join "\n\n" (for [[[in type] probs] problems]
                                   (problem-group-str1 type (spec-name explain-data) form in probs opts')))
             (ansi/color (section-label) :footer)
             (ansi/color "Detected" :footer)
             (ansi/color (count problems) :footer)
             (ansi/color (if (= 1 (count problems)) "error" "errors") :footer)))))))))

(s/def ::foo string?)

#?(:clj
   (defn ns-qualify
     "Qualify symbol s by resolving it or using the current *ns*."
     [s]
     (if-let [ns-sym (some-> s namespace symbol)]
       (or (some-> (get (ns-aliases *ns*) ns-sym) str (symbol (name s)))
           s)
       (symbol (str (.name *ns*)) (str s)))))

;;;;;; public ;;;;;;

(s/fdef custom-printer
        :args (s/cat :opts :expound.printer/opts))
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

(defn defmsg [k error-message]
  (swap! registry-ref assoc k error-message)
  nil)

#?(:clj
   (defmacro def
     "Like clojure.spec.alpha/def, but optionally takes a human-readable error message (will only be used for predicates) e.g. 'should be a string'"
     ([k spec-form]
      `(s/def ~k ~spec-form))
     ([k spec-form error-message]
      (let [k (if (symbol? k) (ns-qualify k) k)]
        `(do
           (defmsg '~k ~error-message)
           (s/def ~k ~spec-form))))))
