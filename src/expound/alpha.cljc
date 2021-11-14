(ns expound.alpha
  "Generates human-readable errors for `clojure.spec`"
  (:require [expound.problems :as problems]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.set :as set]
            [expound.printer :as printer]
            [expound.util :as util]
            [expound.ansi :as ansi]))

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
(s/def :expound.printer/value-str-fn ifn?)
(s/def :expound.printer/print-specs? boolean?)
(s/def :expound.printer/theme #{:figwheel-theme :none})
(s/def :expound.printer/opts (s/keys
                              :opt-un [:expound.printer/show-valid-values?
                                       :expound.printer/value-str-fn
                                       :expound.printer/print-specs?
                                       :expound.printer/theme]))

(s/def :expound.spec/spec (s/or
                           :set set?
                           :pred ifn?
                           :kw qualified-keyword?
                           :spec s/spec?))
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
  (binding [*print-namespace-maps* false]
    (cond
      (= :fn spec-name)
      (printer/indent (ansi/color (pr-str form) :bad-value))

      (= form value)
      (printer/indent (ansi/color (printer/pprint-str value) :bad-value))

      ;; FIXME: It's silly to reconstruct a fake "problem"
      ;; after I've deconstructed it, but I'm not yet ready
      ;; to break the API for value-in-context BUT
      ;; I do want to test that a problems-based API
      ;; is useful.
      ;; See https://github.com/bhb/expound#configuring-the-printer
      path
      (printer/indent (printer/highlighted-value opts
                                                 {:expound/form form
                                                  :expound/in path
                                                  :expound/value value}))
      :else
      (printer/format
       "Part of the value\n\n%s"
       (printer/indent (ansi/color (pr-str form) :bad-value))))))

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
     :cljs (implements? INamed x)))

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

(defn ^:private label
  ([size]
   (apply str (repeat size "-")))
  ([size s]
   (label size s "-"))
  ([size s label-str]
   (ansi/color
    (let [prefix (str label-str label-str " " s " ")
          chars-left (- (long size)
                        (count prefix))]
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
       "%s\n\n%s"
       (section-label "Relevant specs")
       sp-str))))

(defn ^:private multi-spec-parts [spec-form]
  (let [[_multi-spec mm] spec-form]
    {:mm mm}))

(defn ^:private multi-spec [pred spec]
  (->> (s/form spec)
       (tree-seq coll? seq)
       (filter #(and (sequential? %)
                     (<= 2 (count %))
                     (= ::s/multi-spec (keyword (first %)))
                     (= pred (second %))))
       first))

(defn ^:private no-method [_spec-name _form _path problem]
  (let [dispatch-val (last (:expound/path problem))
        sp (s/spec (last (:expound/via problem)))
        {:keys [mm]} (multi-spec-parts
                      (multi-spec (:pred problem) sp))]
    ;; It would be informative if we could print out
    ;; the dispatch function here, but I don't think we can reliably get it.
    ;; I would very much like to be wrong about this.
    ;;
    ;; Previously, I had misunderstood the purpose of the re-tag function.
    ;; but it is NOT used to invoke the multi-method. See
    ;; https://clojuredocs.org/clojure.spec.alpha/multi-spec#example-5b750e5be4b00ac801ed9e60
    ;;
    ;; In many common cases, re-tag will be a symbol that happens to be equal
    ;; to the dispatch function, but there is no guarantee. It's unfortunate to lose
    ;; information that could be useful in many common cases, but I think it's pretty
    ;; bad to display misleading information, even in rare cases.
    ;;
    ;; For CLJ, we might be able to do
    ;; (pr-str (.dispatchFn @(resolve mm)))
    ;; but I'm not sure that we can reliably resolve the multi-method symbol
    ;;
    ;; In any case, I'm fairly confident that for CLJS, we cannot resolve the symbol in
    ;; any context except the REPL, so we couldn't provide this message across implementations
    ;; (pr-str (dispatch-fn @(resolve mm)))
    ;;
    ;; Given the above, I think the safest thing to do is just not attempt to print the dispatch function.

    (printer/format
     " Spec multimethod:      `%s`
 Dispatch value:        `%s`"
     (pr-str mm)
     (pr-str dispatch-val))))

