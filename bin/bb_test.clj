;; TODO - carve out CLJS

(ns bb
  (:require [babashka.deps :as deps]
            [clojure.test :refer [is testing deftest]]
            [clojure.string :as string]
            [clojure.walk :as walk]))

(deps/add-deps
 '{:deps {borkdude/spartan.spec {:git/url "https://github.com/borkdude/spartan.spec"
                                 :sha "d3b4e98ec2b8504868e5a6193515c5d23df15264"}
          expound/expound {:local/root "."}}})

;; Loading spartan.spec will create a namespace clojure.spec.alpha for compatibility:
(require 'spartan.spec
         '[clojure.spec.alpha :as s]
         '[expound.alpha :as expound]
         '[expound.printer :as printer]
         '[expound.problems :as problems]
         '[expound.ansi :as ansi])

(defn pf
  "Fixes platform-specific namespaces and also formats using printf syntax"
  [s & args]
  (apply printer/format
         (string/replace s "pf." "clojure.")
         args))

(defn formatted-exception [printer-options f]
  (let [printer (expound/custom-printer printer-options)
        exception-data (binding [s/*explain-out* printer]
                         (try
                           (f)
                           (catch Exception e (Throwable->map e))))
        ed #?(:cljs (-> exception-data :data)
              :clj (-> exception-data :via last :data))
        cause# (-> #?(:cljs (:message exception-data)
                      :clj (:cause exception-data))
                   (clojure.string/replace #"Call to (.*) did not conform to spec:"
                                           "Call to #'$1 did not conform to spec."))]

    (str cause#
         (if (re-find  #"Detected \d+ error" cause#)
           ""
           (str "\n"
                (with-out-str (printer ed)))))))

(def inverted-ansi-codes
  (reduce
   (fn [m [k v]]
     (assoc m (str v) k))
   {}
   ansi/sgr-code))

(defn readable-ansi [s]
  (string/replace
   s
   #"\x1b\[([0-9]*)m"
   #(str "<" (string/upper-case (name (get inverted-ansi-codes (second %)))) ">")))

(deftest expound-output-ends-in-newline
  (is (= "\n" (str (last (expound/expound-str string? 1)))))
  (is (= "\n" (str (last (expound/expound-str string? ""))))))

(deftest expound-prints-expound-str
  (is (=
       (expound/expound-str string? 1)
       (with-out-str (expound/expound string? 1)))))

(deftest predicate-spec
  (is (= (pf "-- Spec failed --------------------

  1

should satisfy

  string?

-------------------------
Detected 1 error\n")
         (expound/expound-str string? 1))))

(s/def :simple-type-based-spec/str string?)

(deftest simple-type-based-spec
  (testing "valid value"
    (is (= "Success!\n"
           (expound/expound-str :simple-type-based-spec/str ""))))

  (testing "invalid value"
    (is (=
         (pf "-- Spec failed --------------------

  1

should satisfy

  string?

-- Relevant specs -------

:simple-type-based-spec/str:
  pf.core/string?

-------------------------
Detected 1 error\n")
         (expound/expound-str :simple-type-based-spec/str 1)))))

(s/def :set-based-spec/tag #{:foo :bar})
(s/def :set-based-spec/nilable-tag (s/nilable :set-based-spec/tag))
(s/def :set-based-spec/set-of-one #{:foobar})

(s/def :set-based-spec/one-or-two (s/or
                                   :one (s/cat :a #{:one})
                                   :two (s/cat :b #{:two})))

(deftest set-based-spec
  (testing "prints valid options"
    (is (= "-- Spec failed --------------------

  :baz

should be one of: :bar, :foo

-- Relevant specs -------

:set-based-spec/tag:
  #{:bar :foo}

-------------------------
Detected 1 error\n"
           (expound/expound-str :set-based-spec/tag :baz))))

  (testing "prints combined options for various specs"
    (is (= (pf "-- Spec failed --------------------

  [:three]
   ^^^^^^

should be one of: :one, :two

-- Relevant specs -------

:set-based-spec/one-or-two:
  (pf.spec.alpha/or
   :one
   (pf.spec.alpha/cat :a #{:one})
   :two
   (pf.spec.alpha/cat :b #{:two}))

-------------------------
Detected 1 error\n")
           (expound/expound-str :set-based-spec/one-or-two [:three]))))

  (testing "nilable version"
    (is (= (pf "-- Spec failed --------------------

  :baz

should be one of: :bar, :foo

or

should satisfy

  nil?

-- Relevant specs -------

:set-based-spec/tag:
  #{:bar :foo}
:set-based-spec/nilable-tag:
  (pf.spec.alpha/nilable :set-based-spec/tag)

-------------------------
Detected 1 error\n")
           (expound/expound-str :set-based-spec/nilable-tag :baz))))
  (testing "single element spec"
    (is (= (pf "-- Spec failed --------------------

  :baz

should be: :foobar

-- Relevant specs -------

:set-based-spec/set-of-one:
  #{:foobar}

-------------------------
Detected 1 error\n")
           (expound/expound-str :set-based-spec/set-of-one :baz)))))

(s/def :nested-type-based-spec/str string?)
(s/def :nested-type-based-spec/strs (s/coll-of :nested-type-based-spec/str))

(deftest nested-type-based-spec
  (is (=
       (pf "-- Spec failed --------------------

  [... ... 33]
           ^^

should satisfy

  string?

-- Relevant specs -------

:nested-type-based-spec/str:
  pf.core/string?
:nested-type-based-spec/strs:
  (pf.spec.alpha/coll-of :nested-type-based-spec/str)

-------------------------
Detected 1 error\n")
       (expound/expound-str :nested-type-based-spec/strs ["one" "two" 33]))))

(s/def :nested-type-based-spec-special-summary-string/int int?)
(s/def :nested-type-based-spec-special-summary-string/ints (s/coll-of :nested-type-based-spec-special-summary-string/int))

(deftest nested-type-based-spec-special-summary-string
  (is (=
       (pf "-- Spec failed --------------------

  [... ... \"...\"]
           ^^^^^

should satisfy

  int?

-- Relevant specs -------

:nested-type-based-spec-special-summary-string/int:
  pf.core/int?
:nested-type-based-spec-special-summary-string/ints:
  (pf.spec.alpha/coll-of
   :nested-type-based-spec-special-summary-string/int)

-------------------------
Detected 1 error\n")
       (expound/expound-str :nested-type-based-spec-special-summary-string/ints [1 2 "..."]))))

(s/def :or-spec/str-or-int (s/or :int int? :str string?))
(s/def :or-spec/vals (s/coll-of :or-spec/str-or-int))

(s/def :or-spec/str string?)
(s/def :or-spec/int int?)
(s/def :or-spec/m-with-str (s/keys :req [:or-spec/str]))
(s/def :or-spec/m-with-int (s/keys :req [:or-spec/int]))
(s/def :or-spec/m-with-str-or-int (s/or :m-with-str :or-spec/m-with-str
                                        :m-with-int :or-spec/m-with-int))

(deftest or-spec
  (testing "simple value"
    (is (= (pf "-- Spec failed --------------------

  :kw

should satisfy

  int?

or

  string?

-- Relevant specs -------

:or-spec/str-or-int:
  (pf.spec.alpha/or :int pf.core/int? :str pf.core/string?)

-------------------------
Detected 1 error\n")
           (expound/expound-str :or-spec/str-or-int :kw))))
  (testing "collection of values"
    (is (= (pf "-- Spec failed --------------------

  [... ... :kw ...]
           ^^^

should satisfy

  int?

or

  string?

-- Relevant specs -------

:or-spec/str-or-int:
  (pf.spec.alpha/or :int pf.core/int? :str pf.core/string?)
:or-spec/vals:
  (pf.spec.alpha/coll-of :or-spec/str-or-int)

-------------------------
Detected 1 error\n")
           (expound/expound-str :or-spec/vals [0 "hi" :kw "bye"]))))
  (is (= "-- Spec failed --------------------

  50

should satisfy

  coll?

-------------------------
Detected 1 error
"
         (expound/expound-str (s/or
                               :strs (s/coll-of string?)
                               :ints (s/coll-of int?))
                              50)))
  (is (= "-- Spec failed --------------------

  50

should be one of: \"a\", \"b\", 1, 2

-------------------------
Detected 1 error
"
         (expound/expound-str
          (s/or
           :letters #{"a" "b"}
           :ints #{1 2})
          50)))
  (is (= (pf "-- Spec failed --------------------

  {}

should contain keys: :or-spec/int, :or-spec/str

| key          | spec    |
|==============+=========|
| :or-spec/int | int?    |
|--------------+---------|
| :or-spec/str | string? |

-- Relevant specs -------

:or-spec/m-with-int:
  (pf.spec.alpha/keys :req [:or-spec/int])
:or-spec/m-with-str:
  (pf.spec.alpha/keys :req [:or-spec/str])
:or-spec/m-with-str-or-int:
  (pf.spec.alpha/or
   :m-with-str
   :or-spec/m-with-str
   :m-with-int
   :or-spec/m-with-int)

-------------------------
Detected 1 error
")
         (expound/expound-str :or-spec/m-with-str-or-int {})))
  (testing "de-dupes keys"
    (is (= "-- Spec failed --------------------

  {}

should contain keys: :or-spec/str

| key          | spec    |
|==============+=========|
| :or-spec/str | string? |

-------------------------
Detected 1 error
"
           (expound/expound-str (s/or :m-with-str1 (s/keys :req [:or-spec/str])
                                      :m-with-int2 (s/keys :req [:or-spec/str])) {})))))

(s/def :and-spec/name (s/and string? #(pos? (count %))))
(s/def :and-spec/names (s/coll-of :and-spec/name))

(deftest and-spec
  (testing "simple value"
    (is (= (pf "-- Spec failed --------------------

  \"\"

should satisfy

  (fn [%%] (pos? (count %%)))

-- Relevant specs -------

:and-spec/name:
  (pf.spec.alpha/and
   pf.core/string?
   (fn [%%] (pf.core/pos? (pf.core/count %%))))

-------------------------
Detected 1 error\n")
           (expound/expound-str :and-spec/name ""))))

  (testing "shows both failures in order"
    (is (=
         (pf "-- Spec failed --------------------

  [... ... \"\" ...]
           ^^

should satisfy

  %s

-- Relevant specs -------

:and-spec/name:
  (pf.spec.alpha/and
   pf.core/string?
   (fn [%%] (pf.core/pos? (pf.core/count %%))))
:and-spec/names:
  (pf.spec.alpha/coll-of :and-spec/name)

-- Spec failed --------------------

  [... ... ... 1]
               ^

should satisfy

  string?

-- Relevant specs -------

:and-spec/name:
  (pf.spec.alpha/and
   pf.core/string?
   (fn [%%] (pf.core/pos? (pf.core/count %%))))
:and-spec/names:
  (pf.spec.alpha/coll-of :and-spec/name)

-------------------------
Detected 2 errors\n"
             #?(:cljs "(fn [%] (pos? (count %)))"
                :clj "(fn [%] (pos? (count %)))"))
         (expound/expound-str :and-spec/names ["bob" "sally" "" 1])))))

(s/def :coll-of-spec/big-int-coll (s/coll-of int? :min-count 10))

(deftest coll-of-spec
  (testing "min count"
    (is (=
         (pf "-- Spec failed --------------------

  []

should satisfy

  (<= 10 (count %%) %s)

-- Relevant specs -------

:coll-of-spec/big-int-coll:
  (pf.spec.alpha/coll-of pf.core/int? :min-count 10)

-------------------------
Detected 1 error\n"
             #?(:cljs "9007199254740991"
                :clj "Integer/MAX_VALUE"))
         (expound/expound-str :coll-of-spec/big-int-coll [])))))

(s/def :cat-spec/kw (s/cat :k keyword? :v any?))
(s/def :cat-spec/set (s/cat :type #{:foo :bar} :str string?))
(s/def :cat-spec/alt* (s/alt :s string? :i int?))
(s/def :cat-spec/alt (s/+ :cat-spec/alt*))
(s/def :cat-spec/alt-inline (s/+ (s/alt :s string? :i int?)))
(s/def :cat-spec/any (s/cat :x (s/+ any?))) ;; Not a useful spec, but worth testing

(deftest cat-spec
  (testing "too few elements"
    (is (= (pf "-- Syntax error -------------------

  []

should have additional elements. The next element \":k\" should satisfy

  keyword?

-- Relevant specs -------

:cat-spec/kw:
  (pf.spec.alpha/cat :k pf.core/keyword? :v pf.core/any?)

-------------------------
Detected 1 error\n")
           (expound/expound-str :cat-spec/kw [])))
    (is (= (pf "-- Syntax error -------------------

  []

should have additional elements. The next element \":type\" should be one of: :bar, :foo

-- Relevant specs -------

:cat-spec/set:
  (pf.spec.alpha/cat :type #{:bar :foo} :str pf.core/string?)

-------------------------
Detected 1 error\n")
           (expound/expound-str :cat-spec/set [])))
    (is (= (pf "-- Syntax error -------------------

  [:foo]

should have additional elements. The next element \":v\" should satisfy

  any?

-- Relevant specs -------

:cat-spec/kw:
  (pf.spec.alpha/cat :k pf.core/keyword? :v pf.core/any?)

-------------------------
Detected 1 error\n")
           (expound/expound-str :cat-spec/kw [:foo])))
    ;; This isn't ideal, but requires a fix from clojure
    ;; https://clojure.atlassian.net/browse/CLJ-2364
    ;; Commenting out since I don't think Clojure has this right either
    #_(is (= (pf "-- Syntax error -------------------

  []

should have additional elements. The next element should satisfy

  (pf.spec.alpha/alt :s string? :i int?)

-- Relevant specs -------

:cat-spec/alt*:
  (pf.spec.alpha/alt :s pf.core/string? :i pf.core/int?)
:cat-spec/alt:
  (pf.spec.alpha/+ :cat-spec/alt*)

-------------------------
Detected 1 error\n")
           (expound/expound-str :cat-spec/alt [])))
    (is (= (pf "-- Syntax error -------------------

  []

should have additional elements. The next element should satisfy

  (pf.spec.alpha/alt :s string? :i int?)

-- Relevant specs -------

:cat-spec/alt-inline:
  (pf.spec.alpha/+
   (pf.spec.alpha/alt :s pf.core/string? :i pf.core/int?))

-------------------------
Detected 1 error\n")
           (expound/expound-str :cat-spec/alt-inline [])))
    (is (= (pf "-- Syntax error -------------------

  []

should have additional elements. The next element \":x\" should satisfy

  any?

-- Relevant specs -------

:cat-spec/any:
  (pf.spec.alpha/cat :x (pf.spec.alpha/+ pf.core/any?))

-------------------------
Detected 1 error\n")
           (expound/expound-str :cat-spec/any []))))
  (testing "too many elements"
    (is (= (pf "-- Syntax error -------------------

  [... ... :bar ...]
           ^^^^

has extra input

-- Relevant specs -------

:cat-spec/kw:
  (pf.spec.alpha/cat :k pf.core/keyword? :v pf.core/any?)

-------------------------
Detected 1 error\n")
           (expound/expound-str :cat-spec/kw [:foo 1 :bar :baz])))))

(s/def :keys-spec/name string?)
(s/def :keys-spec/age int?)
(s/def :keys-spec/user (s/keys :req [:keys-spec/name]
                               :req-un [:keys-spec/age]))

(s/def :key-spec/state string?)
(s/def :key-spec/city string?)
(s/def :key-spec/zip pos-int?)

(s/def :keys-spec/user2 (s/keys :req [(and :keys-spec/name
                                           :keys-spec/age)]
                                :req-un [(or
                                          :key-spec/zip
                                          (and
                                           :key-spec/state
                                           :key-spec/city))]))

(s/def :keys-spec/user3 (s/keys :req-un [(or
                                          :key-spec/zip
                                          (and
                                           :key-spec/state
                                           :key-spec/city))]))

(s/def :keys-spec/user4 (s/keys :req []))

(defmulti key-spec-mspec :tag)
(defmethod key-spec-mspec :int [_] (s/keys :req-un [::tag ::i]))
(defmethod key-spec-mspec :string [_] (s/keys :req-un [::tag ::s]))

(deftest keys-spec
  (testing "missing keys"
    (is (= (pf "-- Spec failed --------------------

  {}

should contain keys: :age, :keys-spec/name

| key             | spec    |
|=================+=========|
| :age            | int?    |
|-----------------+---------|
| :keys-spec/name | string? |

-- Relevant specs -------

:keys-spec/user:
  %s

-------------------------
Detected 1 error\n"
               #?(:cljs "(cljs.spec.alpha/keys :req [:keys-spec/name] :req-un [:keys-spec/age])"
                  :clj "(clojure.spec.alpha/keys\n   :req\n   [:keys-spec/name]\n   :req-un\n   [:keys-spec/age])"))
           (expound/expound-str :keys-spec/user {}))))
  ;; FIXME - I'm guessing spartan.spec doesn't work for compound keys i.e. (and :state :city)
  #_(testing "missing compound keys"
    (is (= (pf "-- Spec failed --------------------

  {}

should contain keys:

(and (and :keys-spec/name :keys-spec/age) (or :zip (and :state :city)))

| key             | spec     |
|=================+==========|
| :city           | string?  |
|-----------------+----------|
| :state          | string?  |
|-----------------+----------|
| :zip            | pos-int? |
|-----------------+----------|
| :keys-spec/age  | int?     |
|-----------------+----------|
| :keys-spec/name | string?  |

-- Relevant specs -------

:keys-spec/user2:
  (pf.spec.alpha/keys
   :req
   [(and :keys-spec/name :keys-spec/age)]
   :req-un
   [(or :key-spec/zip (and :key-spec/state :key-spec/city))])

-------------------------
Detected 1 error\n")
           (expound/expound-str :keys-spec/user2 {})))
    (is (= (pf "-- Spec failed --------------------

  {}

should contain keys:

(or :zip (and :state :city))

| key    | spec     |
|========+==========|
| :city  | string?  |
|--------+----------|
| :state | string?  |
|--------+----------|
| :zip   | pos-int? |

-- Relevant specs -------

:keys-spec/user3:
  (pf.spec.alpha/keys
   :req-un
   [(or :key-spec/zip (and :key-spec/state :key-spec/city))])

-------------------------
Detected 1 error\n")
           (expound/expound-str :keys-spec/user3 {}))))

  ;; spartan.spec doesn't support multi-spec yet
  #_(testing "inline spec with req-un"
    (is (= (pf "-- Spec failed --------------------

  {}

should contain keys: :age, :name

| key   | spec    |
|=======+=========|
| :age  | int?    |
|-------+---------|
| :name | string? |

-------------------------
Detected 1 error\n"
               #?(:cljs "(cljs.spec.alpha/keys :req [:keys-spec/name] :req-un [:keys-spec/age])"
                  :clj "(clojure.spec.alpha/keys\n   :req\n   [:keys-spec/name]\n   :req-un\n   [:keys-spec/age])"))
           (expound/expound-str (s/keys :req-un [:keys-spec/name :keys-spec/age]) {})))
    (s/def :key-spec/mspec (s/multi-spec key-spec-mspec :tag))
    (s/def :key-spec/i int?)
    (s/def :key-spec/s string?)
    ;; We can't inspect the contents of a multi-spec (to figure out
    ;; which spec we mean by :i), so this is the best we can do.
    (is (= "-- Spec failed --------------------

  {:tag :int}

should contain key: :i

| key | spec                                              |
|=====+===================================================|
| :i  | <can't find spec for unqualified spec identifier> |

-------------------------
Detected 1 error\n"
           (expound/expound-str
            :key-spec/mspec
            {:tag :int}
            {:print-specs? false}))))

  (testing "invalid key"
    (is (= (pf "-- Spec failed --------------------

  {:age ..., :keys-spec/name :bob}
                             ^^^^

should satisfy

  string?

-- Relevant specs -------

:keys-spec/name:
  pf.core/string?
:keys-spec/user:
  %s

-------------------------
Detected 1 error\n"
               #?(:cljs "(cljs.spec.alpha/keys :req [:keys-spec/name] :req-un [:keys-spec/age])"
                  :clj "(clojure.spec.alpha/keys\n   :req\n   [:keys-spec/name]\n   :req-un\n   [:keys-spec/age])"))
           (expound/expound-str :keys-spec/user {:age 1 :keys-spec/name :bob}))))
  (testing "contains compound specs"
    (s/def :keys-spec/states (s/coll-of :key-spec/state :kind vector?))
    (s/def :keys-spec/address (s/keys :req [:key-spec/city :key-space/state]))
    (s/def :keys-spec/cities (s/coll-of :key-spec/city :kind set?))
    (s/def :keys-spec/locations (s/keys :req-un [:keys-spec/states
                                                 :keys-spec/address
                                                 :keys-spec/locations]))
    (is (=
         "-- Spec failed --------------------

  {}

should contain keys: :address, :locations, :states

| key        | spec                                                          |
|============+===============================================================|
| :address   | (keys :req [:key-spec/city :key-space/state])                 |
|------------+---------------------------------------------------------------|
| :locations | (keys                                                         |
|            |  :req-un                                                      |
|            |  [:keys-spec/states :keys-spec/address :keys-spec/locations]) |
|------------+---------------------------------------------------------------|
| :states    | (coll-of :key-spec/state :kind vector?)                       |

-------------------------
Detected 1 error
"
         (expound/expound-str :keys-spec/locations {} {:print-specs? false})))))

(s/def :keys-spec/foo string?)
(s/def :keys-spec/bar string?)
(s/def :keys-spec/baz string?)
(s/def :keys-spec/qux (s/or :string string?
                            :int int?))
(s/def :keys-spec/child-1 (s/keys :req-un [:keys-spec/baz :keys-spec/qux]))
(s/def :keys-spec/child-2 (s/keys :req-un [:keys-spec/bar :keys-spec/child-1]))

(s/def :keys-spec/map-spec-1 (s/keys :req-un [:keys-spec/foo
                                              :keys-spec/bar
                                              :keys-spec/baz]))
(s/def :keys-spec/map-spec-2 (s/keys :req-un [:keys-spec/foo
                                              :keys-spec/bar
                                              :keys-spec/qux]))
(s/def :keys-spec/map-spec-3 (s/keys :req-un [:keys-spec/foo
                                              :keys-spec/child-2]))

(deftest grouping-and-key-specs
  (is (= (pf
          "-- Spec failed --------------------

  {:foo 1.2, :bar ..., :baz ...}
        ^^^

should satisfy

  string?

-- Spec failed --------------------

  {:foo ..., :bar 123, :baz ...}
                  ^^^

should satisfy

  string?

-- Spec failed --------------------

  {:foo ..., :bar ..., :baz true}
                            ^^^^

should satisfy

  string?

-------------------------
Detected 3 errors\n")
         (expound/expound-str :keys-spec/map-spec-1 {:foo 1.2
                                                     :bar 123
                                                     :baz true}
                              {:print-specs? false})))
  (is (= (pf
          "-- Spec failed --------------------

  {:foo 1.2, :bar ..., :qux ...}
        ^^^

should satisfy

  string?

-- Spec failed --------------------

  {:foo ..., :bar 123, :qux ...}
                  ^^^

should satisfy

  string?

-- Spec failed --------------------

  {:foo ..., :bar ..., :qux false}
                            ^^^^^

should satisfy

  string?

or

  int?

-------------------------
Detected 3 errors\n")
         (expound/expound-str :keys-spec/map-spec-2 {:foo 1.2
                                                     :bar 123
                                                     :qux false}
                              {:print-specs? false})))

  (is (=
       "-- Spec failed --------------------

  {:foo 1.2, :child-2 ...}
        ^^^

should satisfy

  string?

-- Spec failed --------------------

  {:foo ..., :child-2 {:bar 123, :child-1 ...}}
                            ^^^

should satisfy

  string?

-- Spec failed --------------------

  {:foo ...,
   :child-2
   {:bar ..., :child-1 {:baz true, :qux ...}}}
                             ^^^^

should satisfy

  string?

-- Spec failed --------------------

  {:foo ...,
   :child-2
   {:bar ..., :child-1 {:baz ..., :qux false}}}
                                       ^^^^^

should satisfy

  string?

or

  int?

-------------------------
Detected 4 errors\n"
       (expound/expound-str :keys-spec/map-spec-3 {:foo 1.2
                                                   :child-2 {:bar 123
                                                             :child-1 {:baz true
                                                                       :qux false}}}
                            {:print-specs? false}))))




;; https://github.com/borkdude/spartan.spec/issues/14
;; (s/def :multi-spec/value string?)
;; (s/def :multi-spec/children vector?)
;; (defmulti el-type :multi-spec/el-type)
;; (defmethod el-type :text [_x]
;;   (s/keys :req [:multi-spec/value]))
;; (defmethod el-type :group [_x]
;;   (s/keys :req [:multi-spec/children]))
;; (s/def :multi-spec/el (s/multi-spec el-type :multi-spec/el-type))

;; (defmulti multi-spec-bar-spec :type)
;; (defmethod multi-spec-bar-spec ::b [_] (s/keys :req [::b]))

#_(deftest multi-spec
  (testing "missing dispatch key"
    (is (=
         (pf "-- Missing spec -------------------

Cannot find spec for

  {}

with

 Spec multimethod:      `expound.alpha-test/el-type`
 Dispatch value:        `nil`

-- Relevant specs -------

:multi-spec/el:
  (pf.spec.alpha/multi-spec
   expound.alpha-test/el-type
   :multi-spec/el-type)

-------------------------
Detected 1 error\n")
         (expound/expound-str :multi-spec/el {}))))
  (testing "invalid dispatch value"
    (is (=
         (pf "-- Missing spec -------------------

Cannot find spec for

  {:multi-spec/el-type :image}

with

 Spec multimethod:      `expound.alpha-test/el-type`
 Dispatch value:        `:image`

-- Relevant specs -------

:multi-spec/el:
  (pf.spec.alpha/multi-spec
   expound.alpha-test/el-type
   :multi-spec/el-type)

-------------------------
Detected 1 error\n")
         (expound/expound-str :multi-spec/el {:multi-spec/el-type :image}))))

  (testing "valid dispatch value, but other error"
    (is (=
         (pf "-- Spec failed --------------------

  {:multi-spec/el-type :text}

should contain key: :multi-spec/value

| key               | spec    |
|===================+=========|
| :multi-spec/value | string? |

-- Relevant specs -------

:multi-spec/el:
  (pf.spec.alpha/multi-spec
   expound.alpha-test/el-type
   :multi-spec/el-type)

-------------------------
Detected 1 error\n")
         (expound/expound-str :multi-spec/el {:multi-spec/el-type :text}))))

  ;; https://github.com/bhb/expound/issues/122
  (testing "when re-tag is a function"
    (s/def :multi-spec/b string?)
    (s/def :multi-spec/bar (s/multi-spec multi-spec-bar-spec (fn [val tag] (assoc val :type tag))))
    (is (= "-- Missing spec -------------------

Cannot find spec for

  {}

with

 Spec multimethod:      `expound.alpha-test/multi-spec-bar-spec`
 Dispatch value:        `nil`

-------------------------
Detected 1 error
"
           (expound/expound-str :multi-spec/bar {} {:print-specs? false})))))

(s/def :recursive-spec/tag #{:text :group})
(s/def :recursive-spec/on-tap (s/coll-of map? :kind vector?))
(s/def :recursive-spec/props (s/keys :opt-un [:recursive-spec/on-tap]))
(s/def :recursive-spec/el (s/keys :req-un [:recursive-spec/tag]
                                  :opt-un [:recursive-spec/props :recursive-spec/children]))
(s/def :recursive-spec/children (s/coll-of (s/nilable :recursive-spec/el) :kind vector?))

(s/def :recursive-spec/tag-2 (s/or :text (fn [n] (= n :text))
                                   :group (fn [n] (= n :group))))
(s/def :recursive-spec/on-tap-2 (s/coll-of map? :kind vector?))
(s/def :recursive-spec/props-2 (s/keys :opt-un [:recursive-spec/on-tap-2]))
(s/def :recursive-spec/el-2 (s/keys :req-un [:recursive-spec/tag-2]
                                    :opt-un [:recursive-spec/props-2
                                             :recursive-spec/children-2]))
(s/def :recursive-spec/children-2 (s/coll-of (s/nilable :recursive-spec/el-2) :kind vector?))

(deftest recursive-spec
  (testing "only shows problem with data at 'leaves' (not problems with all parents in tree)"
    (is (= (pf
            "-- Spec failed --------------------

  {:tag ..., :children [{:tag :group, :children [{:tag :group, :props {:on-tap {}}}]}]}
                        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

should satisfy

  nil?

or value

  {:tag ...,
   :children [{:tag ..., :children [{:tag :group, :props {:on-tap {}}}]}]}
                                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

should satisfy

  nil?

or value

  {:tag ...,
   :children
   [{:tag ...,
     :children
     [{:tag ..., :props {:on-tap {}}}]}]}
                                 ^^

should satisfy

  vector?

-------------------------
Detected 1 error\n")
           (expound/expound-str
            :recursive-spec/el
            {:tag :group
             :children [{:tag :group
                         :children [{:tag :group
                                     :props {:on-tap {}}}]}]}
            {:print-specs? false}))))

  (testing "test that our new recursive spec grouping function works with alternative paths"
    (is (= (pf
             "-- Spec failed --------------------

  {:tag-2 ..., :children-2 [{:tag-2 :group, :children-2 [{:tag-2 :group, :props-2 {:on-tap-2 {}}}]}]}
                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

should satisfy

  nil?

or value

  {:tag-2 ...,
   :children-2 [{:tag-2 ..., :children-2 [{:tag-2 :group, :props-2 {:on-tap-2 {}}}]}]}
                                          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

should satisfy

  nil?

or value

  {:tag-2 ...,
   :children-2
   [{:tag-2 ...,
     :children-2
     [{:tag-2 ..., :props-2 {:on-tap-2 {}}}]}]}
                                       ^^

should satisfy

  vector?

-------------------------
Detected 1 error
")
           (expound/expound-str
            :recursive-spec/el-2
            {:tag-2 :group
             :children-2 [{:tag-2 :group
                           :children-2 [{:tag-2 :group
                                         :props-2 {:on-tap-2 {}}}]}]}
            {:print-specs? false})))))

(s/def :cat-wrapped-in-or-spec/kv (s/and
                                   sequential?
                                   (s/cat :k keyword? :v any?)))
(s/def :cat-wrapped-in-or-spec/type #{:text})
(s/def :cat-wrapped-in-or-spec/kv-or-string (s/or
                                             :map (s/keys :req [:cat-wrapped-in-or-spec/type])
                                             :kv :cat-wrapped-in-or-spec/kv))

(deftest cat-wrapped-in-or-spec
  (is (= (pf "-- Spec failed --------------------

  {\"foo\" \"hi\"}

should contain key: :cat-wrapped-in-or-spec/type

| key                          | spec     |
|==============================+==========|
| :cat-wrapped-in-or-spec/type | #{:text} |

or

should satisfy

  sequential?

-- Relevant specs -------

:cat-wrapped-in-or-spec/kv:
  (pf.spec.alpha/and
   pf.core/sequential?
   (pf.spec.alpha/cat :k pf.core/keyword? :v pf.core/any?))
:cat-wrapped-in-or-spec/kv-or-string:
  (pf.spec.alpha/or
   :map
   (pf.spec.alpha/keys :req [:cat-wrapped-in-or-spec/type])
   :kv
   :cat-wrapped-in-or-spec/kv)

-------------------------
Detected 1 error\n")
         (expound/expound-str :cat-wrapped-in-or-spec/kv-or-string {"foo" "hi"}))))

(s/def :map-of-spec/name string?)
(s/def :map-of-spec/age pos-int?)
(s/def :map-of-spec/name->age (s/map-of :map-of-spec/name :map-of-spec/age))
(deftest map-of-spec
  (is (= (pf "-- Spec failed --------------------

  {\"Sally\" \"30\"}
           ^^^^

should satisfy

  pos-int?

-- Relevant specs -------

:map-of-spec/age:
  pf.core/pos-int?
:map-of-spec/name->age:
  (pf.spec.alpha/map-of :map-of-spec/name :map-of-spec/age)

-------------------------
Detected 1 error\n")
         (expound/expound-str :map-of-spec/name->age {"Sally" "30"})))
  (is (= (pf "-- Spec failed --------------------

  {:sally ...}
   ^^^^^^

should satisfy

  string?

-- Relevant specs -------

:map-of-spec/name:
  pf.core/string?
:map-of-spec/name->age:
  (pf.spec.alpha/map-of :map-of-spec/name :map-of-spec/age)

-------------------------
Detected 1 error\n")
         (expound/expound-str :map-of-spec/name->age {:sally 30}))))

(s/def :expound.ds/spec-key (s/or :kw keyword?
                                  :req (s/tuple
                                        #{:expound.ds/req-key}
                                        (s/map-of
                                         #{:k}
                                         keyword?
                                         :count 1))
                                  :opt (s/tuple
                                        #{:expound.ds/opt-key}
                                        (s/map-of
                                         #{:k}
                                         keyword?
                                         :count 1))))

(s/def :expound.ds/maybe-spec
  (s/tuple
   #{:expound.ds/maybe-spec}
   :expound.ds/spec))

(s/def :expound.ds/simple-specs
  #{string?
    vector?
    int?
    boolean?
    keyword?
    map?
    symbol?
    pos-int?
    neg-int?
    nat-int?})

(s/def :expound.ds/vector-spec (s/coll-of
                                :expound.ds/spec
                                :count 1
                                :kind vector?))

(s/def :expound.ds/set-spec (s/coll-of
                             :expound.ds/spec
                             :count 1
                             :kind set?))

(s/def :expound.ds/map-spec
  (s/map-of :expound.ds/spec-key
            :expound.ds/spec))

(s/def :expound.ds/spec
  (s/or
   :map :expound.ds/map-spec
   :vector :expound.ds/vector-spec
   :set :expound.ds/set-spec
   :simple :expound.ds/simple-specs
   :maybe :expound.ds/maybe-spec))

;; FIXME - keys
;; FIXME - cat + alt, + ? *
;; FIXME - nilable
;; FIXME - test coll-of that is a set . can i should a bad element of a set?

(s/def :test-assert/name string?)
(deftest test-assert
  (testing "assertion passes"
    (is (= "hello"
           (s/assert :test-assert/name "hello"))))
  (testing "assertion fails"
    #?(:cljs
       (try
         (binding [s/*explain-out* expound/printer]
           (s/assert :test-assert/name :hello))
         (catch :default e
           (is (= "Spec assertion failed\n-- Spec failed --------------------

  :hello

should satisfy

  string?

-- Relevant specs -------

:test-assert/name:
  cljs.core/string?

-------------------------
Detected 1 error\n"
                  (.-message e)))))
       :clj
       (try
         (binding [s/*explain-out* expound/printer]
           (s/assert :test-assert/name :hello))
         (catch Exception e
           (is (= "Spec assertion failed
-- Spec failed --------------------

  :hello

should satisfy

  string?

-- Relevant specs -------

:test-assert/name:
  clojure.core/string?

-------------------------
Detected 1 error\n"
                  ;; FIXME - move assertion out of catch, similar to instrument tests
                  (:cause (Throwable->map e)))))))))

(s/def :test-explain-str/name string?)
(deftest test-explain-str
  (is (= (pf "-- Spec failed --------------------

  :hello

should satisfy

  string?

-- Relevant specs -------

:test-explain-str/name:
  pf.core/string?

-------------------------
Detected 1 error\n")
         (binding [s/*explain-out* expound/printer]
           (s/explain-str :test-explain-str/name :hello)))))

(defn test-instrument-adder [& args]
  (let [[x y] args]
    (+ x y)))

(defn no-linum [s]
  (string/replace s #"(.cljc?):\d+" "$1:LINUM"))

(s/def :custom-printer/strings (s/coll-of string?))
(deftest custom-printer
  (testing "custom value printer"
    (is (= (pf "-- Spec failed --------------------

  <HIDDEN>

should satisfy

  string?

-- Relevant specs -------

:custom-printer/strings:
  (pf.spec.alpha/coll-of pf.core/string?)

-------------------------
Detected 1 error
")
           (binding [s/*explain-out* (expound/custom-printer {:value-str-fn (fn [_spec-name _form _path _val] "  <HIDDEN>")})]
             (s/explain-str :custom-printer/strings ["a" "b" :c]))))))

(s/def :alt-spec/int-alt-str (s/alt :int int? :string string?))

(s/def :alt-spec/num-types (s/alt :int int? :float float?))
(s/def :alt-spec/str-types (s/alt :int (fn [n] (= n "int"))
                                  :float (fn [n] (= n "float"))))
(s/def :alt-spec/num-or-str (s/alt :num :alt-spec/num-types
                                   :str :alt-spec/str-types))

(s/def :alt-spec/i int?)
(s/def :alt-spec/s string?)
(s/def :alt-spec/alt-or-map (s/or :i :alt-spec/i
                                  :s :alt-spec/s
                                  :k (s/keys :req-un [:alt-spec/i :alt-spec/s])))

(defmulti alt-spec-mspec :tag)
(s/def :alt-spec/mspec (s/multi-spec alt-spec-mspec :tag))
(defmethod alt-spec-mspec :x [_] (s/keys :req-un [:alt-spec/one-many-int]))

(deftest alt-spec
  (testing "alternatives at different paths in spec"
    (is (=
         "-- Spec failed --------------------

  [\"foo\"]

should satisfy

  int?

or value

  [\"foo\"]
   ^^^^^

should satisfy

  int?

-------------------------
Detected 1 error\n"
         (expound/expound-str
          (s/or
           :i int?
           :seq (s/cat :x1 int? :x2 int?))
          ["foo"]
          {:print-specs? false})))
    (s/def :alt-spec/one-many-int (s/cat :bs (s/alt :one int?
                                                    :many (s/spec (s/+ int?)))))

    (is (= (pf "-- Spec failed --------------------

  [[\"1\"]]
   ^^^^^

should satisfy

  int?

or value

  [[\"1\"]]
    ^^^

should satisfy

  int?

-- Relevant specs -------

:alt-spec/one-many-int:
  (pf.spec.alpha/cat
   :bs
   (pf.spec.alpha/alt
    :one
    pf.core/int?
    :many
    (pf.spec.alpha/spec (pf.spec.alpha/+ pf.core/int?))))

-------------------------
Detected 1 error\n")
           (binding [s/*explain-out* (expound/custom-printer {})]
             (s/explain-str
              :alt-spec/one-many-int
              [["1"]]))))
    (s/def :alt-spec/one-many-int-or-str (s/cat :bs (s/alt :one :alt-spec/int-alt-str
                                                           :many (s/spec (s/+ :alt-spec/int-alt-str)))))
    ;; FIXME: https://github.com/borkdude/spartan.spec/issues/16
    #_(is (= "-- Spec failed --------------------

  [[:one]]
   ^^^^^^

should satisfy

  int?

or

  string?

or value

  [[:one]]
    ^^^^

should satisfy

  int?

or

  string?

-------------------------
Detected 1 error\n"
           (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
             (s/explain-str
              :alt-spec/one-many-int-or-str
              [[:one]]))))
    (s/def :alt-spec/int-or-str (s/or :i int?
                                      :s string?))
    (s/def :alt-spec/one-many-int-or-str (s/cat :bs (s/alt :one :alt-spec/int-or-str
                                                           :many (s/spec (s/+ :alt-spec/int-or-str)))))
    (is (= "-- Spec failed --------------------

  [[:one]]
   ^^^^^^

should satisfy

  int?

or

  string?

or value

  [[:one]]
    ^^^^

should satisfy

  int?

or

  string?

-------------------------
Detected 1 error\n"
           (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
             (s/explain-str
              :alt-spec/one-many-int-or-str
              [[:one]])))))
  (is (= (pf "-- Spec failed --------------------

  [:hi]
   ^^^

should satisfy

  int?

or

  string?

-- Relevant specs -------

:alt-spec/int-alt-str:
  %s

-------------------------
Detected 1 error\n"
             #?(:clj "(clojure.spec.alpha/alt
   :int
   clojure.core/int?
   :string
   clojure.core/string?)"
                :cljs "(cljs.spec.alpha/alt :int cljs.core/int? :string cljs.core/string?)"))
         (expound/expound-str :alt-spec/int-alt-str [:hi])))

  (is (= "-- Spec failed --------------------

  {:i \"\", :s 1}

should satisfy

  int?

or

  string?

-- Spec failed --------------------

  {:i \"\", :s ...}
      ^^

should satisfy

  int?

-- Spec failed --------------------

  {:i ..., :s 1}
              ^

should satisfy

  string?

-------------------------
Detected 3 errors
"

         (expound/expound-str
          :alt-spec/alt-or-map
          {:i "" :s 1}
          {:print-specs? false})))

  ;; FIXME: https://github.com/borkdude/spartan.spec/issues/16
  #_(is (= "-- Spec failed --------------------

  [true]
   ^^^^

should satisfy

  int?

or

  float?

or

  (fn [n] (= n \"int\"))

or

  (fn [n] (= n \"float\"))

-------------------------
Detected 1 error\n" (expound/expound-str :alt-spec/num-or-str [true] {:print-specs? false})))
  ;; If two s/alt specs have the same tags, we shouldn't confuse them.
  
  ;; FIXME: https://github.com/borkdude/spartan.spec/issues/16
  #_(is (= "-- Spec failed --------------------

  {:num-types [true], :str-types ...}
               ^^^^

should satisfy

  int?

or

  float?

-- Spec failed --------------------

  {:num-types ..., :str-types [false]}
                               ^^^^^

should satisfy

  (fn [n] (= n \"int\"))

or

  (fn [n] (= n \"float\"))

-------------------------
Detected 2 errors\n"
         (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
           (s/explain-str (s/keys :req-un [:alt-spec/num-types :alt-spec/str-types])
                          {:num-types [true] :str-types [false]}))))

  (is (=
       "-- Spec failed --------------------

  [\"\"]

should satisfy

  nil?

or value

  [\"\"]
   ^^

should satisfy

  int?

or

  float?

-------------------------
Detected 1 error
"
       (expound/expound-str
        (s/nilable (s/cat :n (s/alt :int int? :float float?)))
        [""]
        {:print-specs? false})))

  ;; mspecs not supported
  #_(is (=
       ;; This output is not what we want: ideally, the two alternates
       ;; should be grouped into a single problem.
       ;; I'm adding it as a spec to avoid regressions and to keep it as
       ;; an example of something I could improve.
       ;; The reason we can't do better is that we can't reliably look
       ;; at the form of a multi-spec. It would be nice if spec inserted
       ;; the actual spec form that was returned by the multi-spec, but
       ;; as it stands today, we'd have to figure out how to call the multi-
       ;; method with the actual value. That would be complicated and
       ;; potentially have unknown side effects from running arbitrary code.

       "-- Spec failed --------------------

  {:mspec {:tag ..., :one-many-int [[\"1\"]]}}
                                    ^^^^^

should satisfy

  int?

-- Spec failed --------------------

  {:mspec {:tag ..., :one-many-int [[\"1\"]]}}
                                     ^^^

should satisfy

  int?

-------------------------
Detected 2 errors\n"

       (expound/expound-str
        (s/keys
         :req-un [:alt-spec/mspec])
        {:mspec
         {:tag :x
          :one-many-int [["1"]]}}

        {:print-specs? false}))))

(defn mutate-coll [x]
  (cond
    (map? x)
    (into [] x)

    (vector? x)
    (into #{} x)

    (set? x)
    (reverse (into '() x))

    (list? x)
    (into {} (map vec (partition 2 x)))

    :else
    x))

(defn mutate-type [x]
  (cond
    (number? x)
    (str x)

    (string? x)
    (keyword x)

    (keyword? x)
    (str x)

    (boolean? x)
    (str x)

    (symbol? x)
    (str x)

    (char? x)
    #?(:cljs (.charCodeAt x)
       :clj (int x))

    (uuid? x)
    (str x)

    :else
    x))

(defn mutate [form path]
  (let [[head & rst] path]
    (cond
      (empty? path)
      (if (coll? form)
        (mutate-coll form)
        (mutate-type form))

      (map? form)
      (if (empty? form)
        (mutate-coll form)
        (let [k (nth (keys form) (mod head (count (keys form))))]
          (assoc form k
                 (mutate (get form k) rst))))

      (vector? form)
      (if (empty? form)
        (mutate-coll form)
        (let [idx (mod head (count form))]
          (assoc form idx
                 (mutate (nth form idx) rst))))

      (not (coll? form))
      (mutate-type form)

      :else
      (mutate-coll form))))

(deftest test-assert2
  (is (thrown-with-msg?
       #?(:cljs :default :clj Exception)
       #"\"Key must be integer\"\n\nshould be one of: \"Extra input\", \"Insufficient input\", \"no method"
       (binding [s/*explain-out* expound/printer]
         (try
           (s/check-asserts true)
           (s/assert (s/nilable #{"Insufficient input" "Extra input" "no method"}) "Key must be integer")
           (finally (s/check-asserts false)))))))

(defn inline-specs [keyword]
  (walk/postwalk
   (fn [x]
     (if (contains? (s/registry) x)
       (s/form x)
       x))
   (s/form keyword)))

;; Using conformers for transformation should not crash by default, or at least give useful error message.
(defn numberify [s]
  (cond
    (number? s) s
    (re-matches #"^\d+$" s) (Integer. s)
    :else ::s/invalid))

(s/def :conformers-test/number (s/conformer numberify))

(defn conform-by
  [tl-key payload-key]
  (s/conformer (fn [m]
                 (let [id (get m tl-key)]
                   (if (and id (map? (get m payload-key)))
                     (assoc-in m [payload-key tl-key] id)
                     ::s/invalid)))))

(s/def :conformers-test.query/id qualified-keyword?)

(defmulti query-params :conformers-test.query/id)
(s/def :conformers-test.query/params (s/multi-spec query-params :conformers-test.query/id))
(s/def :user/id string?)

(defmethod query-params :conformers-test/lookup-user [_]
  (s/keys :req [:user/id]))

(s/def :conformers-test/query
  (s/and
   (conform-by :conformers-test.query/id :conformers-test.query/params)
   (s/keys :req [:conformers-test.query/id
                 :conformers-test.query/params])))

(s/def :conformers-test/string-AB-seq (s/cat :a #{\A} :b #{\B}))

(s/def :conformers-test/string-AB
  (s/and
   ;; conform as sequence (seq function)
   (s/conformer seq)
   ;; re-use previous sequence spec
   :conformers-test/string-AB-seq))

(defn parse-csv [s]
  (map string/upper-case (string/split s #",")))

;; FIXME: conformers are pretty advanced, I'm OK if they don't work in spartan.spec yet
#_(deftest conformers-test
  ;; Example from http://cjohansen.no/a-unified-specification/
  (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})
            *print-namespace-maps* false]
    (testing "conform string to int"
      (is (string?
           (s/explain-str :conformers-test/number "123a"))))
    ;; Example from https://github.com/bhb/expound/issues/15#issuecomment-326838879
    (testing "conform maps"
      (is (string? (s/explain-str :conformers-test/query {})))
      (is (= "-- Spec failed --------------------

Part of the value

  {:conformers-test.query/id :conformers-test/lookup-user, :conformers-test.query/params {}}

when conformed as

  {:conformers-test.query/id :conformers-test/lookup-user}

should contain key: :user/id

| key      | spec    |
|==========+=========|
| :user/id | string? |

-------------------------
Detected 1 error\n"
             (s/explain-str :conformers-test/query {:conformers-test.query/id :conformers-test/lookup-user
                                                    :conformers-test.query/params {}}))))
    ;; Minified example based on https://github.com/bhb/expound/issues/15
    ;; This doesn't look ideal, but really, it's not a good idea to use spec
    ;; for string parsing, so I'm OK with it
    (testing "conform string to seq"
      (is (=
           ;; clojurescript doesn't have a character type
           #?(:cljs "-- Spec failed --------------------\n\n  \"A\"C\"\"\n    ^^^\n\nshould be: \"B\"\n\n-------------------------\nDetected 1 error\n"
              :clj "-- Spec failed --------------------

  \"A\\C\"
    ^^

should be: \\B

-------------------------
Detected 1 error
")
           (s/explain-str :conformers-test/string-AB "AC"))))
    (testing "s/cat"
      (s/def :conformers-test/sorted-pair (s/and (s/cat :x int? :y int?) #(< (-> % :x) (-> % :y))))
      (is (= (pf "-- Spec failed --------------------

  [1 0]

when conformed as

  {:x 1, :y 0}

should satisfy

  %s

-------------------------
Detected 1 error
"
                 #?(:cljs "(fn [%] (< (-> % :x) (-> % :y)))"
                    :clj "(fn
   [%]
   (< (-> % :x) (-> % :y)))"))
             (expound/expound-str :conformers-test/sorted-pair [1 0] {:print-specs? false})))
      (is (= (pf "-- Spec failed --------------------

  [... [1 0]]
       ^^^^^

when conformed as

  {:x 1, :y 0}

should satisfy

  %s

-------------------------
Detected 1 error\n"
                 #?(:cljs "(fn [%] (< (-> % :x) (-> % :y)))"
                    :clj "(fn
   [%]
   (< (-> % :x) (-> % :y)))"))
             (expound/expound-str (s/coll-of :conformers-test/sorted-pair) [[0 1] [1 0]] {:print-specs? false})))
      (is (= (pf "-- Spec failed --------------------

  {:a [1 0]}
      ^^^^^

when conformed as

  {:x 1, :y 0}

should satisfy

  %s

-------------------------
Detected 1 error\n"
                 #?(:cljs "(fn [%] (< (-> % :x) (-> % :y)))"
                    :clj "(fn
   [%]
   (< (-> % :x) (-> % :y)))"))
             (expound/expound-str (s/map-of keyword? :conformers-test/sorted-pair) {:a [1 0]} {:print-specs? false})))
      (is (= (pf "-- Spec failed --------------------

  [... \"a\"]
       ^^^

should satisfy

  int?

-------------------------
Detected 1 error\n")
             (expound/expound-str :conformers-test/sorted-pair [1 "a"] {:print-specs? false}))))
    (testing "conformers that modify path of values"
      (s/def :conformers-test/vals (s/coll-of (s/and string?
                                                     #(re-matches #"[A-G]+" %))))
      (s/def :conformers-test/csv (s/and string?
                                         (s/conformer parse-csv)
                                         :conformers-test/vals))
      (is (= "-- Spec failed --------------------

Part of the value

  \"abc,def,ghi\"

when conformed as

  \"GHI\"

should satisfy

  (fn [%] (re-matches #\"[A-G]+\" %))

-------------------------
Detected 1 error\n"
             (expound/expound-str :conformers-test/csv "abc,def,ghi" {:print-specs? false}))))

    ;; this is NOT recommended!
    ;; so I'm not inclined to make this much nicer than
    ;; the default spec output
    (s/def :conformers-test/coerced-kw (s/and (s/conformer #(if (string? %)
                                                              (keyword %)
                                                              ::s/invalid))
                                              keyword?))
    (testing "coercion"
      (is (= (pf "-- Spec failed --------------------

  nil

should satisfy

  (pf.spec.alpha/conformer
   (fn
    [%%]
    (if
     (string? %%)
     (keyword %%)
     :pf.spec.alpha/invalid)))

-------------------------
Detected 1 error
")
             (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
               (s/explain-str :conformers-test/coerced-kw nil))))

      (is (= (pf "-- Spec failed --------------------

  [... ... ... 0]
               ^

should satisfy

  (pf.spec.alpha/conformer
   (fn
    [%%]
    (if
     (string? %%)
     (keyword %%)
     :pf.spec.alpha/invalid)))

-------------------------
Detected 1 error
")
             (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
               (s/explain-str (s/coll-of :conformers-test/coerced-kw) ["a" "b" "c" 0])))))
    ;; Also not recommended
    (s/def :conformers-test/str-kw? (s/and (s/conformer #(if (string? %)
                                                           (keyword %)
                                                           ::s/invalid)
                                                        name) keyword?))
    (testing "coercion with unformer"
      (is (= (pf "-- Spec failed --------------------

  nil

should satisfy

  (pf.spec.alpha/conformer
   (fn
    [%%]
    (if
     (string? %%)
     (keyword %%)
     :pf.spec.alpha/invalid)))

-------------------------
Detected 1 error
")
             (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
               (s/explain-str :conformers-test/coerced-kw nil))))

      (is (= (pf "-- Spec failed --------------------

  [... ... ... 0]
               ^

should satisfy

  (pf.spec.alpha/conformer
   (fn
    [%%]
    (if
     (string? %%)
     (keyword %%)
     :pf.spec.alpha/invalid)))

-------------------------
Detected 1 error
")
             (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
               (s/explain-str (s/coll-of :conformers-test/coerced-kw) ["a" "b" "c" 0])))))

    (s/def :conformers-test/name string?)
    (s/def :conformers-test/age pos-int?)
    (s/def :conformers-test/person (s/keys* :req-un [:conformers-test/name
                                                     :conformers-test/age]))
    ;; FIXME: Implementation could be simpler once
    ;; https://dev.clojure.org/jira/browse/CLJ-2406 is fixed
    (testing "spec defined with keys*"
      (is (= "-- Spec failed --------------------

  [... ... ... :Stan]
               ^^^^^

should satisfy

  string?

-------------------------
Detected 1 error
"
             (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
               (s/explain-str :conformers-test/person [:age 30 :name :Stan])))))

    (testing "spec defined with keys* and copies of bad value elsewhere in the data"
      (is (= "-- Spec failed --------------------

Part of the value

  [:Stan [:age 30 :name :Stan]]

when conformed as

  :Stan

should satisfy

  string?

-------------------------
Detected 1 error\n"
             (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
               (s/explain-str (s/tuple
                               keyword?
                               :conformers-test/person) [:Stan [:age 30 :name :Stan]])))))

    (testing "ambiguous value"
      (is (= (pf "-- Spec failed --------------------

  {[0 1] ..., [1 0] ...}
              ^^^^^

when conformed as

  {:x 1, :y 0}

should satisfy

  %s

-------------------------
Detected 1 error
"
                 #?(:cljs "(fn [%] (< (-> % :x) (-> % :y)))"
                    :clj "(fn
   [%]
   (< (-> % :x) (-> % :y)))"))
             (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
               (s/explain-str (s/map-of :conformers-test/sorted-pair any?) {[0 1] [1 0]
                                                                            [1 0] [1 0]})))))))

(s/def :duplicate-preds/str-or-str (s/or
                                    ;; Use anonymous functions to assure
                                    ;; non-equality
                                    :str1 #(string? %)
                                    :str2 #(string? %)))
(deftest duplicate-preds
  (testing "duplicate preds only appear once"
    (is (=
         "-- Spec failed --------------------

  1

should satisfy

  (fn [%] (string? %))

-- Relevant specs -------

:duplicate-preds/str-or-str:
  (clojure.spec.alpha/or
   :str1
   (fn [%] (clojure.core/string? %))
   :str2
   (fn [%] (clojure.core/string? %)))

-------------------------
Detected 1 error\n"
           (expound/expound-str :duplicate-preds/str-or-str 1)))))


(s/def :fspec-test/div (s/fspec
                        :args (s/cat :x int? :y pos-int?)))

(defn my-div [x y]
  (assert (not (zero? (/ x y)))))

(defn  until-unsuccessful [f]
  (let [nil-or-failure #(if (= "Success!
" %)
                          nil
                          %)]
    (or (nil-or-failure (f))
        (nil-or-failure (f))
        (nil-or-failure (f))
        (nil-or-failure (f))
        (nil-or-failure (f)))))

;; FIXME - support fspec
#_(deftest fspec-exception-test
  (testing "args that throw exception"
    (is (= (pf "-- Exception ----------------------

  expound.alpha-test/my-div

threw exception

  \"Assert failed: (not (zero? (/ x y)))\"

with args:

  0, 1

-- Relevant specs -------

:fspec-test/div:
  (pf.spec.alpha/fspec
   :args
   (pf.spec.alpha/cat :x pf.core/int? :y pf.core/pos-int?)
   :ret
   pf.core/any?
   :fn
   nil)

-------------------------
Detected 1 error\n")

           ;;
           (until-unsuccessful #(expound/expound-str :fspec-test/div my-div))))

    (is (= (pf "-- Exception ----------------------

  [expound.alpha-test/my-div]
   ^^^^^^^^^^^^^^^^^^^^^^^^^

threw exception

  \"Assert failed: (not (zero? (/ x y)))\"

with args:

  0, 1

-- Relevant specs -------

:fspec-test/div:
  (pf.spec.alpha/fspec
   :args
   (pf.spec.alpha/cat :x pf.core/int? :y pf.core/pos-int?)
   :ret
   pf.core/any?
   :fn
   nil)

-------------------------
Detected 1 error\n")
           (until-unsuccessful #(expound/expound-str (s/coll-of :fspec-test/div) [my-div]))))))


(s/def :fspec-ret-test/my-int pos-int?)
(s/def :fspec-ret-test/plus (s/fspec
                             :args (s/cat :x int? :y pos-int?)
                             :ret :fspec-ret-test/my-int))

(defn my-plus [x y]
  (+ x y))

;; FIXME: support fspec
#_(deftest fspec-ret-test
  (testing "invalid ret"
    (is (= (pf "-- Function spec failed -----------

  expound.alpha-test/my-plus

returned an invalid value

  0

should satisfy

  pos-int?

-------------------------
Detected 1 error\n")
           (until-unsuccessful #(expound/expound-str :fspec-ret-test/plus my-plus {:print-specs? false}))))

    (is (= (pf "-- Function spec failed -----------

  [expound.alpha-test/my-plus]
   ^^^^^^^^^^^^^^^^^^^^^^^^^^

returned an invalid value

  0

should satisfy

  pos-int?

-------------------------
Detected 1 error\n")
           (until-unsuccessful #(expound/expound-str (s/coll-of :fspec-ret-test/plus) [my-plus] {:print-specs? false}))))
    (s/def :fspec-ret-test/return-map (s/fspec
                                       :args (s/cat)
                                       :ret (s/keys :req-un [:fspec-ret-test/my-int])))
    (is (= (pf "-- Function spec failed -----------

  <anonymous function>

returned an invalid value

  {}

should contain key: :my-int

| key     | spec                                              |
|=========+===================================================|
| :my-int | <can't find spec for unqualified spec identifier> |

-------------------------
Detected 1 error
")
           (until-unsuccessful #(expound/expound-str :fspec-ret-test/return-map
                                                     (fn [] {})
                                                     {:print-specs? false}))))))

(s/def :fspec-fn-test/minus (s/fspec
                             :args (s/cat :x int? :y int?)
                             :fn (s/and
                                  #(< (:ret %) (-> % :args :x))
                                  #(< (:ret %) (-> % :args :y)))))

(defn my-minus [x y]
  (- x y))

;; FIXME: support fspec
#_(deftest fspec-fn-test
  (testing "invalid ret"
    (is (= (pf "-- Function spec failed -----------

  expound.alpha-test/my-minus

failed spec. Function arguments and return value

  {:args {:x 0, :y 0}, :ret 0}

should satisfy

  %s

-------------------------
Detected 1 error\n"

               #?(:clj
                  "(fn
   [%]
   (< (:ret %) (-> % :args :x)))"
                  :cljs "(fn [%] (< (:ret %) (-> % :args :x)))"))
           (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
             (until-unsuccessful #(s/explain-str :fspec-fn-test/minus my-minus)))))

    (is (= (pf "-- Function spec failed -----------

  [expound.alpha-test/my-minus]
   ^^^^^^^^^^^^^^^^^^^^^^^^^^^

failed spec. Function arguments and return value

  {:args {:x 0, :y 0}, :ret 0}

should satisfy

  %s

-------------------------
Detected 1 error\n"
               #?(:clj
                  "(fn
   [%]
   (< (:ret %) (-> % :args :x)))"
                  :cljs "(fn [%] (< (:ret %) (-> % :args :x)))"))
           (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
             (until-unsuccessful #(s/explain-str (s/coll-of :fspec-fn-test/minus) [my-minus])))))))

;; FIXME: support fspec
#_(deftest ifn-fspec-test
  (testing "keyword ifn / ret failure"
    (is (= "-- Function spec failed -----------

  [:foo]
   ^^^^

returned an invalid value

  nil

should satisfy

  int?

-------------------------
Detected 1 error\n"
           (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
             (until-unsuccessful #(s/explain-str (s/coll-of (s/fspec :args (s/cat :x int?) :ret int?))
                                                 [:foo])))))
    (testing "set ifn / ret failure"
      (is (= "-- Function spec failed -----------

  [#{}]
   ^^^

returned an invalid value

  nil

should satisfy

  int?

-------------------------
Detected 1 error\n"
             (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
               (until-unsuccessful #(s/explain-str (s/coll-of (s/fspec :args (s/cat :x int?) :ret int?))
                                                   [#{}])))))))
  #?(:clj
     (testing "vector ifn / exception failure"
       (is (= "-- Exception ----------------------

  [[]]
   ^^

threw exception

  nil

with args:

  0

-------------------------
Detected 1 error\n"
              (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
                (until-unsuccessful #(s/explain-str (s/coll-of (s/fspec :args (s/cat :x int?) :ret int?))
                                                    [[]]))))))))

(defmulti pet :pet/type)
(defmethod pet :dog [_]
  (s/keys))
(defmethod pet :cat [_]
  (s/keys))

(defmulti animal :animal/type)
(defmethod animal :dog [_]
  (s/keys))
(defmethod animal :cat [_]
  (s/keys))

(s/def :multispec-in-compound-spec/pet1 (s/and
                                         map?
                                         (s/multi-spec pet :pet/type)))

(s/def :multispec-in-compound-spec/pet2 (s/or
                                         :map1 (s/multi-spec pet :pet/type)
                                         :map2 (s/multi-spec animal :animal/type)))

;; FIXME: support multipspec
#_(deftest multispec-in-compound-spec
  (testing "multispec combined with s/and"
    (is (= (pf "-- Missing spec -------------------

Cannot find spec for

  {:pet/type :fish}

with

 Spec multimethod:      `expound.alpha-test/pet`
 Dispatch value:        `:fish`

-- Relevant specs -------

:multispec-in-compound-spec/pet1:
  (pf.spec.alpha/and
   pf.core/map?
   (pf.spec.alpha/multi-spec expound.alpha-test/pet :pet/type))

-------------------------
Detected 1 error\n")
           (expound/expound-str :multispec-in-compound-spec/pet1 {:pet/type :fish}))))
  ;; FIXME - improve this, maybe something like:
  ;;;;;;;;;;;;;;;;;;;

  ;;   {:pet/type :fish}  

  ;; should be described by a spec multimethod, but

  ;;   expound.alpha-test/pet

  ;; is missing a method for value

  ;;   (:pet/type {:pet/type :fish}) ; => :fish

  ;; or 

  ;; should be described by a spec multimethod, but

  ;;   expound.alpha-test/pet

  ;; is missing a method for value

  ;;  (:animal/type {:pet/type :fish}) ; => nil
  (testing "multispec combined with s/or"
    (is (= (pf "-- Missing spec -------------------

Cannot find spec for

  {:pet/type :fish}

with

 Spec multimethod:      `expound.alpha-test/pet`
 Dispatch value:        `:fish`

or with

 Spec multimethod:      `expound.alpha-test/animal`
 Dispatch value:        `nil`

-- Relevant specs -------

:multispec-in-compound-spec/pet2:
  (pf.spec.alpha/or
   :map1
   (pf.spec.alpha/multi-spec expound.alpha-test/pet :pet/type)
   :map2
   (pf.spec.alpha/multi-spec expound.alpha-test/animal :animal/type))

-------------------------
Detected 1 error\n")
           (expound/expound-str :multispec-in-compound-spec/pet2 {:pet/type :fish})))))

(expound/def :predicate-messages/string string? "should be a string")
(expound/def :predicate-messages/vector vector? "should be a vector")

(deftest predicate-messages
  (binding [s/*explain-out* (expound/custom-printer {:print-specs? false})]
    (testing "predicate with error message"
      (is (= "-- Spec failed --------------------

  :hello

should be a string

-------------------------
Detected 1 error
"
             (s/explain-str :predicate-messages/string :hello))))
    (testing "predicate within a collection"
      (is (= "-- Spec failed --------------------

  [... :foo]
       ^^^^

should be a string

-------------------------
Detected 1 error
"
             (s/explain-str (s/coll-of :predicate-messages/string) ["" :foo]))))
    (testing "two predicates with error messages"
      (is (= "-- Spec failed --------------------

  1

should be a string

or

should be a vector

-------------------------
Detected 1 error
"
             (s/explain-str (s/or :s :predicate-messages/string
                                  :v :predicate-messages/vector) 1))))
    (testing "one predicate with error message, one without"
      (is (= "-- Spec failed --------------------

  foo

should satisfy

  pos-int?

or

  vector?

or

should be a string

-------------------------
Detected 1 error
"
             (s/explain-str (s/or :p pos-int?
                                  :s :predicate-messages/string
                                  :v vector?) 'foo))))
    (testing "compound predicates"
      (let [email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$"]
        (expound/def :predicate-messages/email (s/and string? #(re-matches email-regex %)) "should be a valid email address")
        (is (= "-- Spec failed --------------------

  \"sally@\"

should be a valid email address

-------------------------
Detected 1 error
"
               (s/explain-str
                :predicate-messages/email
                "sally@"))))
      (expound/def :predicate-messages/score (s/int-in 0 100) "should be between 0 and 100")
      (is (= "-- Spec failed --------------------

  101

should be between 0 and 100

-------------------------
Detected 1 error
"
             (s/explain-str
              :predicate-messages/score
              101))))))

(defn results-str-fn1 [x y]
  #?(:clj (+' x y)
     :cljs (+ x y)))

(defn results-str-fn2 [x y]
  (+ x y))

(defn results-str-fn3 [x y]
  (+ x y))

(defn results-str-fn4 [x]
  [x :not-int])

(defn results-str-fn5
  [_x _y]
  #?(:clj (throw (Exception. "Ooop!"))
     :cljs (throw (js/Error. "Oops!"))))

(defn results-str-fn6
  [f]
  (f 1))

(s/def :results-str-fn7/k string?)
(defn results-str-fn7
  [m]
  m)

(defn results-str-missing-args-spec [] 1)

(s/def :colorized-output/strings (s/coll-of string?))
(deftest colorized-output
  (is (= (pf "-- Spec failed --------------------

  [... :a ...]
       ^^

should satisfy

  string?

-- Relevant specs -------

:colorized-output/strings:
  (pf.spec.alpha/coll-of pf.core/string?)

-------------------------
Detected 1 error
")
         (expound/expound-str :colorized-output/strings ["" :a ""] {:theme :none})))
  (is (= (pf "<NONE><NONE><CYAN>-- Spec failed --------------------<NONE>

  [... <RED>:a<NONE> ...]
  <MAGENTA>     ^^<NONE>

should satisfy

  <GREEN>string?<NONE>

<CYAN>-- Relevant specs -------<NONE>

:colorized-output/strings:
  (pf.spec.alpha/coll-of pf.core/string?)

<CYAN>-------------------------<NONE>
<CYAN>Detected<NONE> <CYAN>1<NONE> <CYAN>error<NONE>
")
         (readable-ansi (expound/expound-str :colorized-output/strings ["" :a ""] {:theme :figwheel-theme})))))

(deftest clean-registry
  (testing "only base spec remains"
    (is (<= (count (filter
                    (fn [k] (= "expound-generated-spec" (namespace k)))
                    (keys (s/registry))))
            1)
        (str "Found leftover specs: " (vec (filter
                                            (fn [k] (= "expound-generated-spec" (namespace k)))
                                            (keys (s/registry))))))))

(defmethod expound/problem-group-str ::test-problem1 [_type _spec-name _val _path _problems _opts]
  "fake-problem-group-str")

(defmethod expound/problem-group-str ::test-problem2 [type spec-name val path problems opts]
  (str "fake-problem-group-str\n"
       (expound/expected-str type spec-name val path problems opts)))

(defmethod expound/expected-str ::test-problem2 [_type _spec-name _val _path _problems _opts]
  "fake-expected-str")

(deftest extensibility-test
  (testing "can overwrite entire message"
    (let [printer-str #'expound/printer-str
          ed (assoc-in (s/explain-data int? "")
                       [::s/problems 0 :expound.spec.problem/type]
                       ::test-problem1)]

      (is (= "fake-problem-group-str\n\n-------------------------\nDetected 1 error\n"
             (printer-str {:print-specs? false} ed)))))
  (testing "can overwrite 'expected' str"
    (let [printer-str #'expound/printer-str
          ed (assoc-in (s/explain-data int? "")
                       [::s/problems 0 :expound.spec.problem/type]
                       ::test-problem2)]

      (is (= "fake-problem-group-str\nfake-expected-str\n\n-------------------------\nDetected 1 error\n"
             (printer-str {:print-specs? false} ed)))))
  (testing "if type has no mm implemented, throw an error"
    (let [printer-str #'expound/printer-str
          ed (assoc-in (s/explain-data int? "")
                       [::s/problems 0 :expound.spec.problem/type]
                       ::test-problem3)]

      (is (thrown-with-msg?
           #?(:cljs :default :clj Exception)
           #"No method in multimethod"
           (printer-str {:print-specs? false} ed))))))

(deftest sorted-map-values
  (is (= "-- Spec failed --------------------

  {\"bar\" 1}

should satisfy

  number?

-------------------------
Detected 1 error\n"
         (expound/expound-str
          number?
          (sorted-map "bar" 1))))
  (is (= "-- Spec failed --------------------

  {:foo {\"bar\" 1}}

should satisfy

  number?

-------------------------
Detected 1 error\n"
         (expound/expound-str
          number?
          {:foo (sorted-map "bar"

                            1)}))))

(defn select-expound-info [spec value]
  (->> (s/explain-data spec value)
       (problems/annotate)
       (:expound/problems)
       (map #(select-keys % [:expound.spec.problem/type :expound/in]))
       (set)))

(deftest defmsg-test
  (s/def :defmsg-test/id1 string?)
  (expound/defmsg :defmsg-test/id1 "should be a string ID")
  (testing "messages for predicate specs"
    (is (= "-- Spec failed --------------------

  123

should be a string ID

-------------------------
Detected 1 error\n"
           (expound/expound-str
            :defmsg-test/id1
            123
            {:print-specs? false}))))

  (s/def :defmsg-test/id2 (s/and string?
                                 #(<= 4 (count %))))
  (expound/defmsg :defmsg-test/id2 "should be a string ID of length 4 or more")
  (testing "messages for 'and' specs"
    (is (= "-- Spec failed --------------------

  \"123\"

should be a string ID of length 4 or more

-------------------------
Detected 1 error\n"
           (expound/expound-str
            :defmsg-test/id2
            "123"
            {:print-specs? false}))))

  (s/def :defmsg-test/statuses #{:ok :failed})
  (expound/defmsg :defmsg-test/statuses "should be either :ok or :failed")
  (testing "messages for set specs"
    (is (= "-- Spec failed --------------------

  :oak

should be either :ok or :failed

-------------------------
Detected 1 error
"
           (expound/expound-str
            :defmsg-test/statuses
            :oak
            {:print-specs? false}))))
  (testing "messages for alt specs"
    (s/def ::x int?)
    (s/def ::y int?)
    (expound/defmsg ::x "must be an integer")
    (is (=
         "-- Spec failed --------------------

  [\"\" ...]
   ^^

must be an integer

-------------------------
Detected 1 error\n"
         (expound/expound-str (s/alt :one
                                     (s/cat :x ::x)
                                     :two
                                     (s/cat :x ::x
                                            :y ::y))

                              ["" ""]
                              {:print-specs? false}))))

  (testing "messages for alt specs (if user duplicates existing message)"
    (s/def ::x int?)
    (s/def ::y int?)
    (expound/defmsg ::x "should satisfy\n\n  int?")
    (is (=
         "-- Spec failed --------------------

  [\"\"]
   ^^

should satisfy

  int?

-------------------------
Detected 1 error\n"
         (expound/expound-str (s/alt :one
                                     ::x
                                     :two
                                     ::y)
                              [""]
                              {:print-specs? false}))))
  (testing "messages for alternatives and set specs"
    (is (= "-- Spec failed --------------------

  :oak

should be either :ok or :failed

or

should satisfy

  string?

-------------------------
Detected 1 error\n"
           (expound/expound-str
            (s/or
             :num
             :defmsg-test/statuses
             :s string?)
            :oak
            {:print-specs? false})))))

(deftest printer
  (binding [s/*explain-out* expound/printer]
    (is (string? (s/explain-str int? "a")))
    (is (= "Success!\n" (s/explain-str int? 1)))
    (is (= "Success!\n" (with-out-str (expound/printer (s/explain-data int? 1)))))))

(deftest undefined-key
  (is (= "-- Spec failed --------------------

  {}

should contain key: :undefined-key/does-not-exist

| key                           | spec                          |
|===============================+===============================|
| :undefined-key/does-not-exist | :undefined-key/does-not-exist |

-------------------------
Detected 1 error
"
         (expound/expound-str (s/keys :req [:undefined-key/does-not-exist])
                              {}
                              {:print-specs? false}))))


(clojure.test/run-tests)

