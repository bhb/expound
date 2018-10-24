(ns ^:no-doc expound.problems
  (:require [expound.paths :as paths]
            [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [clojure.string :as string]
            [expound.printer :as printer]
            [expound.ansi :as ansi])
  (:refer-clojure :exclude [type]))

(defn blank-form [form]
  (cond
    (map? form)
    (zipmap (keys form) (repeat ::irrelevant))

    (vector? form)
    (vec (repeat (count form) ::irrelevant))

    (set? form)
    form

    (or (list? form)
        (seq? form))
    (apply list (repeat (count form) ::irrelevant))

    :else
    ::irrelevant))

(s/fdef summary-form
        :args (s/cat :show-valid-values? boolean?
                     :form any?
                     :highlighted-path :expound/path))
(defn summary-form [show-valid-values? form in]
  (let [[k & rst] in
        rst (or rst [])
        displayed-form (if show-valid-values? form (blank-form form))]
    (cond
      (empty? in)
      ::relevant

      (and (map? form) (paths/kps? k))
      (-> displayed-form
          (dissoc (:key k))
          (assoc (summary-form show-valid-values? (:key k) rst)
                 ::irrelevant))

      (and (map? form) (paths/kvps? k))
      (recur show-valid-values? (nth (seq form) (:idx k)) rst)

      (associative? form)
      (assoc displayed-form
             k
             (summary-form show-valid-values? (get form k) rst))

      (and (int? k) (seq? form))
      (apply list (-> displayed-form
                      vec
                      (assoc k (summary-form show-valid-values? (nth form k) rst))))

      (and (int? k) (set? form))
      (into #{} (-> displayed-form
                    vec
                    (assoc k (summary-form show-valid-values? (nth (seq form) k) rst))))

      (and (int? k) (list? form))
      (into '() (-> displayed-form
                    vec
                    (assoc k (summary-form show-valid-values? (nth (seq form) k) rst))))
      (and (int? k) (string? form))
      (string/join (assoc (vec form) k ::relevant))

      :else
      (throw (ex-info "Cannot find path segment in form. This can be caused by using conformers to transform values, which is not supported in Expound"
                      {:form form
                       :in in})))))