(defmulti ^:no-doc problem-group-str (fn [type _spec-name _form _path _problems _opts] type))
(defmulti ^:no-doc expected-str (fn [type  _spec-name _form _path _problems _opts] type))
(defmulti ^:no-doc value-str (fn [type _spec-name _form _path _problems _opts] type))

(defn ^:private expected-str* [spec-name problems opts]
  (let [problem (first problems)
        {:expound/keys [form in]} problem
        type (:expound.spec.problem/type problem)]
    (expected-str type spec-name form in problems opts)))

(defn ^:private value-str* [spec-name problems opts]
  (let [problem (first problems)
        {:expound/keys [form in]} problem
        type (:expound.spec.problem/type problem)]
    (value-str type spec-name form in problems opts)))

(defn ^:private conformed-value [problems invalid-value]
  (let [conformed-val (-> problems first :val)]
    (if (= conformed-val invalid-value)
      ""
      (printer/format
       "\n\nwhen conformed as\n\n%s"
       (printer/indent (ansi/color (pr-str conformed-val) :bad-value))))))

;; FIXME - when I decide to break compatibility for value-str-fn, maybe
;; make it show conform/unformed value
(defn ^:private value+conformed-value [problems spec-name form path opts]
  (let [{:keys [show-conformed?]} opts
        invalid-value (if (nil? path)
                      ;; This isn't used by default
                      ;; because value-in-context will look at
                      ;; path and only print form, but anyone
                      ;; who provides their own *value-str-fn*
                      ;; could use this
                        ::no-value-found
                        (problems/value-in form path))]
    (printer/format
     "%s%s"
     (*value-str-fn* spec-name form path invalid-value)
     (if show-conformed?
       (conformed-value problems invalid-value)
       ""))))

(defmethod value-str :default [_type spec-name form path problems _opts]
  (show-spec-name spec-name (value+conformed-value problems spec-name form path {:show-conformed? true})))

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

(def ^:private format-str "%s\n\n%s\n\n%s")

(defn ^:private format-err [header type spec-name form in problems opts expected]
  (printer/format
   format-str
   (header-label header)
   (value-str type spec-name form in problems opts)
   expected))

(defmethod expected-str :expound.problem-group/one-value [_type spec-name _form _path problems opts]
  (let [problem (first problems)
        subproblems (:problems problem)
        grouped-subproblems (vals (group-by :expound.spec.problem/type subproblems))]
    (string/join
     "\n\nor\n\n"
     (map #(expected-str* spec-name % opts) grouped-subproblems))))

(defmethod value-str :expound.problem-group/one-value [_type spec-name _form _path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)
        subproblems (:problems problem)]
    (value-str* spec-name subproblems opts)))

(defn ^:private header [type]
  (case type
    :expound.problem/missing-spec
    "Missing spec"

    "Spec failed"))

(defmethod problem-group-str :expound.problem-group/one-value [type spec-name _form path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)
        subproblems (:problems problem)
        {:expound/keys [form in]} (first subproblems)]
    (format-err (-> subproblems first :expound.spec.problem/type header)
                type
                spec-name
                form
                in
                problems
                opts
                (expected-str type spec-name form path problems opts))))

(defmethod expected-str :expound.problem-group/many-values [_type spec-name _form _path problems opts]
  (let [subproblems (:problems (first problems))]
    (string/join
     "\n\nor value\n\n"
     (for [problem subproblems]
       (printer/format
        "%s\n\n%s"
        (value-str* spec-name [problem] opts)
        (expected-str* spec-name [problem] opts))))))

(defmethod problem-group-str :expound.problem-group/many-values [_type spec-name form path problems opts]
  (s/assert ::singleton problems)
  (printer/format
   "%s\n\n%s"
   (header-label "Spec failed")
   (expected-str _type spec-name form path problems opts)))

(defmethod expected-str :expound.problem/missing-key [_type _spec-name _form _path problems _opts]
  (explain-missing-keys problems))

