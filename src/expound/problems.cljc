(ns expound.problems
  (:require [expound.paths :as paths]
            [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [clojure.string :as string]
            [expound.printer :as printer]
            [expound.ansi :as ansi]))

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
      (assoc
       (dissoc displayed-form
               (:key k))
       (summary-form show-valid-values? (:key k) rst)
       ::irrelevant)

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
      (set (-> displayed-form
               vec
               (assoc k (summary-form show-valid-values? (nth (seq form) k) rst))))

      (and (int? k) (list? form))
      (into '() (-> displayed-form
                    vec
                    (assoc k (summary-form show-valid-values? (nth (seq form) k) rst))))

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

(defn- adjust-in [form problem]
  ;; Remove try/catch when
  ;; https://dev.clojure.org/jira/browse/CLJ-2192 or
  ;; https://dev.clojure.org/jira/browse/CLJ-2258 are fixed
  (try
    (assoc problem :expound/in (paths/in-with-kps form (:val problem) (:in problem) []))
    (catch #?(:cljs :default
              :clj Exception) e
      (if (or
           (= '(apply fn) (:pred problem))
           (#{:ret} (first (:path problem))))
        (assoc problem :expound/in (:in problem))
        (throw e)))))

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

;;;;;;;;;;;;;;;;;;;;;;;;;; public ;;;;;;;;;;;;;;;;;;;;;;;;;

(defn leaf-only
  "Given a collection of problems, returns only those problems with data on the 'leaves' of the data"
  [problems]
  (let [paths-to-data (into #{} (map :expound/in problems))]
    (remove
     (fn [problem]
       (some
        (fn [path]
          (paths/prefix-path? (:expound/in problem) path))
        paths-to-data))
     problems)))

(defn annotate [explain-data]
  (if (nil? explain-data)
    nil
    (let [{:keys [::s/problems ::s/value ::s/args ::s/ret ::s/fn ::s/failure ::s/spec]} explain-data
          caller (or (:clojure.spec.test.alpha/caller explain-data) (:orchestra.spec.test/caller explain-data))
          form (if (not= :instrument failure)
                 value
                 (cond
                   (contains? explain-data ::s/ret) ret
                   (contains? explain-data ::s/fn) fn
                   (contains? explain-data ::s/args) args))
          problems' (map (comp (partial adjust-in form)
                               (partial adjust-path failure)
                               (partial add-spec spec)
                               (partial fix-via spec)
                               #(assoc % :expound/form form))
                         problems)]
      (assoc explain-data
             :expound/form form
             :expound/caller caller
             :expound/problems problems'))))

(defn value-in
  "Similar to get-in, but works with paths that reference map keys"
  [form in]
  (let [[k & rst] in]
    (cond
      (empty? in)
      form

      (and (map? form) (paths/kps? k))
      (:key k)

      (and (map? form) (paths/kvps? k))
      (recur (nth (seq form) (:idx k)) rst)

      (associative? form)
      (recur (get form k) rst)

      (and (int? k) (seqable? form))
      (recur (nth (seq form) k) rst))))

;; TODO - deduple with value-in??
;; TODO - rename
(defn assoc-in1 [form in value]
  (let [[k & rst] in]
    (cond
      (empty? in)
      value

      (and (map? form) (paths/kps? k))
      (assoc form (:key k) value)

      ;; TODO - make this work
      ;;(and (map? form) (paths/kvps? k))
      ;;(recur (nth (seq form) (:idx k)) rst)

      (associative? form)
      (assoc form k (assoc-in1 (get form k) rst value))

      (and (int? k) (seq? form))
      (list* (assoc (vec form) k (assoc-in1 (nth (seq form) k) rst value))))))

(defn escape-replacement [pattern s]
  #?(:clj (if (string? pattern)
            s
            (string/re-quote-replacement s))
     :cljs (string/replace s #"\$" "$$$$")))

(defn highlighted-value
  "Given a problem, returns a pretty printed
   string that highlights the problem value"
  [opts problem]
  (let [{:keys [:expound/form :expound/in]} problem
        {:keys [show-valid-values?] :or {show-valid-values? false}} opts
        value-at-path (value-in form in)
        relevant (str "(" ::relevant "|(" ::kv-relevant "\\s+" ::kv-relevant "))")
        regex (re-pattern (str "(.*)" relevant ".*"))
        s (binding [*print-namespace-maps* false] (printer/pprint-str (walk/prewalk-replace {::irrelevant '...} (summary-form show-valid-values? form in))))
        [line prefix & _more] (re-find regex s)
        highlighted-line (-> line
                             (string/replace (re-pattern relevant) (escape-replacement
                                                                    (re-pattern relevant)
                                                                    (printer/indent 0 (count prefix) (ansi/color (printer/pprint-str value-at-path) :bad-value))))
                             (str "\n" (ansi/color (highlight-line prefix (printer/pprint-str value-at-path))
                                                   :pointer)))]
    ;;highlighted-line
    (printer/no-trailing-whitespace (string/replace s line (escape-replacement line highlighted-line)))))