;; FIXME - this function is not intuitive.
(defn highlight-line
  [prefix replacement]
  (let [max-width (apply max (map #(count (str %)) (string/split-lines replacement)))]
    (printer/indent (count (str prefix))
                    (apply str (repeat max-width "^")))))

;; can simplify when 
;; https://dev.clojure.org/jira/browse/CLJ-2192 or
;; https://dev.clojure.org/jira/browse/CLJ-2258 are fixed
(defn- adjust-in [form problem]
  ;; Three strategies for finding the value...
  (let [;; 1. Find the original value
        in (paths/in-with-kps form (:val problem) (:in problem) [])
        in1 (if (not= ::paths/not-found-path in)
              in
              nil)

        ;; 2. If value is unique, just find that, ignoring the 'in' path
        in2 (let [paths (paths/paths-to-value form (:val problem) [] [])]
              (if (= 1 (count paths))
                (first paths)
                nil))

        ;; 3. Find the unformed value (if there is an unformer)
        in3 (try
              (paths/in-with-kps form
                                 (s/unform (last (:via problem)) (:val problem))
                                 (:in problem) [])
              ;; The unform fails if there is no unformer
              ;; and the unform function could throw any type of
              ;; exception (it's provided by user)
              (catch #?(:cljs :default
                        :clj java.lang.Exception) _e
                nil)
              (catch #?(:cljs :default
                        :clj java.lang.Error) _e
                nil))

        new-in (cond
                   ;; TODO - simplify by returning nil?
                 (and in1
                      (not= ::paths/not-found-path in1))
                 in1

                 (and in2
                      (not= ::paths/not-found-path in2))
                 in2

                 (and in3
                      (not= ::paths/not-found-path in3))
                 in3

                 (or (= '(apply fn) (:pred problem))
                     (#{:ret} (first (:path problem))))
                 (:in problem)

                 :else
                 ::paths/not-found-path)]
    (assoc problem
           :expound/in
           (or new-in ::paths/not-found-path))))

(defn- adjust-path [failure problem]
  (assoc problem :expound/path
         (if (= :instrument failure)
           (vec (rest (:path problem)))
           (:path problem))))

(defn- add-spec [spec problem]
  (assoc problem :spec spec))

;; via is slightly different when using s/assert
(defn fix-via [spec problem]
  (if (= spec (first (:via problem)))
    (assoc problem :expound/via (:via problem))
    (assoc problem :expound/via (into [spec] (:via problem)))))

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

(defn ^:private ptype [failure problem]
  (cond
    (:expound.spec.problem/type problem)
    (:expound.spec.problem/type problem)

    (insufficient-input? failure problem)
    :expound.problem/insufficient-input

    (extra-input? failure problem)
    :expound.problem/extra-input

    (not-in-set? failure problem)
    :expound.problem/not-in-set

    (missing-key? failure problem)
    :expound.problem/missing-key

    (missing-spec? failure problem)
    :expound.problem/missing-spec

    (fspec-exception-failure? failure problem)
    :expound.problem/fspec-exception-failure

    (fspec-ret-failure? failure problem)
    :expound.problem/fspec-ret-failure

    (fspec-fn-failure? failure problem)
    :expound.problem/fspec-fn-failure

    (check-ret-failure? failure problem)
    :expound.problem/check-ret-failure

    (check-fn-failure? failure problem)
    :expound.problem/check-fn-failure

    :else
    :expound.problem/unknown))

;;;;;;;;;;;;;;;;;;;;;;;;;; public ;;;;;;;;;;;;;;;;;;;;;;;;;

(defn escape-replacement [pattern s]
  #?(:clj (if (string? pattern)
            s
            (string/re-quote-replacement s))
     :cljs (string/replace s #"\$" "$$$$")))

;; TODO - refactor for readability
(defn highlighted-value
  "Given a problem, returns a pretty printed
   string that highlights the problem value"
  [opts problem]
  (let [{:keys [:expound/form :expound/in]} problem
        {:keys [show-valid-values?] :or {show-valid-values? false}} opts
        printed-val (printer/pprint-str (paths/value-in form in))
        relevant (str "(" ::relevant "|(" ::kv-relevant "\\s+" ::kv-relevant "))")
        regex (re-pattern (str "(.*)" relevant ".*"))
        s (binding [*print-namespace-maps* false] (printer/pprint-str (walk/prewalk-replace {::irrelevant '...} (summary-form show-valid-values? form in))))
        [line prefix & _more] (re-find regex s)
        highlighted-line (-> line
                             (string/replace (re-pattern relevant) (escape-replacement
                                                                    (re-pattern relevant)
                                                                    (printer/indent 0 (count prefix) (ansi/color printed-val :bad-value))))
                             (str "\n" (ansi/color (highlight-line prefix printed-val)
                                                   :pointer)))]
    ;;highlighted-line
    (printer/no-trailing-whitespace (string/replace s line (escape-replacement line highlighted-line)))))

(defn annotate [explain-data]
  (let [{::s/keys [problems value args ret fn failure spec]} explain-data
        caller (or (:clojure.spec.test.alpha/caller explain-data) (:orchestra.spec.test/caller explain-data))
        form (if (not= :instrument failure)
               value
               (cond
                 (contains? explain-data ::s/ret) ret
                 (contains? explain-data ::s/args) args
                 (contains? explain-data ::s/fn) fn))
        problems' (map (comp (partial adjust-in form)
                             (partial adjust-path failure)
                             (partial add-spec spec)
                             (partial fix-via spec)
                             #(assoc % :expound/form form)
                             #(assoc % :expound.spec.problem/type (ptype failure %)))
                       problems)]
    (-> explain-data
        (assoc :expound/form form
               :expound/caller caller
               :expound/problems problems'))))

(def type ptype)

;; Must keep this function here because
;; spell-spec uses it
;; https://github.com/bhauman/spell-spec/blob/48ea2ca544f02b04a73dc42a91aa4876dcc5fc95/src/spell_spec/expound.cljc#L20
(def value-in paths/value-in)
