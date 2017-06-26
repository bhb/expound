(ns expound.spec
  "Drop-in replacement for clojure.spec.alpha, with 
  human-readable `explain` function"
  (:require [clojure.data]
            [cljs.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.walk :as walk]
            [goog.string :as gstring]
            [cljs.pprint :as pprint]))

;;;;;; specs   ;;;;;;

(s/def ::singleton (s/coll-of any? :count 1))

;;;;;; private ;;;;;;

(def header-size 35)
(def section-size 25)

(defn pprint-str [x]
  (pprint/write x :stream nil))

(def indent-level 2)

(defn no-trailing-whitespace [s]
  (->> s
       string/split-lines
       (map string/trimr)
       (string/join "\n")))

(def headers {:problem/missing-key   "Spec failed"
              :problem/not-in-set    "Spec failed"
              :problem/missing-spec  "Missing spec"
              :problem/regex-failure "Syntax error"
              :problem/unknown       "Spec failed"})

(defn indent
  ([s]
   (indent indent-level s))
  ([indent-level s]
   (indent indent-level indent-level s))
  ([first-line-indent rest-lines-indent s]
   (let [[line & lines] (string/split-lines s)]
     (string/join "\n"
                  (into [(str (apply str (repeat first-line-indent " ")) line)]
                        (map #(str (apply str (repeat rest-lines-indent " ")) %) lines))))))

(defn prefix-path?
  "True iff partial-path is a prefix of full-path"
  [partial-path full-path]
  (and (< (count partial-path) (count full-path))
       (= partial-path
          (subvec full-path 0 (count partial-path)))))

(def mapv-indexed (comp vec map-indexed))

(defn walk-with-path
  "Like walk but passes both the path and current value to inner and outer."
  ([inner outer path form]
   (cond
     (vector? form) (outer path (mapv-indexed (fn [idx x] (inner (conj path idx) x)) form))
     (record? form) (outer path form)
     (map? form)    (outer path (reduce-kv (fn [m k v]
                                             (assoc m k (inner (conj path k) v)))
                                           {}
                                           form))
     :else          (outer path form))))

(defn postwalk-with-path
  ([f form] (postwalk-with-path f [] form))
  ([f path form]
   (walk-with-path (partial postwalk-with-path f) f path form)))

(defn summary-form
  "Given a form and a path to highlight, returns a data structure that marks
   the highlighted and irrelevant data"
  [form highlighted-path]
  (postwalk-with-path
   (fn [path x]
     (cond
       (= ::irrelevant x)
       ::irrelevant

       (= path highlighted-path)
       ::relevant

       (prefix-path? path highlighted-path)
       x

       :else
       ::irrelevant))
   form))

(defn highlight-line [prefix replacement]
  (let [max-width (apply max (map #(count (str %)) (string/split-lines replacement)))]
    (indent (count (str prefix))
            (apply str (repeat max-width "^")))))

;; TODO - delete
(defn get-or-nth
  ([c x]
   (get-or-nth c x nil))
  ([c x not-found]
   (if (or (zero? x) (pos-int? x))
     (cond
       (map? c)
       (nth (seq c) x not-found)
       
       (associative? c)
       (get c x not-found)
       
       :else
       (nth c x not-found))
     
     (get c x not-found))))

(defn get-or-nth-in
  "Returns the value in a nested sequential structure,
  where ks is a sequence of keys or positions. Returns nil if the key
  is not present, or the not-found value if supplied."  
  {:added "1.2"
   :static true}
  ([m ks]
     (reduce get-or-nth m ks))
  ([m ks not-found]
     (loop [sentinel lookup-sentinel
            m m
            ks (seq ks)]
       (if-not (nil? ks)
         (let [m (get-or-nth m (first ks) sentinel)]
           (if (identical? sentinel m)
             not-found
             (recur sentinel m (next ks))))
         m))))

(defn paths-for-val [form v]
  (let [paths (atom [])]
    (postwalk-with-path
     (fn [path x]
       (when (= x v)
         (swap! paths conj path))
       x)
     form)
    @paths))

;; Works around some issues in spec where the 'in'
;; value does not actually get us the value.
(defn adjust-in-path [form problem]
  (if (contains? #{"Insufficient input" "Extra input"} (:reason problem))
    (assoc problem :in1 (:in problem))
    (let [{:keys [in val]} problem
          found-val (get-in form in)]
      (if (= found-val val)
        (assoc problem :in1 in)
        (let [matching-paths (paths-for-val form val)]
          (case (count matching-paths)
            0 (throw (js/Error. (str "Cannot find " (pr-str val) " in " (pr-str form))))
            1 (assoc problem :in1 (first matching-paths))
            (throw (js/Error. (str "'in' value was incorrect and found multiple copies of " (pr-str val) " in " (pr-str form))))))))))

(defn highlighted-form
  "Given a form and a path into that form, returns a pretty printed
   string that highlights the value at the path."
  [form path expected-val]
  (let [value-at-path (get-in form path)
        regex (re-pattern (str "(.*)" ::relevant ".*"))
        s (pprint-str (walk/prewalk-replace {::irrelevant '...} (summary-form form path)))
        [line prefix & _more] (re-find regex s)
        highlighted-line (-> line
                             (string/replace (str ::relevant) (indent 0 (count prefix) (pprint-str value-at-path)))
                             (str "\n" (highlight-line prefix (pprint-str value-at-path))))]

    (no-trailing-whitespace (string/replace s line highlighted-line))))

(defn value-in-context
  "Given a form and a path into that form, returns a string
   that helps the user understand where that path is located
   in the form"
  [form path expected-val]
  (let [val (get-in form path)]
    (if (== form val)
      (pr-str val)
      (highlighted-form form path expected-val))))

(defn spec-str [spec]
  (if (keyword? spec)
    (gstring/format
     "%s:\n%s"
     spec
     (indent (pprint-str (s/form spec))))
    (pprint-str (s/form spec))))

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

(defn preds [preds]
  (string/join "\n\nor\n\n" (map indent preds)))

(defn insufficient-input [val path problem]
  (gstring/format
   "%s

should have additional elements. The next element is named `%s` and satisfies

%s"
   (indent (value-in-context val path (:val problem)))
   (pr-str (first (:path problem)))
   (indent (:pred problem))))

(defn extra-input [val path expected-val]
  (gstring/format
   "Value has extra input

%s"
   (indent (value-in-context val path expected-val))))

(defn missing-key [form]
  (let [[_contains _arg key-keyword] form]
    (s/assert #{'contains?} _contains)
    key-keyword))

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
  (gstring/format
   "%s

%s"
   (section-label "Relevant specs")
   (specs-str problems)))

(defn multi-spec-parts [spec]
  (let [[_multi-spec mm retag]  (s/form spec)]
    {:mm mm :retag retag}))

(defn missing-spec? [problem]
  (= "no method" (:reason problem)))

(defn not-in-set? [problem]
  (set? (:pred problem)))

(defn missing-key? [problem]
  (let [pred (:pred problem)]
    (and (list? pred)
         (map? (:val problem))
         (= 'contains? (first pred)))))

(defn regex-failure? [problem]
  (contains? #{"Insufficient input" "Extra input"} (:reason problem)))

(defn no-method [val path problem]
  (let [sp (s/spec (last (:via problem)))
        {:keys [mm retag]} (multi-spec-parts sp)]
    (gstring/format
     "Cannot find spec for

%s

Spec multimethod:      `%s`
Dispatch function:     `%s`
Dispatch value:        `%s`
"
     (indent (value-in-context val path (:val problem)))
     (pr-str mm)
     (pr-str retag)
     (pr-str (if retag (retag (get-in val path)) nil)))))

(defmulti problem-group-str (fn [type _val _path _problems] type))

(defmethod problem-group-str :problem/missing-key [_type val path problems]
    (assert (apply = (map :val problems)) "All values should be the same")
  (gstring/format
   "%s

%s

should contain keys: %s

%s"
   (header-label "Spec failed")
   (indent (value-in-context val path (:val (first problems))))
   (string/join "," (map #(str "`" (missing-key (:pred %)) "`") problems))
   (relevant-specs problems)))

(defmethod problem-group-str :problem/not-in-set [_type val path problems]
  (assert (apply = (map :val problems)) "All values should be the same")
  (s/assert ::singleton problems)
  (gstring/format
   "%s

%s

should be one of: %s

%s"
   (header-label "Spec failed")
   (indent (value-in-context val path (:val (first problems))))
   (string/join "," (map #(str "`" % "`") (:pred (first problems))))
   (relevant-specs problems)))

(defmethod problem-group-str :problem/missing-spec [_type val path problems]
  (s/assert ::singleton problems)
  (gstring/format
   "%s

%s

%s"
   (header-label "Missing spec")
   (no-method val path (first problems))
   (relevant-specs problems)))

(defmethod problem-group-str :problem/regex-failure [_type val path problems]
  (s/assert ::singleton problems)
  (let [problem (first problems)]
    (gstring/format
     "%s

%s

%s"
     (header-label "Syntax error")
     (case (:reason problem)
       "Insufficient input" (insufficient-input val path problem)
       "Extra input" (extra-input val path (:val problem)))
     (relevant-specs problems))))

(defmethod problem-group-str :problem/unknown [_type val path problems]
  (assert (apply = (map :val problems)) "All values should be the same")
  (gstring/format
   "%s

%s

should satisfy

%s

%s"
   (header-label "Spec failed")
   (indent (value-in-context val path (:val (first problems))))
   (preds (map :pred problems))
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

(defn leaf-problems
  "Given a collection of problems, returns only those problems with data on the 'leaves' of the data"
  [problems]
  (let [paths-to-data (into #{} (map :in1 problems))]
    (remove
     (fn [problem]
       (some
        (fn [path]
          (prefix-path? (:in1 problem) path))
        paths-to-data))
     problems)))

(defn path+problem-type->problems
  "Returns problems grouped by path (i.e. the 'in' key) then and then problem-type"
  [problems]
  (group-by (juxt :in1 problem-type) problems))

;;;;;; public ;;;;;;

(defn pretty-explain-str
  "Given a spec and a value that fails to conform, returns a human-readable explanation as a string."
  [spec val]
  (let [problems (::s/problems (s/explain-data spec val))
        _ (doseq [problem problems]
            (s/assert (s/nilable #{"Insufficient input" "Extra input" "no method"}) (:reason problem)))
        leaf-problems (leaf-problems (map (partial adjust-in-path val) (::s/problems (s/explain-data spec val))))
        grouped-problems (sort (path+problem-type->problems leaf-problems))]
    (if (empty? problems)
      "Success!\n"
      (let [problems-str (string/join "\n\n" (for [[[in1 type] problems] grouped-problems]
                                               (problem-group-str type val in1 problems)))]
        (no-trailing-whitespace
         (gstring/format
          "%s

%s
Detected %s %s"
          problems-str
          (section-label)
          (count grouped-problems)
          (if (= 1 (count grouped-problems)) "error" "errors")))))))
