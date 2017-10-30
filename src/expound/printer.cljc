(ns expound.printer
  (:require [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [clojure.pprint :as pprint]
            #?(:clj [clojure.main :as clojure.main]))
  (:refer-clojure :exclude [format]))

(def indent-level 2)

;;;; public

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