(defmethod problem-group-str :expound.problem/missing-key [type spec-name form path problems opts]
  (assert (apply = (map :val problems)) (str util/assert-message ": All values should be the same, but they are " problems))
  (format-err "Spec failed"
              type
              spec-name
              form
              path
              problems
              opts
              (expected-str type spec-name form path problems opts)))

(defmethod expected-str :expound.problem/not-in-set [_type _spec-name _form _path problems _opts]
  (let [{:keys [expound/via]} (first problems)
        last-spec (last via)]
    (if (and (qualified-keyword? last-spec) (error-message last-spec))
      (ansi/color (error-message last-spec) :good)
      (let [combined-set (apply set/union (map :pred problems))]
        (printer/format
         "should be%s: %s"
         (if (= 1 (count combined-set)) "" " one of")
         (ansi/color (->> combined-set
                          (map #(str "" (pr-str %) ""))
                          (sort)
                          (map #(ansi/color % :good))
                          (string/join ", "))
                     :good))))))

(defmethod problem-group-str :expound.problem/not-in-set [type spec-name form path problems opts]
  (assert (apply = (map :val problems)) (str util/assert-message ": All values should be the same, but they are " problems))
  (format-err "Spec failed"
              type
              spec-name
              form
              path
              problems
              opts
              (expected-str type spec-name form path problems opts)))

(defmethod expected-str :expound.problem/missing-spec [_type spec-name form path problems _opts]
  (str "with\n\n"
       (->> problems
            (map #(no-method spec-name form path %))
            (string/join "\n\nor with\n\n"))))

(defmethod value-str :expound.problem/missing-spec [_type spec-name form path _problems _opts]
  (printer/format
   "Cannot find spec for

%s"
   (show-spec-name spec-name (*value-str-fn* spec-name form path (problems/value-in form path)))))

(defmethod problem-group-str :expound.problem/missing-spec [type spec-name form path problems opts]
  (printer/format
   "%s\n\n%s\n\n%s"
   (header-label "Missing spec")
   (value-str type spec-name form path problems opts)
   (expected-str type spec-name form path problems opts)))

(defn ^:private lcs* [[x & xs] [y & ys]]
  (cond
    (or (= x nil) (= y nil)) nil
    (= x y) (vec (cons x (lcs* xs ys)))
    :else []))

(defn ^:private lcs [& paths]
  (reduce
   (fn [xs ys]
     (lcs* xs ys))
   paths))

(defn- all-key-symbols [key-form]
  (->> (s/conform
        :expound.spec/keys-spec
        key-form)
       :clauses
       (map :specs)
       (tree-seq coll? seq)
       (filter
        (fn [x]
          (and (vector? x)
               (= (first x) :kw))))

       (map second)))

(defn ^:private contains-alternate-at-path? [spec-form path]
  (if (not (coll? spec-form))
    false
    (let [[op & rest-form] spec-form
          [k & rest-path] path]
      (condp contains? op
        #{`s/or `s/alt} (let [node-keys (->> rest-form (apply hash-map) keys set)]
                          (cond
                            (empty? path) true
                            (contains? node-keys k) (some #(contains-alternate-at-path? % rest-path) rest-form)
                            :else false))

        #{`s/keys `s/keys*} (let [keys-args (->> rest-form (apply hash-map))
                                  node-keys (set (all-key-symbols spec-form))
                                  possible-spec-names (if (qualified-keyword? k)
                                                        [k]
                                                        (filter
                                                         #(= k
                                                             (keyword (name %)))
                                                         (flatten (vals keys-args))))]
                              (cond
                                ;; path is ambiguous here, we don't know which they intended if
                                ;; there are multiple-paths
                                (empty? path) false

                                (contains? node-keys k) (some #(contains-alternate-at-path? % rest-path)
                                                              (map s/form possible-spec-names))

                                :else false))

        #{`s/cat} (let [node-keys (->> rest-form (apply hash-map) keys set)]
                    (cond
                      (empty? path) false
                      (contains? node-keys k) (some #(contains-alternate-at-path? % rest-path) rest-form)
                      :else false))

        ;; It annoys me that I can't figure out a way to hit this branch in a spec
        ;; and I can't sufficiently explain why this will never be hit. Intuitively,
        ;; it seems like this should be similar to 's/or' and 's/alt' cases
        #{`s/nilable} (cond
                        (empty? path) true
                        (contains? #{::s/pred ::s/nil} k) (some
                                                           #(contains-alternate-at-path? % rest-path)
                                                           rest-form)

                        :else false)

        (some #(contains-alternate-at-path? % path) rest-form)))))

(defn ^:private share-alt-tags?
  "Determine if two groups have prefixes (ie. spec tags) that are included in
  an s/or or s/alt predicate."
  [grp1 grp2]
  (let [pprefix1 (:path-prefix grp1)
        pprefix2 (:path-prefix grp2)
        shared-prefix (lcs pprefix1 pprefix2)
        shared-specs (lcs (:via-prefix grp1) (:via-prefix grp2))]

    (and (get pprefix1 (-> shared-prefix count))
         (get pprefix2 (-> shared-prefix count))
         (some #(and
                 (contains-alternate-at-path? (s/form %) shared-prefix)
                 (contains-alternate-at-path? (s/form %) shared-prefix))
               shared-specs))))

(defn ^:private recursive-spec?
  "Determine if either group 1 or 2 is recursive (ie. have repeating specs in
  their via paths) and if one group is included in another."
  [grp1 grp2]
  (let [vxs (:via-prefix grp1)
        vys (:via-prefix grp2)
        vprefix (lcs vxs vys)]

    (or (and (not= (count vys) (count (distinct vys)))
             (< (count vprefix) (count vys))
             (= vxs vprefix))
        (and (not= (count vxs) (count (distinct vxs)))
             (< (count vprefix) (count vxs))
             (= vys vprefix)))))

(defn ^:private problem-group [grp1 grp2]
  {:expound.spec.problem/type :expound.problem-group/many-values
   :path-prefix               (lcs (:path-prefix grp1)
                                   (:path-prefix grp2))
   :via-prefix                (lcs (:via-prefix grp1)
                                   (:via-prefix grp2))
   :problems                  (into
                               (if (= :expound.problem-group/many-values
                                      (:expound.spec.problem/type grp1))
                                 (:problems grp1)
                                 [grp1])
                               (if (= :expound.problem-group/many-values
                                      (:expound.spec.problem/type grp2))
                                 (:problems grp2)
                                 [grp2]))})

(defn ^:private target-form? [form]
  (and (map? form)
       (not (sorted? form))
       (contains? #{:expound.problem-group/many-values
                    :expound.problem-group/one-value}
                  (:expound.spec.problem/type form))
       (= 1 (count (:problems form)))))

(defn ^:private groups-walk [f form]
  (cond
    (and (map? form)
         (contains? #{:expound.problem-group/many-values
                      :expound.problem-group/one-value}
                    (:expound.spec.problem/type form))
         (contains? form :problems))
    (f (update form :problems #(into (empty %) (map (partial groups-walk f) %))))

    :else form))

(defn ^:private lift-singleton-groups [groups]
  (mapv (partial groups-walk #(if (target-form? %)
                                (first (:problems %))
                                %)) groups))

(defn ^:private vec-remove [v x]
  (vec (remove #{x} v)))

(defn ^:private replace-group [groups old-groups group]
  (-> groups
      (vec-remove old-groups)
      (conj (problem-group old-groups group))))

(defn ^:private conj-groups
  "Consolidate a group into a group collection if it's either part of an s/or,
  s/alt or recursive spec."
  [groups group]
  (if-let [old-group (first (filter #(or (recursive-spec? % group)
                                         (share-alt-tags? % group))
                                    groups))]
    (replace-group groups old-group group)
    (conj groups group)))

(defn ^:private groups [problems]
  (let [grouped-by-in-path
        (->> problems
             (group-by :expound/in)
             vals
             (map (fn [grp]
                    {:expound.spec.problem/type :expound.problem-group/one-value
                     :path-prefix               (apply lcs (map :expound/path grp))
                     :via-prefix                (apply lcs (map :expound/via grp))
                     :problems                  grp})))]
    (->> grouped-by-in-path
         (reduce conj-groups [])
         lift-singleton-groups)))

(defn ^:private problems-without-location [problems opts]
  (let [failure nil
        non-matching-value [:expound/value-that-should-never-match]
        problems (->> problems
                      (map #(dissoc % :expound.spec.problem/type :reason))
                      (map #(assoc % :expound.spec.problem/type (problems/type failure % true)))
                      groups)]
    (apply str (for [prob problems]
                 (let [in (-> prob :expound/in)]
                   (expected-str (-> prob :expound.spec.problem/type) :expound/no-spec-name non-matching-value in [prob] opts))))))

(defmethod expected-str :expound.problem/insufficient-input [_type _spec-name _form _path problems opts]
  (let [problem (first problems)]
    (printer/format
     "should have additional elements. The next element%s %s"
     (if-some [el-name (last (:expound/path problem))]
       (str " \"" (pr-str el-name) "\"")
       "")
     (problems-without-location problems opts))))

(defmethod problem-group-str :expound.problem/insufficient-input [type spec-name form path problems opts]
  (format-err "Syntax error"
              type
              spec-name
              form
              path
              problems
              opts
              (expected-str type spec-name form path problems opts)))

(defmethod expected-str :expound.problem/extra-input [_type _spec-name _form _path problems _opts]
  (s/assert ::singleton problems)
  "has extra input")

(defmethod problem-group-str :expound.problem/extra-input [type spec-name form path problems opts]
  (format-err "Syntax error"
              type
              spec-name
              form
              path
              problems
              opts
              (expected-str type spec-name form path problems opts)))

(defmethod expected-str :expound.problem/fspec-exception-failure [_type _spec-name _form _path problems _opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "threw exception

%s

with args:

%s"
     (printer/indent (if (string? (:reason problem))
                       (str "\"" (:reason problem) "\"")
                       (pr-str (:reason problem))))
     (printer/indent (string/join ", " (:val problem))))))

(defmethod problem-group-str :expound.problem/fspec-exception-failure [type spec-name form path problems opts]
  (format-err
   "Exception"
   type
   spec-name
   form
   path
   problems
   opts
   (expected-str type spec-name form path problems opts)))

(defmethod expected-str :expound.problem/fspec-ret-failure [_type _spec-name _form _path problems opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "returned an invalid value\n\n%s\n\n%s"
     (ansi/color (printer/indent (pr-str (:val problem))) :bad-value)
     (problems-without-location problems opts))))

(defmethod problem-group-str :expound.problem/fspec-ret-failure [type spec-name form path problems opts]
  (format-err
   "Function spec failed"
   type
   spec-name
   form
   path
   problems
   opts
   (expected-str type spec-name form path problems opts)))

(defmethod value-str :expound.problem/insufficient-input [_type spec-name form path problems _opts]
  (show-spec-name spec-name (value+conformed-value problems spec-name form path {:show-conformed? false})))

(defmethod value-str :expound.problem/extra-input [_type spec-name form path problems _opts]
  (show-spec-name spec-name (value+conformed-value problems spec-name form path {:show-conformed? false})))

(defmethod value-str :expound.problem/fspec-fn-failure [_type spec-name form path problems _opts]
  (show-spec-name spec-name (value+conformed-value problems spec-name form path {:show-conformed? false})))

(defmethod value-str :expound.problem/fspec-exception-failure [_type spec-name form path problems _opts]
  (show-spec-name spec-name (value+conformed-value problems spec-name form path {:show-conformed? false})))

(defmethod value-str :expound.problem/fspec-ret-failure [_type spec-name form path problems _opts]
  (show-spec-name spec-name (value+conformed-value problems spec-name form path {:show-conformed? false})))

(defmethod expected-str :expound.problem/fspec-fn-failure [_type _spec-name _form _path problems _opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "failed spec. Function arguments and return value

%s

should satisfy

%s"
     (printer/indent (ansi/color (pr-str (:val problem)) :bad-value))
     (printer/indent (ansi/color (pr-pred (:pred problem) (:spec problem)) :good-pred)))))

(defmethod problem-group-str :expound.problem/fspec-fn-failure [type spec-name form path problems opts]
  (s/assert ::singleton problems)
  (format-err
   "Function spec failed"
   type
   spec-name
   form
   path
   problems
   opts
   (expected-str type spec-name form path problems opts)))

(defmethod expected-str :expound.problem/check-fn-failure [_type _spec-name _form _path problems _opts]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (printer/format
     "failed spec. Function arguments and return value

%s

should satisfy

%s"
     (printer/indent (ansi/color (pr-str (:val problem)) :bad-value))
     (printer/indent (ansi/color (pr-pred (:pred problem) (:spec problem)) :good-pred)))))

(defmethod problem-group-str :expound.problem/check-fn-failure [_type spec-name form path problems opts]
  (s/assert ::singleton problems)
  (printer/format
   format-str
   (header-label "Function spec failed")
   (ansi/color (printer/indent (pr-str (:expound/check-fn-call (first problems)))) :bad-value)
   (expected-str _type spec-name form path problems opts)))

(defmethod expected-str :expound.problem/check-ret-failure [_type _spec-name _form _path problems opts]
  (problems-without-location problems opts))

(defmethod problem-group-str :expound.problem/check-ret-failure [_type spec-name form path problems opts]
  (printer/format
   "%s

%s

returned an invalid value.

%s

%s"
   (header-label "Function spec failed")

   (ansi/color (printer/indent (pr-str (:expound/check-fn-call (first problems)))) :bad-value)

   (*value-str-fn* spec-name form path (problems/value-in form path))
   (expected-str _type spec-name form path problems opts)))

(defmethod expected-str :expound.problem/unknown [_type _spec-name _form _path problems _opts]
  (let [[with-msg no-msgs] ((juxt filter remove)
                            (fn [{:keys [expound/via pred]}]
                              (spec-w-error-message? via pred))
                            problems)]
    (->> (when (seq no-msgs)
           (printer/format
            "should satisfy\n\n%s"
            (preds no-msgs)))
         (conj (keep (fn [{:keys [expound/via]}]
                       (let [last-spec (last via)]
                         (if (qualified-keyword? last-spec)
                           (ansi/color (error-message last-spec) :good)
                           nil)))
                     with-msg))
         distinct
         (remove nil?)
         (string/join "\n\nor\n\n"))))

(defmethod problem-group-str :expound.problem/unknown [type spec-name form path problems opts]
  (assert (apply = (map :val problems)) (str util/assert-message ": All values should be the same, but they are " problems))
  (format-err
   "Spec failed"
   type
   spec-name
   form
   path
   problems
   opts
   (expected-str type spec-name form path problems opts)))

(defn ^:private instrumentation-info [failure caller]
  (if (= :instrument failure)
    (printer/format "%s:%s\n\n"
                    (:file caller "<filename missing>")
                    (:line caller "<line number missing>"))
    ""))

(defn ^:private spec-name [ed]
  (if (#{:instrument} (::s/failure ed))
    (cond
      ;; This works for clojure.spec <= 0.2.176
      ;; and CLJS <= 1.10.439
      (::s/args ed)
      :args

      (::s/ret ed)
      :ret

      (::s/fn ed)
      :fn

      :else
      ;; for earlier versions
      (-> ed ::s/problems first :path first))

    nil))

(defn ^:private print-explain-data [opts explain-data]
  (if-not explain-data
    "Success!\n"
    (let [explain-data' (problems/annotate explain-data)
          {:expound/keys [caller form]
           ::s/keys [failure]} explain-data'
          problems (->> explain-data'
                        :expound/problems
                        groups)]
      (printer/no-trailing-whitespace
       (str
        (ansi/color (instrumentation-info failure caller) :none)
        (printer/format
         "%s%s\n%s %s %s\n"
         (apply str
                (for [prob problems]
                  (str
                   (problem-group-str (-> prob :expound.spec.problem/type)
                                      (spec-name explain-data')
                                      form
                                      (-> prob :expound/in)
                                      [prob]
                                      opts)
                   "\n\n"
                   (let [s (if (:print-specs? opts)
                             (relevant-specs (:expound/problems
                                              explain-data'))
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
        (->> #?(:bb (identity)
                :clj (s/unform fspec-sp)
                :cljs (s/unform fspec-sp))))))

(defn ^:private print-check-result [check-result]
  (let [{:keys [sym spec failure] :or {sym '<unknown>}} check-result
        ret #?(:clj (:clojure.spec.test.check/ret check-result)
               :cljs (or (:clojure.spec.test.check/ret check-result)
                         (:clojure.test.check/ret check-result)))
        explain-data (ex-data failure)
        bad-args (or #?(:clj (:clojure.spec.test.alpha/args explain-data)
                        :cljs (:cljs.spec.test.alpha/args explain-data))
                     (-> ret :shrunk :smallest first))
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
       (str
        #?(:clj
           (let [path (::s/path explain-data)]
             (str
              "Unable to construct generator for "
              (ansi/color (pr-str path) :error-key)))
           :cljs
           (.-message failure))
        " in\n\n"
        (printer/indent (str (s/form (:args (:spec check-result)))))
        "\n")

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
         "Cannot check undefined function\n")

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
        (throw (ex-info  "Unknown data:\n\n" {:data data}))))))

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
  :args (s/cat :k any?)
  :ret (s/nilable string?))
(defn error-message
  "Given a spec named `k`, return its human-readable error message."
  [k]
  (reduce (fn [_ k]
            (when-let [msg (get @registry-ref k)]
              (reduced msg)))
          nil
          (util/spec-vals k)))

(s/fdef custom-printer
  :args (s/cat :opts :expound.printer/opts)
  :ret ifn?)
(defn custom-printer
  "Returns a printer.

  Options:
   - `:show-valid-values?` - if `false`, replaces valid values with \"...\"
   - `:value-str-fn`       - function to print bad values
   - `:print-specs?`       - if `true`, display \"Relevant specs\" section. Otherwise, omit that section.
   - `:theme`               - enables color theme. Possible values: `:figwheel-theme`, `:none`"
  [opts]
  (fn [explain-data]
    (print (printer-str opts explain-data))))

(s/fdef printer
  :args (s/cat :explain-data (s/nilable map?))
  :ret nil?)
(defn printer
  "Prints `explain-data` in a human-readable format."
  [explain-data]
  ((custom-printer {}) explain-data))

(s/fdef expound-str
  :args (s/cat :spec :expound.spec/spec
               :form any?
               :opts (s/? :expound.printer/opts))
  :ret string?)
(defn expound-str
  "Given a `spec` and a `form`, either returns success message or a human-readable error message."
  ([spec form]
   (expound-str spec form {}))
  ([spec form opts]
   (printer-str opts (s/explain-data spec form))))

(s/fdef expound
  :args (s/cat :spec :expound.spec/spec
               :form any?
               :opts (s/? :expound.printer/opts))
  :ret nil?)
(defn expound
  "Given a `spec` and a `form`, either prints a success message or a human-readable error message."
  ([spec form]
   (expound spec form {}))
  ([spec form opts]
   (print (expound-str spec form opts))))

(s/fdef defmsg
  :args (s/cat :k (s/or :ident qualified-ident?
                        :form any?)
               :error-message string?)
  :ret nil?)
(defn defmsg
  "Associates the spec named `k` with `error-message`."
  [k error-message]
  (swap! registry-ref assoc k error-message)
  nil)

(defn undefmsg
  "dissociate the message for spec named `k`"
  [k]
  (swap! registry-ref dissoc k))

#?(:clj
   (defmacro def
     "DEPRECATED: Prefer `defmsg`

  Define a spec with an optional `error-message`.

  Replaces `clojure.spec.alpha/def` but optionally takes a human-readable `error-message` (will only be used for predicates) e.g. \"should be a string\"."
     {:deprecated "0.7.2"}
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
