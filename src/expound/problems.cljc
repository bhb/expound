(ns expound.problems
  (:require [expound.paths :as paths]
            [clojure.spec.alpha :as s]
            [clojure.walk :as walk]
            [clojure.string :as string]
            [expound.printer :as printer]))

(s/fdef summary-form
        :args (s/cat :show-valid-valids? boolean?
                     :form any?
                     :highlighted-path :expound/path))
(defn summary-form
  "Given a form and a path to highlight, returns a data structure that marks
   the highlighted and irrelevant data"
  [show-valid-values? form highlighted-path]
  (paths/postwalk-with-path
   (fn [path x]
     (cond
       (= ::irrelevant x)
       (if show-valid-values?
         x
         ::irrelevant)

       (= ::relevant x)
       ::relevant

       (and (paths/kvps-path? path) (= path highlighted-path))
       [::kv-relevant ::kv-relevant]

       (= path highlighted-path)
       ::relevant

       (paths/prefix-path? path highlighted-path)
       x

       (paths/kps-path? path)
       x

       (paths/kvps-path? path)
       x

       :else
       (if show-valid-values?
         x
         ::irrelevant)))
   form))

;; FIXME - this function is not intuitive.
(defn highlight-line
  [prefix replacement]
  (let [max-width (apply max (map #(count (str %)) (string/split-lines replacement)))]
    (printer/indent (count (str prefix))
                    (apply str (repeat max-width "^")))))

(defn- adjust-in [form problem]
  (assoc problem :expound/in (paths/in-with-kps form (:in problem) [])))

(defn- adjust-path [failure problem]
  (assoc problem :expound/path
         (if (= :instrument failure)
           (vec (rest (:path problem)))
           (:path problem))))

(defn- add-spec [spec problem]
  (assoc problem :spec spec))

(defn add-caller [explain-data])

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
                             #(assoc % :expound/form form))
                       problems)]
    (assoc explain-data
           :expound/form form
           :expound/caller caller
           :expound/problems problems')))

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
      (nth (seq form) (:idx k))

      (associative? form)
      (recur (get form k) rst)

      (int? k)
      (recur (nth form k) rst))))

(defn escape-replacement [pattern s]
  #?(:clj (if (string? pattern)
            s
            (string/re-quote-replacement s))
     :cljs (string/replace s #"\$" "$$$$")))

;; FIXME - perhaps a more useful API would be an API on 'problems'?
;; - group problems
;; - print out data structure given problem
;; - categorize problem
(defn highlighted-value
  "Given a form and a path into that form, returns a pretty printed
   string that highlights the value at the path."
  [opts form path]
  (let [{:keys [show-valid-values?] :or {show-valid-values? false}} opts
        value-at-path (value-in form path)
        relevant (str "(" ::relevant "|(" ::kv-relevant "\\s+" ::kv-relevant "))")
        regex (re-pattern (str "(.*)" relevant ".*"))
        s (binding [*print-namespace-maps* false] (printer/pprint-str (walk/prewalk-replace {::irrelevant '...} (summary-form show-valid-values? form path))))
        [line prefix & _more] (re-find regex s)
        highlighted-line (-> line
                             (string/replace (re-pattern relevant) (escape-replacement
                                                                    (re-pattern relevant)
                                                                    (printer/indent 0 (count prefix) (printer/pprint-str value-at-path))))
                             (str "\n" (highlight-line prefix (printer/pprint-str value-at-path))))]
    ;;highlighted-line
    (printer/no-trailing-whitespace (string/replace s line (escape-replacement line highlighted-line)))))

;; FIXME - I want to move everything to use annontated problems
;;         but it will be a breaking change for
;;         https://github.com/bhb/expound#configuring-the-printer
(defn highlighted-value1 [opts problem]
  (highlighted-value opts (:expound/form problem) (:expound/in problem)))