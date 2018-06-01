(ns expound.alpha
  "Functions to print human-readable errors for clojure.spec"
  (:require [expound.paths :as paths]
            [expound.problems :as problems]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.set :as set]
            #?(:cljs [goog.string.format])
            #?(:cljs [goog.string])
            [expound.printer :as printer]
            [expound.util :as util]
            [expound.ansi :as ansi]
            [clojure.spec.gen.alpha :as gen]))

;;;;;; registry ;;;;;;

(defonce ^:private registry-ref (atom {}))

;;;;;; internal specs ;;;;;;

(s/def ::singleton (s/coll-of any? :count 1))
(s/def :expound.spec/spec keyword?)
(s/def :expound.spec/specs (s/coll-of :expound.spec/spec))
(s/def :expound.spec.problem/via (s/coll-of :expound.spec/spec :kind vector?))
(s/def :expound.spec/problem (s/keys :req-un [:expound.spec.problem/via]))
(s/def :expound.spec/problems (s/coll-of :expound.spec/problem))

(s/def :expound.printer/show-valid-values? boolean?)
(s/def :expound.printer/value-str-fn (s/with-gen ifn?
                                       #(gen/return (fn [_ _ _ _] "NOT IMPLEMENTED"))))
(s/def :expound.printer/print-specs? boolean?)
(s/def :expound.printer/theme #{:figwheel-theme :none})
(s/def :expound.printer/opts (s/keys
                              :opt-un [:expound.printer/show-valid-values?
                                       :expound.printer/value-str-fn
                                       :expound.printer/print-specs?
                                       :expound.printer/theme]))

(s/def :expound.spec/spec (s/or
                           :set set?
                           :pred (s/with-gen ifn?
                                   #(gen/elements [boolean? string? int? keyword? symbol?]))
                           :kw qualified-keyword?
                           :spec (s/with-gen s/spec?
                                   #(gen/elements
                                     (for [pr [boolean? string? int? keyword? symbol?]]
                                       (s/spec pr))))))

;;;;;; themes ;;;;;;

(def ^:private figwheel-theme
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

(def ^:private check-header-size 45)
(def ^:private header-size 35)
(def ^:private section-size 25)

(def ^:private ^:dynamic *value-str-fn* (fn [_ _ _ _] "NOT IMPLEMENTED"))

(s/fdef value-in-context
        :args (s/cat
               :opts map?
               :spec-name (s/nilable #{:args :fn :ret ::s/pred})
               :form any?
               :path :expound/path
               :value any?)
        :ret string?)
(defn ^:private value-in-context
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

(defn ^:private spec-str [spec]
  (if (keyword? spec)
    (printer/format
     "%s:\n%s"
     spec
     (printer/indent (printer/pprint-str (s/form spec))))
    (printer/pprint-str (s/form spec))))

;; via is different when using asserts
(defn ^:private spec+via [problem]
  (let [{:keys [via spec]} problem]
    (if (keyword? spec)
      (into [spec] via)
      via)))

(s/fdef specs
        :args (s/cat :problems :expound.spec/problems)
        :ret :expound.spec/specs)
(defn ^:private specs
  "Given a collection of problems, returns the specs for those problems, with duplicates removed"
  [problems]
  (->> problems
       (map spec+via)
       flatten
       distinct))

(defn ^:private specs-str [problems]
  (->> problems
       specs
       reverse
       (map spec-str)
       (string/join "\n")))

(defn ^:private named? [x]
  #?(:clj (instance? clojure.lang.Named x)
     :cljs (implements? cljs.core.INamed x)))

(defn ^:private pr-pred* [pred]
  (cond
    (or (symbol? pred) (named? pred))
    (name pred)

    (fn? pred)
    (printer/pprint-fn pred)

    :else
    (printer/elide-core-ns (binding [*print-namespace-maps* false] (printer/pprint-str pred)))))

(defn ^:private pr-pred [pred spec]
  (if (= ::s/unknown pred)
    (pr-pred* spec)
    (pr-pred* pred)))

(defn ^:private show-spec-name [spec-name value]
  (if spec-name
    (str
     (case spec-name
       ::s/pred "" ; Used in s/assert
       :args "Function arguments\n\n"
       :ret "Return value\n\n"
       :fn "Function arguments and return value\n\n")
     value)
    value))

(defn ^:private preds [problems]
  (->> problems
       (map (fn [problem]
              (printer/indent
               (ansi/color
                (pr-pred (:pred problem)
                         (:spec problem))
                :good-pred))))
       distinct
       (string/join "\n\nor\n\n")))

(declare error-message)

(defn ^:private spec-w-error-message? [via pred]
  (boolean (let [last-spec (last via)]
             (and (not= ::s/unknown pred)
                  (qualified-keyword? last-spec)
                  (error-message last-spec)
                  (s/get-spec last-spec)))))

(defn ^:private predicate-errors [problems]
  (let [[with-msg no-msgs] ((juxt filter remove)
                            (fn [{:keys [expound/via pred]}]
                              (spec-w-error-message? via pred))
                            problems)]
    (->> (when (seq no-msgs)
           (printer/format
            "should satisfy

%s"
            (preds no-msgs)))
         (conj (keep (fn [{:keys [expound/via]}]
                       (let [last-spec (last via)]
                         (if (qualified-keyword? last-spec)
                           (ansi/color (error-message last-spec) :good)
                           nil)))
                     with-msg))
         (remove nil?)
         (string/join "\n\nor\n\n"))))

(defn ^:private label
  ([size]
   (apply str (repeat size "-")))
  ([size s]
   (label size s "-"))
  ([size s label-str]
   (ansi/color
    (let [prefix (str label-str label-str " " s " ")
          chars-left (- size (count prefix))]
      (->> (repeat chars-left label-str)
           (apply str)
           (str prefix)))
    :header)))

(def ^:private header-label (partial label header-size))
(def ^:private section-label (partial label section-size))

(defn ^:private relevant-specs [problems]
  (let [sp-str (specs-str problems)]
    (if (string/blank? sp-str)
      ""
      (printer/format
       "%s

%s"
       (section-label "Relevant specs")
       sp-str))))

(defn ^:private multi-spec-parts [spec-form]
  (let [[_multi-spec mm retag] spec-form]
    {:mm mm :retag retag}))

(defn ^:private missing-spec? [_failure problem]
  (= "no method" (:reason problem)))

(defn ^:private not-in-set? [_failure problem]
  (set? (:pred problem)))

(defn ^:private fspec-exception-failure? [failure problem]
  (and (not= :instrument failure)
       (not= :check-failed failure)
       (= '(apply fn) (:pred problem))))

(defn ^:private fspec-ret-failure? [failure problem]
  (and
   (not= :instrument failure)
   (not= :check-failed failure)
   (= :ret (first (:path problem)))))

(defn ^:private fspec-fn-failure? [failure problem]
  (and
   (not= :instrument failure)
   (not= :check-failed failure)
   (= :fn (first (:path problem)))))

(defn ^:private check-ret-failure? [failure problem]
  (and
   (= :check-failed failure)
   (= :ret (first (:path problem)))))

(defn ^:private check-fn-failure? [failure problem]
  (and (= :check-failed failure)
       (= :fn (first (:path problem)))))

(defn ^:private missing-key? [_failure problem]
  (let [pred (:pred problem)]
    (and (seq? pred)
         (< 2 (count pred))
         (s/valid?
          :expound.spec/contains-key-pred
          (nth pred 2)))))

(defn ^:private insufficient-input? [_failure problem]
  (contains? #{"Insufficient input"} (:reason problem)))

(defn ^:private extra-input? [_failure problem]
  (contains? #{"Extra input"} (:reason problem)))

(defn ^:private multi-spec [pred spec]
  (->> (s/form spec)
       (tree-seq coll? seq)
       (filter #(and (sequential? %)
                     (<= 2 (count %))
                     (= ::s/multi-spec (keyword (first %)))
                     (= pred (second %))))
       first))

(defn ^:private no-method [spec-name val path problem]
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

(defmulti ^:no-doc problem-group-str (fn [type spec-name _val _path _problems _opts] type))
(defmulti ^:no-doc expected-str (fn [type  spec-name _val _path _problems _opts] type))

(defn ^:private explain-missing-keys [problems]
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
     (ansi/color (->> combined-set
                      (map #(str "" (pr-str %) ""))
                      (sort)
                      (map #(ansi/color % :good))
                      (string/join ", "))
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

(defn ^:private safe-sort-by
  "Same as sort-by, but if an error is raised, returns the original unsorted collection"
  [key-fn comp coll]
  (try
    (sort-by key-fn comp coll)
    (catch #?(:cljs :default
              :clj Exception) e coll)))

(defn ^:private problem-type [failure problem]
  (cond
    (get-method problem-group-str (:expound.spec.problem/type problem))
    (:expound.spec.problem/type problem)

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

    (check-ret-failure? failure problem)
    :problem/check-ret-failure

    (check-fn-failure? failure problem)
    :problem/check-fn-failure

    :else
    :problem/unknown))

(defn ^:private grouped-and-sorted-problems [failure problems]
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
  "has extra input")

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

(defmethod expected-str :problem/check-fn-failure [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "failed spec. Function arguments and return value

%s

should satisfy

%s"
     (printer/indent (ansi/color (pr-str (:val problem)) :bad-value))
     (printer/indent (ansi/color (pr-pred (:pred problem) (:spec problem)) :good-pred)))))

(defmethod problem-group-str :problem/check-fn-failure [_type spec-name val path problems opts]
  (s/assert ::singleton problems)
  (printer/format
   "%s

%s

%s"
   (header-label "Function spec failed")
   (ansi/color (printer/indent (pr-str (:expound/check-fn-call (first problems)))) :bad-value)
   (expected-str _type spec-name val path problems opts)))

(defmethod expected-str :problem/check-ret-failure [_type spec-name val path problems opts]
  (predicate-errors problems))

(defmethod problem-group-str :problem/check-ret-failure [_type spec-name val path problems opts]
  (printer/format
   "%s

%s

returned an invalid value.

%s

%s"
   (header-label "Function spec failed")

   (ansi/color (printer/indent (pr-str (:expound/check-fn-call (first problems)))) :bad-value)

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

(defn ^:private instrumentation-info [failure caller]
  ;; As of version 1.9.562, Clojurescript does
  ;; not include failure or caller info, so
  ;; if these are null, print a placeholder
  (if (= :instrument failure)
    (printer/format "%s:%s
\n"
                    (:file caller "<filename missing>")
                    (:line caller "<line number missing>"))
    ""))

(defn ^:private spec-name [ed]
  (if (#{:instrument} (::s/failure ed))
    (-> ed ::s/problems first :path first)
    nil))

(defn ^:private print-explain-data [opts explain-data]
  (if-not explain-data
    "Success!\n"
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
         "%s%s
%s %s %s\n"
         (apply str
                (for [[[in type] probs] problems]
                  (str
                   (problem-group-str type (spec-name explain-data) form in probs opts)
                   "\n\n"
                   (let [s (if (:print-specs? opts)
                             (relevant-specs probs)
                             "")]
                     (if (empty? s)
                       s
                       (str s "\n\n"))))))
         (ansi/color (section-label) :footer)
         (ansi/color "Detected" :footer)
         (ansi/color (count problems) :footer)
         (ansi/color (if (= 1 (count problems)) "error" "errors") :footer)))))))

(defn ^:private minimal-fspec [form]
  (let [fspec-sp (s/cat
                  :sym qualified-symbol?
                  :args (s/*
                         (s/cat :k #{:args :fn :ret} :v any?)))]

    (-> (s/conform fspec-sp form)
        (update :args (fn [args] (filter #(some? (:v %)) args)))
        (->> (s/unform fspec-sp)))))

(defn ^:private print-check-result [check-result]
  (let [{:keys [sym spec failure] :or {sym '<unknown>}} check-result
        ret #?(:clj (:clojure.spec.test.check/ret check-result)
               :cljs (:clojure.test.check/ret check-result))
        explain-data (ex-data failure)
        bad-args (or #?(:clj (:clojure.spec.test.alpha/args explain-data)
                        :cljs (:cljs.spec.test.alpha/args explain-data))
                     (first (:fail ret)))
        failure-reason (::s/failure explain-data)
        sym (or sym '<unknown>)]
    (str
     ;; CLJS does not contain symbol if function is undefined
     (label check-header-size (str "Checked " sym) "=")
     "\n\n"
     (cond
       ;; FIXME - once we have a function that can highlight
       ;;         a spec, use it here to make this error message clearer
       #?(:clj (and failure (= :no-gen failure-reason))
          ;; Workaround for CLJS
          :cljs (and
                 failure
                 (re-matches #"Unable to construct gen at.*" (.-message failure))))
       (let [path (::s/path explain-data)]
         (str
          #?(:clj
             (str
              "Unable to construct generator for "
              (ansi/color (pr-str path) :error-key))
             :cljs
             (.-message failure))
          " in\n\n"
          (printer/indent (str (s/form (:args (:spec check-result)))))
          "\n"))

       (= :no-args-spec failure-reason)
       (str
        "Failed to check function.\n\n"
        (ansi/color (printer/indent (printer/pprint-str
                                     (minimal-fspec (s/form spec)))) :bad-value)
        "\n\nshould contain an :args spec\n")

       (= :no-fn failure-reason)
       (if (some? sym)
         (str
          "Failed to check function.\n\n"
          (ansi/color (printer/indent (pr-str sym)) :bad-value)
          "\n\nis not defined\n")
         ;; CLJS doesn't set the symbol
         (str
          "Cannot check undefined function\n"))

       (and explain-data
            (= :check-failed (-> explain-data ::s/failure)))
       (with-out-str
         (s/*explain-out* (update
                           explain-data
                           ::s/problems
                           #(map
                             (fn [p]
                               (assoc p :expound/check-fn-call (concat (list sym)
                                                                       bad-args)))
                             %))))

       failure
       (str
        (ansi/color (printer/indent (printer/pprint-str
                                     (concat (list sym) bad-args))) :bad-value)
        "\n\n threw error\n\n"
        (printer/pprint-str failure))

       :else
       "Success!\n"))))

(defn ^:private explain-data? [data]
  (s/valid?
   (s/keys :req
           [::s/problems
            ::s/spec
            ::s/value]
           :opt
           [::s/failure])
   data))

(defn ^:private check-result? [data]
  (s/valid?
   (s/keys :req-un [::spec]
           :opt-un [::sym
                    ::failure
                    :clojure.spec.test.check/ret])
   data))

(defn ^:private printer-str [opts data]
  (let [opts' (merge {:show-valid-values? false
                      :print-specs? true}
                     opts)
        enable-color? (or (not= :none (get opts :theme :none))
                          ansi/*enable-color*)]
    (binding [*value-str-fn* (get opts :value-str-fn (partial value-in-context opts'))
              ansi/*enable-color* enable-color?
              ansi/*print-styles* (case (get opts :theme (if enable-color? :figwheel-theme :none))
                                    :figwheel-theme
                                    figwheel-theme

                                    :none
                                    {})]

      (cond
        (or (explain-data? data)
            (nil? data))
        (print-explain-data opts' data)

        (check-result? data)
        (print-check-result data)

        :else
        (str "Unknown data:\n\n" data)))))

#?(:clj
   (defn ^:private ns-qualify
     "Qualify symbol s by resolving it or using the current *ns*."
     [s]
     (if-let [ns-sym (some-> s namespace symbol)]
       (or (some-> (get (ns-aliases *ns*) ns-sym) str (symbol (name s)))
           s)
       (symbol (str (.name *ns*)) (str s)))))

;;;;;; public ;;;;;;

(s/fdef error-message
        :args (s/cat :k qualified-keyword?)
        :ret (s/nilable string?))
(defn error-message
  "Given a spec named `k`, return its human-readable error message."
  [k]
  (get @registry-ref k))

(s/fdef custom-printer
        :args (s/cat :opts :expound.printer/opts)
        :ret ifn?)
(defn custom-printer
  "Returns a printer.

  Options:
   :show-valid-values? - if false, replaces valid values with \"...\"
   :value-str-fn       - function to print bad values
   :print-specs?       - if true, display \"Relevant specs\" section. Otherwise, omit that section.
   :theme               - enables color theme. Possible values: :figwheel-theme, :none"
  [opts]
  (fn [explain-data]
    (print (printer-str opts explain-data))))

(s/fdef printer
        :args (s/cat :explain-data map?)
        :ret nil?)
(defn printer
  "Prints `explain-data` in a human-readable format."
  [explain-data]
  ((custom-printer {}) explain-data))

(s/fdef expound-str
        :args (s/cat :spec :expound.spec/spec
                     :form any?)
        :ret string?)
(defn expound-str
  "Given a `spec` and a `form`, either returns success message or a human-readable error message."
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

(s/fdef expound
        :args (s/cat :spec :expound.spec/spec
                     :form any?)
        :ret nil?)
(defn expound
  "Given a `spec` and a `form`, either prints a success message or a human-readable error message."
  [spec form]
  (print (expound-str spec form)))

(s/fdef defmsg
        :args (s/cat :k qualified-keyword?
                     :error-message string?)
        :ret nil?)
(defn defmsg
  "Associates the spec named `k` with `error-message`."
  [k error-message]
  (swap! registry-ref assoc k error-message)
  nil)

#?(:clj
   (defmacro def
     "Define a spec with an optional `error-message`.

  Replaces `clojure.spec.alpha/def` but optionally takes a human-readable `error-message` (will only be used for predicates) e.g. 'should be a string'."
     ([k spec-form]
      `(s/def ~k ~spec-form))
     ([k spec-form error-message]
      (let [k (if (symbol? k) (ns-qualify k) k)]
        `(do
           (defmsg '~k ~error-message)
           (s/def ~k ~spec-form))))))

(s/fdef explain-result
        :args (s/cat :check-result (s/nilable map?))
        :ret nil?)
(defn explain-result
  "Given a result from `clojure.spec.test.alpha/check`, prints a summary of the result."
  [check-result]
  (when (= s/*explain-out* s/explain-printer)
    (throw (ex-info "Cannot print check results with default printer. Use 'set!' or 'binding' to use Expound printer." {})))
  (s/*explain-out* check-result))

(s/fdef explain-result-str
        :args (s/cat :check-result (s/nilable map?))
        :ret string?)
(defn explain-result-str
  "Given a result from `clojure.spec.test.alpha/check`, returns a string summarizing the result."
  [check-result]
  (with-out-str (explain-result check-result)))

(s/fdef explain-results
        :args (s/cat :check-results (s/coll-of (s/nilable map?)))
        :ret nil?)
(defn explain-results
  "Given a sequence of results from `clojure.spec.test.alpha/check`, prints a summary of the results."
  [check-results]
  (doseq [check-result (butlast check-results)]
    (explain-result check-result)
    (print "\n\n"))
  (explain-result (last check-results)))

(s/fdef explain-results-str
        :args (s/cat :check-results (s/coll-of (s/nilable map?)))
        :ret string?)
(defn explain-results-str
  "Given a sequence of results from `clojure.spec.test.alpha/check`, returns a string summarizing the results."
  [check-results]
  (with-out-str (explain-results check-results)))
