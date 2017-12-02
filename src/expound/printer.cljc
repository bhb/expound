(ns expound.printer
  (:require [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [clojure.pprint :as pprint]
            [clojure.walk :as walk]
            #?(:clj [clojure.main :as clojure.main]))
  (:refer-clojure :exclude [format]))

(def indent-level 2)
(def anon-fn-str "<anonymous function>")

(s/def :spec/spec-conjunction
  (s/cat
   :op #{'or 'and}
   :specs (s/+ :spec/kw-or-conjunction)))
(s/def :spec/kw-or-conjunction
  (s/or
   :kw qualified-keyword?
   :conj :spec/spec-conjunction))
(s/def :spec/key-spec
  (s/cat :keys #{'clojure.spec.alpha/keys
                 'cljs.spec.alpha/keys}
         :clauses (s/*
                   (s/cat :qualifier #{:req-un :req :opt-un :opt}
                          :specs (s/coll-of :spec/kw-or-conjunction)))))

;;;; private

(defn key->spec [keys problems]
  (assert (apply = (map :expound/via problems)))
  (doseq [p problems]
    (assert (some? (:expound/via p))))
  (let [via (:expound/via (first problems))
        form (some-> via last s/form)]
    ;; TODO - restore?
    ;;(s/assert (s/nilable :spec/key-spec) form)
    (let [specs (if (every? qualified-keyword? keys)
                  keys
                  (let [conformed (s/conform :spec/key-spec form)]
                    ;; The spec might containing spec might not be
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
                      keys)))]
      (reduce
       (fn [m k]
         (assoc m
                k
                (if (qualified-keyword? k)
                  k
                  (->> specs
                       (filter #(= (name k) (name %)))
                       first))))
       {}
       keys))))

(defn expand-spec [spec]
  (let [!seen-specs (atom #{})]
    (walk/prewalk
     (fn [x]
       (if-not (qualified-keyword? x)
         x
         (if-let [sp (s/get-spec x)]
           (if-not (contains? @!seen-specs x)
             (do
               (swap! !seen-specs conj x)
               (s/form sp))
             x)
           x)))
     (s/form spec))))

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
     :clj (string/replace s "clojure.spec.alpha/" "")))

(defn pprint-fn [f]
  (-> #?(:clj
         (let [[_ ns-n f-n] (re-matches #"(.*)\$(.*?)(__[0-9]+)?" (str f))]
           (if (re-matches #"^fn__\d+\@.*$" f-n)
             anon-fn-str
             (str
              (clojure.main/demunge ns-n) "/"
              (clojure.main/demunge f-n))))
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
              (demunge-str ns-n) "/"
              (demunge-str fn-n)))))
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

(defn simple-spec-or-name [spec-name]
  (let [spec-str (elide-spec-ns (elide-core-ns (pr-str (expand-spec spec-name))))]
    (if (or
         ;; TODO - extract to named constant
         (< 100 (count spec-str))
         (string/includes? spec-str "\n"))
      spec-name
      spec-str)))

(defn print-spec-keys [problems]
  (let [keys
        (map #(missing-key (:pred %)) problems)]
    (if (and (empty? (:expound/via (first problems)))
             (some simple-keyword? keys))
      ;; The containing spec is not present in the problems
      ;; and at least one key is not namespaced, so we can't figure out
      ;; the spec they intended.
      ;; TODO - combine this with print-missing-keys
      (string/join ", " (sort (map #(str "`" (missing-key (:pred %)) "`") problems)))
      (->> (key->spec keys problems)
           (map (fn [[k v]] {"key" k "spec" (simple-spec-or-name v)}))
           (sort-by #(get % "key"))
           (pprint/print-table ["key" "spec"])
           with-out-str))))

(defn print-missing-keys [problems]
  (string/join ", " (sort (map #(str "`" (missing-key (:pred %)) "`") problems))))

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

