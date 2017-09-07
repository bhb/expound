(ns expound.alpha
  "Drop-in replacement for clojure.spec.alpha, with
  human-readable `explain` function"
  (:require [expound.paths :as paths]
            [expound.problems :as problems]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            #?(:clj [clojure.main :as clojure.main])
            [clojure.walk :as walk]
            #?(:cljs [goog.string.format])
            #?(:cljs [goog.string])
            [clojure.pprint :as pprint])
  (:refer-clojure :exclude [format]))

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
(def indent-level 2)

(def ^:dynamic *value-str-fn* (fn [_ _ _ _] "NOT IMPLEMENTED"))

#?(:cljs
   (defn format [fmt & args]
     (apply goog.string/format fmt args))
   :clj (def format clojure.core/format))

(s/fdef pprint-str
        :args (s/cat :x any?)
        :ret string?)
(defn pprint-str
  "Returns the pretty-printed string"
  [x]
  (pprint/write x :stream nil))

(s/fdef no-trailing-whitespace
        :args (s/cat :s string?)
        :ret string?)
(defn no-trailing-whitespace
  "Given an potentially multi-line string, returns that string with all
  trailing whitespace removed."
  [s]
  (let [s' (->> s
                string/split-lines
                (map string/trimr)
                (string/join "\n"))]
    (if (= \newline (last s))
      (str s' "\n")
      s')))

(s/fdef indent
        :args (s/cat
               :first-line-indent-level (s/? nat-int?)
               :indent-level (s/? nat-int?)
               :s string?)
        :ret string?)
(defn indent
  "Given an potentially multi-line string, returns that string indented by
   'indent-level' spaces. Optionally, can indent first line and other lines
   different amounts."
  ([s]
   (indent indent-level s))
  ([indent-level s]
   (indent indent-level indent-level s))
  ([first-line-indent rest-lines-indent s]
   (let [[line & lines] (string/split-lines (str s))]
     (string/join "\n"
                  (into [(str (apply str (repeat first-line-indent " ")) line)]
                        (map #(str (apply str (repeat rest-lines-indent " ")) %) lines))))))

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
    (indent (count (str prefix))
            (apply str (repeat max-width "^")))))

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
        s (binding [*print-namespace-maps* false] (pprint-str (walk/prewalk-replace {::irrelevant '...} (summary-form show-valid-values? form path))))
        [line prefix & _more] (re-find regex s)
        highlighted-line (-> line
                             (string/replace (re-pattern relevant) (indent 0 (count prefix) (pprint-str value-at-path)))
                             (str "\n" (highlight-line prefix (pprint-str value-at-path))))]
    ;;highlighted-line
    (no-trailing-whitespace (string/replace s line highlighted-line))))

(s/fdef value-in-context
        :args (s/cat
               :opts map?
               :spec-name (s/nilable #{:args :fn :ret})
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
      (binding [*print-namespace-maps* false] (pr-str value))
      (highlighted-value opts form path))))

(defn spec-str [spec]
  (if (keyword? spec)
    (format
     "%s:\n%s"
     spec
     (indent (pprint-str (s/form spec))))
    (pprint-str (s/form spec))))

(s/fdef specs
        :args (s/cat :problems :spec/problems)
        :ret :spec/specs)
(defn specs
  "Given a collection of problems, returns the specs for those problems, with duplicates removed"
  [problems]
  (->> problems
       (map :via)
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

(defn elide-core-ns [s]
  #?(:cljs (-> s
               (string/replace "cljs.core/" "")
               (string/replace "cljs/core/" ""))
     :clj (string/replace s "clojure.core/" "")))

(defn pprint-fn [f]
  (-> #?(:clj
         (let [[_ ns-n f-n] (re-matches #"(.*)\$(.*?)(__[0-9]+)?" (str f))]
           (str
            (clojure.main/demunge ns-n) "/"
            (clojure.main/demunge f-n)))
         :cljs
         (let [fn-parts (string/split (second (re-find
                                                #"function ([^\(]+)"
                                                (str f)))
                                      #"\$")
               ns-n (string/join "." (butlast fn-parts))
               fn-n  (last fn-parts)]
           (str
            (demunge-str ns-n) "/"
            (demunge-str fn-n))))
      (elide-core-ns)
      (string/replace #"--\d+" "")
      (string/replace #"@[a-zA-Z0-9]+" "")))

(defn pr-pred* [pred]
  (cond
    (or (symbol? pred) (named? pred))
    (name pred)

    (fn? pred)
    (pprint-fn pred)

    :else
    (elide-core-ns (binding [*print-namespace-maps* false] (pprint-str pred)))))

(defn pr-pred [pred spec]
  (if (= ::s/unknown pred)
    (pr-pred* spec)
    (pr-pred* pred)))

(defn show-spec-name [spec-name value]
  (if spec-name
    (str
     (case spec-name
       :args "Function arguments"
       :ret "Return value"
       :fn "Function arguments and return value")
     "\n\n"
     value)
    value))

(defn preds [problems]
  (string/join "\n\nor\n\n" (map (fn [problem]
                                   (indent
                                    (pr-pred (:pred problem)
                                             (:spec problem)))) problems)))

(defn insufficient-input [spec-name val path problem]
  (format
   "%s

should have additional elements. The next element is named `%s` and satisfies

%s"
   (show-spec-name spec-name (indent (*value-str-fn* spec-name val path (value-in val path))))
   (pr-str (first (:expound/path problem)))
   (indent (pr-pred (:pred problem) (:spec problem)))))

(defn extra-input [spec-name val path]
  (format
    "Value has extra input

%s"
    (show-spec-name spec-name (indent (*value-str-fn* spec-name val path (value-in val path))))))

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
      (format
       "%s

%s"
       (section-label "Relevant specs")
       sp-str))))

(defn multi-spec-parts [spec]
  (let [[_multi-spec mm retag]  (s/form spec)]
    {:mm mm :retag retag}))

(defn missing-spec? [problem]
  (= "no method" (:reason problem)))

(defn not-in-set? [problem]
  (set? (:pred problem)))

(defn missing-key? [problem]
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

(defn regex-failure? [problem]
  (contains? #{"Insufficient input" "Extra input"} (:reason problem)))

(defn no-method [spec-name val path problem]
  (let [sp (s/spec (last (:via problem)))
        {:keys [mm retag]} (multi-spec-parts sp)]
    (format
     "Cannot find spec for

 %s

 Spec multimethod:      `%s`
 Dispatch function:     `%s`
 Dispatch value:        `%s`
 "
     (show-spec-name spec-name (indent (*value-str-fn* spec-name val path (value-in val path))))
     (pr-str mm)
     (pr-str retag)
     (pr-str (if retag (retag (value-in val path)) nil)))))

(defmulti problem-group-str (fn [type spec-name _val _path _problems] type))

(defmethod problem-group-str :problem/missing-key [_type spec-name val path problems]
  (assert (apply = (map :val problems)) (str "All values should be the same, but they are " problems))
  (format
   "%s

%s

should contain keys: %s

%s"
   (header-label "Spec failed")
   (show-spec-name spec-name (indent (*value-str-fn* spec-name val path (value-in val path))))
   (string/join "," (map #(str "`" (missing-key (:pred %)) "`") problems))
   (relevant-specs problems)))

(defmethod problem-group-str :problem/not-in-set [_type spec-name val path problems]
  (assert (apply = (map :val problems)) (str "All values should be the same, but they are " problems))
  (s/assert ::singleton problems)
  (format
   "%s

%s

should be one of: %s

%s"
   (header-label "Spec failed")
   (show-spec-name spec-name (indent (*value-str-fn* spec-name val path (value-in val path))))
   (string/join "," (map #(str "`" % "`") (:pred (first problems))))
   (relevant-specs problems)))

(defmethod problem-group-str :problem/missing-spec [_type spec-name val path problems]
  (s/assert ::singleton problems)
  (format
   "%s

%s

%s"
   (header-label "Missing spec")
   (no-method spec-name val path (first problems))
   (relevant-specs problems)))

(defmethod problem-group-str :problem/regex-failure [_type spec-name val path problems]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (format
     "%s

%s

%s"
     (header-label "Syntax error")
     (case (:reason problem)
       "Insufficient input" (insufficient-input spec-name val path problem)
       "Extra input" (extra-input spec-name val path))
     (relevant-specs problems))))

(defmethod problem-group-str :problem/unknown [_type spec-name val path problems]
  (assert (apply = (map :val problems)) (str "All values should be the same, but they are " problems))
  (format
   "%s

%s

should satisfy

%s

%s"
   (header-label "Spec failed")
   (show-spec-name spec-name (indent (*value-str-fn* spec-name val path (value-in val path))))
   (preds problems)
   (relevant-specs problems)))

(defn problem-type [problem]
  (cond
    (not-in-set? problem)
    :problem/not-in-set

    (missing-key? problem)
    :problem/missing-key

    (missing-spec? problem)
    :problem/missing-spec

    (regex-failure? problem)
    :problem/regex-failure

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
    (format "%s:%s
\n"
            (:file caller "<filename missing>")
            (:line caller "<line number missing>"))
    ""))

(defn spec-name [ed]
  (if (::s/failure ed)
    (-> ed ::s/problems first :path first)
    nil))

(defn printer-str [opts explain-data]
  (if-not explain-data
    "Success!\n"
    (binding [*value-str-fn* (get opts :value-str-fn (partial value-in-context
                                                              (merge {:show-valid-values? false}
                                                                     opts)))]
      (let [{:keys [::s/problems ::s/fn ::s/failure]} explain-data
            _ (doseq [problem problems]
                (s/assert (s/nilable #{"Insufficient input" "Extra input" "no method"}) (:reason problem)))
            explain-data' (problems/annotate explain-data)

            
            caller (:expound/caller explain-data')
            form (:expound/form explain-data')
            
            grouped-problems (->> explain-data'                                  
                                  :expound/problems
                                  (problems/leaf-only)
                                  (group-by (juxt :expound/in problem-type))
                                  ;; We attempt to sort the problems by path, but it's not feasible to sort in
                                  ;; all cases, since paths could contain arbitrary user-defined data structures.
                                  ;; If there is an error, we just give up on sorting.
                                  (safe-sort-by first paths/compare-paths))]
        
        (no-trailing-whitespace
         (str
          (instrumentation-info failure caller)
          (format
           "%s

%s
Detected %s %s\n"
           (string/join "\n\n" (for [[[in type] problems] grouped-problems]
                                 (problem-group-str type (spec-name explain-data) form in problems)))
           (section-label)
           (count grouped-problems)
           (if (= 1 (count grouped-problems)) "error" "errors"))))))))

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
