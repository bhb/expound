(ns ^:no-doc expound.alpha2.printer
  (:require [clojure.string :as string]
            [clojure.alpha.spec :as s]
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [expound.alpha2.util :as util]
            [expound.alpha2.ansi :as ansi]
            [expound.alpha2.paths :as paths]
            [clojure.walk :as walk]
            #?(:cljs [goog.string.format]) ; https://github.com/bhb/expound/issues/183
            #?(:cljs [goog.string])        ; https://github.com/bhb/expound/issues/183
            #?(:clj [clojure.main :as main]))
  (:refer-clojure :exclude [format]))

(def indent-level 2)
(def anon-fn-str "<anonymous function>")

;; Unroll this when
;; https://github.com/borkdude/speculative/issues/124#issuecomment-473593685 is fixed
;;;;;;;;;;;;;;;;;;;;;;;;;;;
(s/def :expound.spec/kw-or-conjunction-base
  (s/or
   :kw qualified-keyword?))
(s/def :expound.spec/spec-conjunction2
  (s/cat
   :op (fn [op] (#{'or 'and} op))
   :specs (s/+ :expound.spec/kw-or-conjunction-base)))

(s/def :expound.spec/kw-or-conjunction2
  (s/or
   :kw qualified-keyword?
   :conj :expound.spec/spec-conjunction2))
(s/def :expound.spec/spec-conjunction1
  (s/cat
   :op (fn [op] (#{'or 'and} op))
   :specs (s/+ :expound.spec/kw-or-conjunction2)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/def :expound.spec/kw-or-conjunction
  (s/or
   :kw qualified-keyword?
   :conj :expound.spec/spec-conjunction1))
(s/def :expound.spec/spec-conjunction
  (s/cat
   :op (fn [op] (#{'or 'and} op))
   :specs (s/+ :expound.spec/kw-or-conjunction)))
(s/def :expound.spec/key-spec
  (s/cat :keys #{clojure.alpha.spec/keys
                 }
         :clauses (s/*
                   (s/cat :qualifier #{:req-un :req :opt-un :opt}
                          :specs (s/coll-of :expound.spec/kw-or-conjunction)))))

;; FIXME: Can't do forward reference until 
;; https://github.com/borkdude/speculative/issues/124#issuecomment-473593685
;; is fixed.
;; Until then, I can copy/paste a few levels of nested specs that terminate in the base spec
(s/def :expound.spec/contains-key-pred-base (s/or
                                             :simple (s/cat
                                                      :contains #{contains?}
                                                      :arg #{%}
                                                      :kw keyword?)
                                             ))

(s/def :expound.spec/contains-key-pred3 (s/or
                                              :simple (s/cat
                                                      :contains #{contains?}
                                                      :arg #{%}
                                                      :kw keyword?)
                                             :compound (s/cat
                                                   :op #{and or}
                                                   :clauses (s/+ :expound.spec/contains-key-pred-base))
                                             ))

(s/def :expound.spec/contains-key-pred2 (s/or
                                             :simple (s/cat
                                                      :contains #{contains?}
                                                      :arg #{%}
                                                      :kw keyword?)
                                             :compound (s/cat
                                                   :op #{and or}
                                                   :clauses (s/+ :expound.spec/contains-key-pred3))
                                             ))

(s/def :expound.spec/contains-key-pred1 (s/or
                                             :simple (s/cat
                                                      :contains #{contains?}
                                                      :arg #{%}
                                                      :kw keyword?)
                                             :compound (s/cat
                                                   :op #{and or}
                                                   :clauses (s/+ :expound.spec/contains-key-pred2))
                                             ))

(s/def :expound.spec/contains-key-pred (s/or
                                        :simple (s/cat
                                                 :contains #{contains?}
                                                 :arg #{%}
                                                 :kw keyword?)
                                        :compound (s/cat
                                                   :op #{and or}
                                                   :clauses (s/+ :expound.spec/contains-key-pred1)))

  )

(declare format)

(defn ^:private str-width [lines]
  (apply max (map count lines)))

(defn ^:private max-column-width [rows i]
  (apply max 0 (map #(str-width (string/split-lines (str (nth % i)))) rows)))

(defn ^:private max-row-height [row]
  (apply max 0
         (map #(count (string/split-lines (str %))) row)))

(defn ^:private indented-multirows [column-widths multi-rows]
  (->> multi-rows
       (map
        (fn [multi-row]
          (map
           (fn [row]
             (map-indexed
              (fn [i v]
                (format (str "%-" (nth column-widths i) "s") v))
              row))
           multi-row)))))

(defn ^:private formatted-row [row edge spacer middle]
  (str edge spacer
       (string/join (str spacer middle spacer) row)
       spacer edge))

(defn ^:private table [multirows]
  (let [header (first (first multirows))
        columns-dividers (map #(apply str (repeat (count (str %)) "-")) header)
        header-columns-dividers (map #(apply str (repeat (count (str %)) "=")) header)
        header-divider (formatted-row header-columns-dividers "|" "=" "+")
        row-divider (formatted-row columns-dividers "|" "-" "+")
        formatted-multirows (->> multirows
                                 (map
                                  (fn [multirow]
                                    (map (fn [row] (formatted-row row "|" " " "|")) multirow))))]

    (->>
     (concat [[header-divider]] (repeat [row-divider]))
     (mapcat vector formatted-multirows)
     (butlast) ;; remove the trailing row-divider
     (mapcat seq))))

(defn ^:private multirow [row-height row]
  (let [split-row-contents (mapv (fn [v] (string/split-lines (str v))) row)]
    (for [row-idx (range row-height)]
      (for [col-idx (range (count row))]
        (get-in split-row-contents [col-idx row-idx] "")))))

(defn ^:private multirows [row-heights rows]
  (map-indexed (fn [idx row] (multirow (get row-heights idx) row)) rows))

(defn ^:private formatted-multirows [column-keys map-rows]
  (when-not (empty? map-rows)
    (let [rows (into [column-keys] (map #(map % column-keys) map-rows))
          row-heights (mapv max-row-height rows)
          column-widths (map-indexed
                         (fn [i _] (max-column-width rows i))
                         (first rows))]

      (->>
       rows
       (multirows row-heights)
       (indented-multirows column-widths)))))

(defn table-str [column-keys map-rows]
  (str
   "\n"
   (apply str
          (map
           (fn [line] (str line "\n"))
           (table (formatted-multirows column-keys map-rows))))))

(s/fdef print-table
  :args (s/cat
         :columns (s/? (s/coll-of any?))
         :map-rows (s/coll-of map?)))
(defn print-table
  ([map-rows]
   (print-table (keys (first map-rows)) map-rows))
  ([column-keys map-rows]
   (print (table-str column-keys map-rows))))

;;;; private


(defn keywords [form]
  (->> form
       (tree-seq coll? seq)
       (filter keyword?)))

(defn singleton? [xs]
  (= 1 (count xs)))



(defn specs-from-form [via]
  (let [form (some-> via last s/form)
        conformed (s/conform :expound.spec/key-spec form)]
    ;; The containing spec might not be
    ;; a simple 'keys' call, in which case we give up
    (if (and form
             (not= ::s/invalid conformed))
      (->> (:clauses conformed)
           (map :specs)
           (tree-seq coll? seq)
           (filter
            (fn [x]
              (and (vector? x) (= :kw (first x)))))
           (map second)
           set)
      #{})))

(defn key->spec [keys problems]
  (doseq [p problems]
    (assert (some? (:expound/via p)) util/assert-message))
  (let [vias (map :expound/via problems)
        specs (if (every? qualified-keyword? keys)
                keys
                (if-let [specs (apply set/union (map specs-from-form vias))]
                  specs
                  keys))]
    (reduce
     (fn [m k]
       (assoc m
              k
              (if (qualified-keyword? k)
                k
                (or (->> specs
                         (filter #(= (name k) (name %)))
                         first)
                    "<can't find spec for unqualified spec identifier>"))))
     {}
     keys)))

(defn summarize-key-clause [[branch match]]
  (case branch
    :simple
    (:kw match)

    :compound
    (apply list
           (symbol (name (:op match)))
           (map summarize-key-clause (:clauses match)))))

(defn missing-key [form]
  (let [[branch match] (s/conform :expound.spec/contains-key-pred (nth form 2))]
    (case branch
      :simple
      (:kw match)

      :compound
      (summarize-key-clause [branch match]))))

;;;; public

(defn elide-core-ns [s]
  #?(:cljs (-> s
               (string/replace "cljs.core/" "")
               (string/replace "cljs/core/" ""))
     :clj (string/replace s "clojure.core/" "")))

(defn elide-spec-ns [s]
  #?(:cljs (-> s
               (string/replace "cljs.spec.alpha/" "")
               (string/replace "cljs/spec/alpha" ""))
     :clj (string/replace s "clojure.alpha.spec/" "")))

(defn pprint-fn [f]
  (-> #?(:clj
         (let [[_ ns-n f-n] (re-matches #"(.*)\$(.*?)(__[0-9]+)?" (str f))]
           (if (re-matches #"^fn__\d+\@.*$" f-n)
             anon-fn-str
             (str
              (main/demunge ns-n) "/"
              (main/demunge f-n))))
         :cljs
         (let [fn-parts (string/split (second (re-find
                                               #"object\[([^\( \]]+).*(\n|\])?"
                                               (pr-str f)))
                                      #"\$")
               ns-n (string/join "." (butlast fn-parts))
               fn-n  (last fn-parts)]
           (if (empty? ns-n)
             anon-fn-str
             (str
              (demunge ns-n) "/"
              (demunge fn-n)))))
      (elide-core-ns)
      (string/replace #"--\d+" "")
      (string/replace #"@[a-zA-Z0-9]+" "")))

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
  (if (fn? x)
    (pprint-fn x)
    (pprint/write x :stream nil)))

(defn expand-spec [spec]
  (if (s/get-spec spec)
    (pprint-str (s/form spec))
    spec))

(defn simple-spec-or-name [spec-name]
  (let [expanded (expand-spec spec-name)
        spec-str (elide-spec-ns (elide-core-ns
                                 (if (nil? expanded)
                                   "nil"
                                   expanded)))]

    spec-str))

(defn print-spec-keys* [problems]
  (let [keys (keywords (map #(missing-key (:pred %)) problems))]
    (if (and (empty? (:expound/via (first problems)))
             (some simple-keyword? keys))
      ;; The containing spec is not present in the problems
      ;; and at least one key is not namespaced, so we can't figure out
      ;; the spec they intended.
      nil

      (->> (key->spec keys problems)
           (map (fn [[k v]] {"key" k "spec" (simple-spec-or-name v)}))
           (sort-by #(get % "key"))))))

(defn print-spec-keys [problems]
  (->>
   (print-spec-keys* problems)
   (print-table ["key" "spec"])
   with-out-str
   string/trim))

(defn print-missing-keys [problems]
  (let [keys-clauses (distinct (map (comp missing-key :pred) problems))]
    (if (every? keyword? keys-clauses)
      (string/join ", " (map #(ansi/color % :correct-key) (sort keys-clauses)))
      (str "\n\n"
           (ansi/color (pprint-str
                        (if (singleton? keys-clauses)
                          (first keys-clauses)
                          (apply list
                                 'and
                                 keys-clauses))) :correct-key)))))

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
     (->> lines
          (map #(str (apply str (repeat rest-lines-indent " ")) %))
          (into [(str (apply str (repeat first-line-indent " ")) line)])
          (string/join "\n")))))

(defn escape-replacement [#?(:clj pattern :cljs _pattern) s]
  #?(:clj (if (string? pattern)
            s
            (string/re-quote-replacement s))
     :cljs (string/replace s #"\$" "$$$$")))

(defn blank-form [form]
  (cond
    (map? form)
    (zipmap (keys form) (repeat :expound.alpha2.problems/irrelevant))

    (vector? form)
    (vec (repeat (count form) :expound.alpha2.problems/irrelevant))

    (set? form)
    form

    (or (list? form)
        (seq? form))
    (apply list (repeat (count form) :expound.alpha2.problems/irrelevant))

    :else
    :expound.alpha2.problems/irrelevant))

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
      :expound.alpha2.problems/relevant

      (and (map? form) (paths/kps? k))
      (-> displayed-form
          (dissoc (:key k))
          (assoc (summary-form show-valid-values? (:key k) rst)
                 :expound.alpha2.problems/irrelevant))

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
      (string/join (assoc (vec form) k :expound.alpha2.problems/relevant))

      :else
      (throw (ex-info "Cannot find path segment in form. This can be caused by using conformers to transform values, which is not supported in Expound"
                      {:form form
                       :in in})))))

;; FIXME - this function is not intuitive.
(defn highlight-line
  [prefix replacement]
  (let [max-width (apply max (map #(count (str %)) (string/split-lines replacement)))]
    (indent (count (str prefix))
            (apply str (repeat max-width "^")))))

(defn highlighted-value
  "Given a problem, returns a pretty printed
   string that highlights the problem value"
  [opts problem]
  (let [{:keys [:expound/form :expound/in]} problem
        {:keys [show-valid-values?] :or {show-valid-values? false}} opts
        printed-val (pprint-str (paths/value-in form in))
        relevant (str "(" :expound.alpha2.problems/relevant "|(" :expound.alpha2.problems/kv-relevant "\\s+" :expound.alpha2.problems/kv-relevant "))")
        regex (re-pattern (str "(.*)" relevant ".*"))
        s (binding [*print-namespace-maps* false] (pprint-str (walk/prewalk-replace {:expound.alpha2.problems/irrelevant '...} (summary-form show-valid-values? form in))))
        [line prefix & _more] (re-find regex s)
        highlighted-line (-> line
                             (string/replace (re-pattern relevant) (escape-replacement
                                                                    (re-pattern relevant)
                                                                    (indent 0 (count prefix) (ansi/color printed-val :bad-value))))
                             (str "\n" (ansi/color (highlight-line prefix printed-val)
                                                   :pointer)))]
    ;;highlighted-line
    (no-trailing-whitespace (string/replace s line (escape-replacement line highlighted-line)))))
