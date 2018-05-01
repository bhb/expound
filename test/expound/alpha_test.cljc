(ns expound.alpha-test
  (:require #?@(:clj
                ;; just to include the specs
                [[clojure.core.specs.alpha]
                 [ring.core.spec]
                 [onyx.spec]])
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [clojure.string :as string]
            [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.gfredericks.test.chuck.properties :as properties]
            [com.stuartsierra.dependency :as deps]
            [expound.alpha :as expound]
            [expound.printer :as printer]
            [expound.test-utils :as test-utils]
            [clojure.walk :as walk]
            [spec-tools.data-spec :as ds]
            [expound.ansi :as ansi]
            #?(:clj [orchestra.spec.test :as orch.st]
               :cljs [orchestra-cljs.spec.test :as orch.st])))

(def num-tests 3) ;; TODO - restore

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

;; Missing onyx specs
(s/def :trigger/materialize any?)
(s/def :flow/short-circuit any?)

(def any-printable-wo-nan (gen/such-that (complement test-utils/contains-nan?) gen/any-printable))

(defn pf
  "Fixes platform-specific namespaces and also formats using printf syntax"
  [s & args]
  (apply printer/format
         #?(:cljs (string/replace s "pf." "cljs.")
            :clj (string/replace s "pf." "clojure."))
         args))

(defn take-lines [n s]
  (string/join "\n" (take n (string/split-lines s))))

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

;; https://github.com/bhb/expound/issues/8
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

  ;; FIXME - we should fix nilable and or specs better so they are clearly grouped
  (testing "nilable version"
    (is (= (pf "-- Spec failed --------------------

  :baz

should be one of: :bar, :foo

-- Relevant specs -------

:set-based-spec/tag:
  #{:bar :foo}
:set-based-spec/nilable-tag:
  (pf.spec.alpha/nilable :set-based-spec/tag)

-- Spec failed --------------------

  :baz

should satisfy

  nil?

-- Relevant specs -------

:set-based-spec/nilable-tag:
  (pf.spec.alpha/nilable :set-based-spec/tag)

-------------------------
Detected 2 errors\n")
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

|          key |    spec |
|--------------+---------|
| :or-spec/int |    int? |
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

|          key |    spec |
|--------------+---------|
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

  %s

-- Relevant specs -------

:and-spec/name:
  (pf.spec.alpha/and
   pf.core/string?
   (pf.core/fn [%%] (pf.core/pos? (pf.core/count %%))))

-------------------------
Detected 1 error\n"
               #?(:cljs "(fn [%] (pos? (count %)))"
                  :clj "(fn [%] (pos? (count %)))"))
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
   (pf.core/fn [%%] (pf.core/pos? (pf.core/count %%))))
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
   (pf.core/fn [%%] (pf.core/pos? (pf.core/count %%))))
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
    (is (= (pf "-- Syntax error -------------------

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

(deftest keys-spec
  (testing "missing keys"
    (is (= (pf "-- Spec failed --------------------

  {}

should contain keys: :age, :keys-spec/name

|             key |    spec |
|-----------------+---------|
|            :age |    int? |
| :keys-spec/name | string? |

-- Relevant specs -------

:keys-spec/user:
  %s

-------------------------
Detected 1 error\n"
               #?(:cljs "(cljs.spec.alpha/keys :req [:keys-spec/name] :req-un [:keys-spec/age])"
                  :clj "(clojure.spec.alpha/keys\n   :req\n   [:keys-spec/name]\n   :req-un\n   [:keys-spec/age])"))
           (expound/expound-str :keys-spec/user {}))))
  (testing "missing compound keys"
    (is (= (pf "-- Spec failed --------------------

  {}

should contain keys:

(and (and :keys-spec/name :keys-spec/age) (or :zip (and :state :city)))

|             key |     spec |
|-----------------+----------|
|           :city |  string? |
|          :state |  string? |
|            :zip | pos-int? |
|  :keys-spec/age |     int? |
| :keys-spec/name |  string? |

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

|    key |     spec |
|--------+----------|
|  :city |  string? |
| :state |  string? |
|   :zip | pos-int? |

-- Relevant specs -------

:keys-spec/user3:
  (pf.spec.alpha/keys
   :req-un
   [(or :key-spec/zip (and :key-spec/state :key-spec/city))])

-------------------------
Detected 1 error\n")
           (expound/expound-str :keys-spec/user3 {}))))

  (testing "inline spec with req-un"
    (is (= (pf "-- Spec failed --------------------

  {}

should contain keys: :age, :name

|   key |    spec |
|-------+---------|
|  :age |    int? |
| :name | string? |

-------------------------
Detected 1 error\n"
               #?(:cljs "(cljs.spec.alpha/keys :req [:keys-spec/name] :req-un [:keys-spec/age])"
                  :clj "(clojure.spec.alpha/keys\n   :req\n   [:keys-spec/name]\n   :req-un\n   [:keys-spec/age])"))
           (expound/expound-str (s/keys :req-un [:keys-spec/name :keys-spec/age]) {}))))

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
           (expound/expound-str :keys-spec/user {:age 1 :keys-spec/name :bob})))))

(s/def :multi-spec/value string?)
(s/def :multi-spec/children vector?)
(defmulti el-type :multi-spec/el-type)
(defmethod el-type :text [x]
  (s/keys :req [:multi-spec/value]))
(defmethod el-type :group [x]
  (s/keys :req [:multi-spec/children]))
(s/def :multi-spec/el (s/multi-spec el-type :multi-spec/el-type))

(deftest multi-spec
  (testing "missing dispatch key"
    (is (=
         (pf "-- Missing spec -------------------

Cannot find spec for

   {}

 Spec multimethod:      `expound.alpha-test/el-type`
 Dispatch function:     `:multi-spec/el-type`
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

 Spec multimethod:      `expound.alpha-test/el-type`
 Dispatch function:     `:multi-spec/el-type`
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

|               key |    spec |
|-------------------+---------|
| :multi-spec/value | string? |

-- Relevant specs -------

:multi-spec/el:
  (pf.spec.alpha/multi-spec
   expound.alpha-test/el-type
   :multi-spec/el-type)

-------------------------
Detected 1 error\n")
         (expound/expound-str :multi-spec/el {:multi-spec/el-type :text})))))

(s/def :recursive-spec/tag #{:text :group})
(s/def :recursive-spec/on-tap (s/coll-of map? :kind vector?))
(s/def :recursive-spec/props (s/keys :opt-un [:recursive-spec/on-tap]))
(s/def :recursive-spec/el (s/keys :req-un [:recursive-spec/tag]
                                  :opt-un [:recursive-spec/props :recursive-spec/children]))
(s/def :recursive-spec/children (s/coll-of (s/nilable :recursive-spec/el) :kind vector?))

(deftest recursive-spec
  (testing "only shows problem with data at 'leaves' (not problems with all parents in tree)"
    (is (= (pf "-- Spec failed --------------------

  {:tag ...,
   :children
   [{:tag ...,
     :children
     [{:tag ..., :props {:on-tap {}}}]}]}
                                 ^^

should satisfy

  vector?

-- Relevant specs -------

%s

-------------------------
Detected 1 error\n"
               #?(:cljs ":recursive-spec/on-tap:
  (cljs.spec.alpha/coll-of cljs.core/map? :kind cljs.core/vector?)
:recursive-spec/props:
  (cljs.spec.alpha/keys :opt-un [:recursive-spec/on-tap])
:recursive-spec/children:
  (cljs.spec.alpha/coll-of
   (cljs.spec.alpha/nilable :recursive-spec/el)
   :kind
   cljs.core/vector?)
:recursive-spec/el:
  (cljs.spec.alpha/keys
   :req-un
   [:recursive-spec/tag]
   :opt-un
   [:recursive-spec/props :recursive-spec/children])"
                  :clj ":recursive-spec/on-tap:
  (clojure.spec.alpha/coll-of
   clojure.core/map?
   :kind
   clojure.core/vector?)
:recursive-spec/props:
  (clojure.spec.alpha/keys :opt-un [:recursive-spec/on-tap])
:recursive-spec/children:
  (clojure.spec.alpha/coll-of
   (clojure.spec.alpha/nilable :recursive-spec/el)
   :kind
   clojure.core/vector?)
:recursive-spec/el:
  (clojure.spec.alpha/keys
   :req-un
   [:recursive-spec/tag]
   :opt-un
   [:recursive-spec/props :recursive-spec/children])"))
           (expound/expound-str
            :recursive-spec/el
            {:tag :group
             :children [{:tag :group
                         :children [{:tag :group
                                     :props {:on-tap {}}}]}]})))))

(s/def :cat-wrapped-in-or-spec/kv (s/and
                                   sequential?
                                   (s/cat :k keyword? :v any?)))
(s/def :cat-wrapped-in-or-spec/type #{:text})
(s/def :cat-wrapped-in-or-spec/kv-or-string (s/or
                                             :map (s/keys :req [:cat-wrapped-in-or-spec/type])
                                             :kv :cat-wrapped-in-or-spec/kv))

(deftest cat-wrapped-in-or-spec
  ;; FIXME - make multiple types of specs on the same value display as single error
  (is (= (pf "-- Spec failed --------------------

  {\"foo\" \"hi\"}

should contain key: :cat-wrapped-in-or-spec/type

|                          key |     spec |
|------------------------------+----------|
| :cat-wrapped-in-or-spec/type | #{:text} |

-- Relevant specs -------

:cat-wrapped-in-or-spec/kv-or-string:
  (pf.spec.alpha/or
   :map
   (pf.spec.alpha/keys :req [:cat-wrapped-in-or-spec/type])
   :kv
   :cat-wrapped-in-or-spec/kv)

-- Spec failed --------------------

  {\"foo\" \"hi\"}

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
Detected 2 errors\n")
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

;; I want to do something like
;; (s/def :specs.coll-of/into #{[] '() #{}})
;; but Clojure (not Clojurescript) won't allow
;; this. As a workaround, I'll just use vectors instead
;; of vectors and lists.
;; FIXME - force a specific type of into/kind one for each test
;; (one for vectors, one for lists, etc)
(s/def :specs.coll-of/into #{[] #{}})
(s/def :specs.coll-of/kind #{vector? list? set?})
(s/def :specs.coll-of/count pos-int?)
(s/def :specs.coll-of/max-count pos-int?)
(s/def :specs.coll-of/min-count pos-int?)
(s/def :specs.coll-of/distinct boolean?)

(s/def :specs/every-args
  (s/keys :req-un
          [:specs.coll-of/into
           :specs.coll-of/kind
           :specs.coll-of/count
           :specs.coll-of/max-count
           :specs.coll-of/min-count
           :specs.coll-of/distinct]))

(defn apply-coll-of [spec {:keys [into kind count max-count min-count distinct gen-max gen-into gen] :as opts}]
  (s/coll-of spec :into into :min-count min-count :max-count max-count :distinct distinct))

(defn apply-map-of [spec1 spec2 {:keys [into kind count max-count min-count distinct gen-max gen-into gen] :as opts}]
  (s/map-of spec1 spec2 :into into :min-count min-count :max-count max-count :distinct distinct))

;; Since CLJS prints out entire source of a function when
;; it pretty-prints a failure, the output becomes much nicer if
;; we wrap each function in a simple spec
(expound/def :specs/string string? "should be a string")
(expound/def :specs/vector vector? "should be a vector")
(s/def :specs/int int?)
(s/def :specs/boolean boolean?)
(expound/def :specs/keyword keyword? "should be a keyword")
(s/def :specs/map map?)
(s/def :specs/symbol symbol?)
(s/def :specs/pos-int pos-int?)
(s/def :specs/neg-int neg-int?)
(s/def :specs/zero #(and (number? %) (zero? %)))
(s/def :specs/keys (s/keys
                    :req-un [:specs/string]
                    :req [:specs/map]
                    :opt-un [:specs/vector]
                    :opt [:specs/int]))

(def simple-spec-gen (gen/one-of
                      [(gen/elements [:specs/string
                                      :specs/vector
                                      :specs/int
                                      :specs/boolean
                                      :specs/keyword
                                      :specs/map
                                      :specs/symbol
                                      :specs/pos-int
                                      :specs/neg-int
                                      :specs/zero
                                      :specs/keys])
                       (gen/set gen/simple-type-printable)]))

(deftest generated-simple-spec
  (checking
   "simple spec"
   num-tests
   [simple-spec simple-spec-gen
    :let [sp-form (s/form simple-spec)]
    form gen/any-printable]
   (is (string? (expound/expound-str simple-spec form)))))

(deftest generated-coll-of-specs
  (checking
   "'coll-of' spec"
   num-tests
   [simple-spec simple-spec-gen
    every-args (s/gen :specs/every-args)
    :let [spec (apply-coll-of simple-spec every-args)]
    :let [sp-form (s/form spec)]
    form gen/any-printable]
   (is (string? (expound/expound-str spec form)))))

(deftest generated-and-specs
  (checking
   "'and' spec"
   num-tests
   [simple-spec1 simple-spec-gen
    simple-spec2 simple-spec-gen
    :let [spec (s/and simple-spec1 simple-spec2)]
    :let [sp-form (s/form spec)]
    form gen/any-printable]
   (is (string? (expound/expound-str spec form)))))

(deftest generated-or-specs
  (checking
   "'or' spec"
   num-tests
   [simple-spec1 simple-spec-gen
    simple-spec2 simple-spec-gen
    :let [spec (s/or :or1 simple-spec1 :or2 simple-spec2)]
    :let [sp-form (s/form spec)]
    form gen/any-printable]
   (is (string? (expound/expound-str spec form)))))

(deftest generated-map-of-specs
  (checking
   "'map-of' spec"
   num-tests
   [simple-spec1 simple-spec-gen
    simple-spec2 simple-spec-gen
    simple-spec3 simple-spec-gen
    every-args1 (s/gen :specs/every-args)
    every-args2 (s/gen :specs/every-args)
    :let [spec (apply-map-of simple-spec1 (apply-map-of simple-spec2 simple-spec3 every-args1) every-args2)
          sp-form (s/form spec)]
    form any-printable-wo-nan]
   (is (string? (expound/expound-str spec form)))))

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

(defn real-spec [form]
  (walk/prewalk
   (fn [x]
     (if (vector? x)
       (case (first x)
         :expound.ds/opt-key
         (ds/map->OptionalKey (second x))

         :expound.ds/req-key
         (ds/map->RequiredKey (second x))

         :expound.ds/maybe-spec
         (ds/maybe (second x))

         x)
       x))
   form))

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

(def data-spec-compat?
  #?(:cljs
     (do
       ;; FIXME - anything including or after 1.9.908
       ;; should work, but seems to fail, possibly due to?
       ;; https://dev.clojure.org/jira/browse/CLJS-1297?
       (not= "1.9.562"
             *clojurescript-version*)
       ;; Just force false for now
       false)
     :clj
     true))

(when data-spec-compat?
  (deftest generated-data-specs
    (checking
     "generated data specs"
     num-tests
     [data-spec (s/gen :expound.ds/spec)
      form any-printable-wo-nan
      prefix (s/gen qualified-keyword?)
      :let [gen-spec (ds/spec prefix (real-spec data-spec))]]
     (is (string? (expound/expound-str gen-spec form))))))

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

(s/fdef test-instrument-adder
        :args (s/cat :x int? :y int?)
        :fn #(> (:ret %) (-> % :args :x))
        :ret pos-int?)
(defn test-instrument-adder
  ([x]) ;; Just to avoid compiler errors
  ([x y]
   (+ x y)))

(defn no-linum [s]
  (string/replace s #"(.cljc?):\d+" "$1:LINUM"))

(deftest test-instrument
  (st/instrument `test-instrument-adder)
  #?(:cljs (is (=
                "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
<filename missing>:<line number missing>

-- Spec failed --------------------

Function arguments

  (\"\" ...)
   ^^

should satisfy

  int?

-- Example ------------------------

  (<f> 0 0)

-------------------------
Detected 1 error\n"
                (.-message (try
                             (binding [s/*explain-out* expound/printer]
                               (test-instrument-adder "" :x))
                             (catch :default e e)))))
     :clj
     (is (= "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
alpha_test.cljc:LINUM

-- Spec failed --------------------

Function arguments

  (\"\" ...)
   ^^

should satisfy

  int?

-- Example ------------------------

  (<f> 0 0)

-------------------------
Detected 1 error\n"
            (no-linum
             (:cause
              (Throwable->map (try
                                (binding [s/*explain-out* expound/printer]
                                  (test-instrument-adder "" :x))
                                (catch Exception e e))))))))
  (st/unstrument `test-instrument-adder))

(deftest test-instrument-with-orchestra-args-spec-failure
  (orch.st/instrument `test-instrument-adder)
  #?(:cljs (is (=
                "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
<filename missing>:<line number missing>

-- Spec failed --------------------

Function arguments

  (\"\" ...)
   ^^

should satisfy

  int?

-- Example ------------------------

  (<f> 0 0)

-------------------------
Detected 1 error\n"
                (.-message (try
                             (binding [s/*explain-out* expound/printer]
                               (test-instrument-adder "" :x))
                             (catch :default e e)))))
     :clj
     (is (= "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
alpha_test.cljc:LINUM

-- Spec failed --------------------

Function arguments

  (\"\" ...)
   ^^

should satisfy

  int?

-- Example ------------------------

  (<f> 0 0)

-------------------------
Detected 1 error\n"
            (no-linum
             (:cause
              (Throwable->map (try
                                (binding [s/*explain-out* expound/printer]
                                  (test-instrument-adder "" :x))
                                (catch Exception e e))))))))
  (orch.st/unstrument `test-instrument-adder))

(deftest test-instrument-with-orchestra-args-syntax-failure
  (orch.st/instrument `test-instrument-adder)
  #?(:cljs (is (=
                "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
<filename missing>:<line number missing>

-- Syntax error -------------------

Function arguments

  (1)

should have additional elements. The next element \":y\" should satisfy

  int?

-- Example ------------------------

  (<f> -1 0)

-------------------------
Detected 1 error\n"
                (.-message (try
                             (binding [s/*explain-out* expound/printer]
                               (test-instrument-adder 1))
                             (catch :default e e)))))
     :clj
     (is (= "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
alpha_test.cljc:LINUM

-- Syntax error -------------------

Function arguments

  (1)

should have additional elements. The next element \":y\" should satisfy

  int?

-- Example ------------------------

  (<f> -1 0)

-------------------------
Detected 1 error\n"
            (no-linum
             (:cause
              (Throwable->map (try
                                (binding [s/*explain-out* expound/printer]
                                  (test-instrument-adder 1))
                                (catch Exception e e))))))))
  (orch.st/unstrument `test-instrument-adder))

(deftest test-instrument-with-orchestra-ret-failure
  (orch.st/instrument `test-instrument-adder)
  #?(:cljs (is (=
                "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
<filename missing>:<line number missing>

-- Spec failed --------------------

Return value

  -3

should satisfy

  pos-int?

-------------------------
Detected 1 error\n"
                (.-message (try
                             (binding [s/*explain-out* expound/printer]
                               (test-instrument-adder -1 -2))
                             (catch :default e e)))))
     :clj
     (is (= "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
alpha_test.cljc:LINUM

-- Spec failed --------------------

Return value

  -3

should satisfy

  pos-int?

-------------------------
Detected 1 error\n"
            (no-linum
             (:cause
              (Throwable->map (try
                                (binding [s/*explain-out* expound/printer]
                                  (test-instrument-adder -1 -2))
                                (catch Exception e e))))))))
  (orch.st/unstrument `test-instrument-adder))

(deftest test-instrument-with-orchestra-fn-failure
  (orch.st/instrument `test-instrument-adder)
  #?(:cljs (is (=
                "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
<filename missing>:<line number missing>

-- Spec failed --------------------

Function arguments and return value

  {:ret 1, :args {:x 1, :y 0}}

should satisfy

  (fn [%] (> (:ret %) (-> % :args :x)))

-------------------------
Detected 1 error\n"
                (.-message (try
                             (binding [s/*explain-out* expound/printer]
                               (test-instrument-adder 1 0))
                             (catch :default e e)))))
     :clj
     (is (= "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
alpha_test.cljc:LINUM

-- Spec failed --------------------

Function arguments and return value

  {:ret 1, :args {:x 1, :y 0}}

should satisfy

  (fn
   [%]
   (> (:ret %) (-> % :args :x)))

-------------------------
Detected 1 error\n"
            (no-linum
             (:cause
              (Throwable->map (try
                                (binding [s/*explain-out* expound/printer]
                                  (test-instrument-adder 1 0))
                                (catch Exception e e))))))))
  (orch.st/unstrument `test-instrument-adder))

(deftest test-instrument-with-custom-value-printer
  (st/instrument `test-instrument-adder)
  #?(:cljs
     (is (=
          "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
<filename missing>:<line number missing>

-- Spec failed --------------------

Function arguments

  (\"\" :x)
   ^^

should satisfy

  int?

-- Example ------------------------

  (<f> 0 0)

-------------------------
Detected 1 error\n"
          (.-message (try
                       (binding [s/*explain-out* (expound/custom-printer {:show-valid-values? true})]
                         (test-instrument-adder "" :x))
                       (catch :default e e)))))
     :clj
     (is (= "Call to #'expound.alpha-test/test-instrument-adder did not conform to spec:
alpha_test.cljc:LINUM

-- Spec failed --------------------

Function arguments

  (\"\" :x)
   ^^

should satisfy

  int?

-- Example ------------------------

  (<f> 0 0)

-------------------------
Detected 1 error\n"
            (no-linum
             (:cause
              (Throwable->map (try
                                (binding [s/*explain-out* (expound/custom-printer {:show-valid-values? true})]
                                  (test-instrument-adder "" :x))
                                (catch Exception e e))))))))
  (st/unstrument `test-instrument-adder))

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
           (binding [s/*explain-out* (expound/custom-printer {:value-str-fn (fn [spec-name form path val] "<HIDDEN>")})]
             (s/explain-str :custom-printer/strings ["a" "b" :c])))))
  (testing "modified version of the included value printer"
    (testing "custom value printer"
      (is (= (pf "-- Spec failed --------------------

  [\"a\" \"b\" :c]
           ^^

should satisfy

  string?

-- Relevant specs -------

:custom-printer/strings:
  (pf.spec.alpha/coll-of pf.core/string?)

-------------------------
Detected 1 error
")
             (binding [s/*explain-out* (expound/custom-printer {:value-str-fn (partial expound/value-in-context {:show-valid-values? true})})]
               (s/explain-str :custom-printer/strings ["a" "b" :c])))))))

(defn spec-dependencies [spec]
  (->> spec
       s/form
       (tree-seq coll? seq)
       (filter #(and (s/get-spec %) (not= spec %)))
       distinct))

(defn topo-sort [specs]
  (deps/topo-sort
   (reduce
    (fn [gr spec]
      (reduce
       (fn [g d]
         ;; If this creates a circular reference, then
         ;; just skip it.
         (if (deps/depends? g d spec)
           g
           (deps/depend g spec d)))
       gr
       (spec-dependencies spec)))
    (deps/graph)
    specs)))

(s/def :alt-spec/int-or-str (s/alt :int int? :string string?))
(deftest alt-spec
  (is (=  (pf "-- Spec failed --------------------

  [:hi]
   ^^^

should satisfy

  int?

or

  string?

-- Relevant specs -------

:alt-spec/int-or-str:
  %s

-------------------------
Detected 1 error\n"
              #?(:clj "(clojure.spec.alpha/alt
   :int
   clojure.core/int?
   :string
   clojure.core/string?)"
                 :cljs "(cljs.spec.alpha/alt :int cljs.core/int? :string cljs.core/string?)"))
          (expound/expound-str :alt-spec/int-or-str [:hi]))))

#?(:clj
   (def spec-gen (gen/elements (->> (s/registry)
                                    (map key)
                                    topo-sort
                                    (filter keyword?)))))

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
    (int x)

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
         (s/assert (s/nilable #{"Insufficient input" "Extra input" "no method"}) "Key must be integer")))))

(defn inline-specs [keyword]
  (walk/postwalk
   (fn [x]
     (if (contains? (s/registry) x)
       (s/form x)
       x))
   (s/form keyword)))

#?(:clj
   (deftest real-spec-tests
     (checking
      "for any real-world spec and any data, explain-str returns a string"
      ;; At 50, it might find a bug in failures for the
      ;; :ring/handler spec, but keep it plugged in, since it
      ;; takes a long time to shrink
      num-tests
      [spec spec-gen
       form gen/any-printable]
      ;; Can't reliably test fspecs until
      ;; https://dev.clojure.org/jira/browse/CLJ-2258 is fixed
      ;; because the algorithm to fix up the 'in' paths depends
      ;; on the non-conforming value existing somewhere within
      ;; the top-level form
      (when-not (some
                 #{"clojure.spec.alpha/fspec"}
                 (->> spec
                      inline-specs
                      (tree-seq coll? identity)
                      (map str)))
        (is (string? (expound/expound-str spec form)))))))

#?(:clj
   (deftest assert-on-real-spec-tests
     (checking
      "for any real-world spec and any data, assert returns an error that matches explain-str"
      num-tests
      [spec spec-gen
       form gen/any-printable]
      ;; Can't reliably test fspecs until
      ;; https://dev.clojure.org/jira/browse/CLJ-2258 is fixed
      ;; because the algorithm to fix up the 'in' paths depends
      ;; on the non-conforming value existing somewhere within
      ;; the top-level form
      (when-not (some
                 #{"clojure.spec.alpha/fspec"}
                 (->> spec
                      inline-specs
                      (tree-seq coll? identity)
                      (map str)))
        (when-not (s/valid? spec form)
          (let [expected-err-msg (str "Spec assertion failed\n"
                                      (binding [s/*explain-out* (expound/custom-printer {:print-specs? true})]
                                        (s/explain-str spec form)))]
            (is (thrown-with-msg?
                 #?(:cljs :default :clj Exception)
                 (re-pattern (java.util.regex.Pattern/quote expected-err-msg))
                 (binding [s/*explain-out* expound/printer]
                   (s/assert spec form)))
                (str "Expected: " expected-err-msg))))))))

(deftest test-mutate
  (checking
   "mutation alters data structure"
   num-tests
   [form gen/any-printable
    mutate-path (gen/vector gen/pos-int 1 10)]
   (is (not= form
             (mutate form mutate-path)))))

#?(:clj
   1
   ;; FIXME - we need to use generate mutated value, instead
   ;; of adding randomness to test
   #_(deftest real-spec-tests-mutated-valid-value
       (checking
        "for any real-world spec and any mutated valid data, explain-str returns a string"
        num-tests
        [spec spec-gen
         mutate-path (gen/vector gen/pos-int)]
        (when-not (some
                   #{"clojure.spec.alpha/fspec"}
                   (->> spec
                        inline-specs
                        (tree-seq coll? identity)
                        (map str)))
          (when (contains? (s/registry) spec)
            (try
              (let [valid-form (first (s/exercise spec 1))
                    invalid-form (mutate valid-form mutate-path)]
                (is (string? (expound/expound-str spec invalid-form))))
              (catch clojure.lang.ExceptionInfo e
                (when (not= :no-gen (::s/failure (ex-data e)))
                  (when (not= "Couldn't satisfy such-that predicate after 100 tries." (.getMessage e))
                    (throw e))))))))))

;; Using conformers for transformation should not crash by default, or at least give useful error message.
(defn numberify [s]
  (cond
    (number? s) s
    (re-matches #"^\d+$" s) #?(:cljs (js/parseInt s 10)
                               :clj (Integer. s))
    :default ::s/invalid))

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
   ; conform as sequence (seq function)
   (s/conformer seq)
   ; re-use previous sequence spec
   :conformers-test/string-AB-seq))

(deftest conformers-test
  ;; Example from http://cjohansen.no/a-unified-specification/
  (testing "conform string to int"
    (is (string?
         (expound/expound-str :conformers-test/number "123a"))))
  ;; Example from https://github.com/bhb/expound/issues/15#issuecomment-326838879
  (testing "conform maps"
    (is (string? (expound/expound-str :conformers-test/query {})))
    (is (thrown-with-msg?
         #?(:cljs :default :clj Exception)
         #".*Cannot convert path.*conformers.*"
         (expound/expound-str :conformers-test/query {:conformers-test.query/id :conformers-test/lookup-user
                                                      :conformers-test.query/params {}}))))
  ;; Minified example based on https://github.com/bhb/expound/issues/15
  (testing "conform string to seq"
    (is (thrown-with-msg?
         #?(:cljs :default :clj Exception)
         #".*Cannot find path segment in form.*conformers.*"
         (expound/expound-str :conformers-test/string-AB "AC")))))

(s/def :duplicate-preds/str-or-str (s/or
                                    ;; Use anonymous functions to assure
                                    ;; non-equality
                                    :str1 #(string? %)
                                    :str2 #(string? %)))
(deftest duplicate-preds
  (testing "duplicate preds only appear once"
    (is (= (pf "-- Spec failed --------------------

  1

should satisfy

  (fn [%%] (string? %%))

-- Relevant specs -------

:duplicate-preds/str-or-str:
  (pf.spec.alpha/or
   :str1
   (pf.core/fn [%%] (pf.core/string? %%))
   :str2
   (pf.core/fn [%%] (pf.core/string? %%)))

-------------------------
Detected 1 error\n")
           (expound/expound-str :duplicate-preds/str-or-str 1)))))

(s/def :fspec-test/div (s/fspec
                        :args (s/cat :x int? :y pos-int?)))

(defn my-div [x y]
  (assert (not (zero? (/ x y)))))

(defn  until-unsuccessful [f]
  (let [nil-or-failure #(if (= "Success!\n" %)
                          nil
                          %)]
    (or (nil-or-failure (f))
        (nil-or-failure (f))
        (nil-or-failure (f))
        (nil-or-failure (f))
        (nil-or-failure (f)))))

(deftest fspec-exception-test
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

(deftest fspec-ret-test
  (testing "invalid ret"
    (is (= (pf "-- Function spec failed -----------

  expound.alpha-test/my-plus

returned an invalid value

  0

should satisfy

  pos-int?

-- Relevant specs -------

:fspec-ret-test/my-int:
  pf.core/pos-int?
:fspec-ret-test/plus:
  (pf.spec.alpha/fspec
   :args
   (pf.spec.alpha/cat :x pf.core/int? :y pf.core/pos-int?)
   :ret
   :fspec-ret-test/my-int
   :fn
   nil)

-------------------------
Detected 1 error\n")
           (until-unsuccessful #(expound/expound-str :fspec-ret-test/plus my-plus))))

    (is (= (pf "-- Function spec failed -----------

  [expound.alpha-test/my-plus]
   ^^^^^^^^^^^^^^^^^^^^^^^^^^

returned an invalid value

  0

should satisfy

  pos-int?

-- Relevant specs -------

:fspec-ret-test/my-int:
  pf.core/pos-int?
:fspec-ret-test/plus:
  (pf.spec.alpha/fspec
   :args
   (pf.spec.alpha/cat :x pf.core/int? :y pf.core/pos-int?)
   :ret
   :fspec-ret-test/my-int
   :fn
   nil)

-------------------------
Detected 1 error\n")
           (until-unsuccessful #(expound/expound-str (s/coll-of :fspec-ret-test/plus) [my-plus]))))))

(s/def :fspec-fn-test/minus (s/fspec
                             :args (s/cat :x int? :y int?)
                             :fn (s/and
                                  #(< (:ret %) (-> % :args :x))
                                  #(< (:ret %) (-> % :args :y)))))

(defn my-minus [x y]
  (- x y))

(deftest fspec-fn-test
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

(deftest ifn-fspec-test
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

#?(:clj
   (deftest form-containing-incomparables
     (checking
      "for any value including NaN, or Infinity, expound returns a string"
      num-tests
      [form (gen/frequency
             [[1 (gen/elements
                  [Double/NaN
                   Double/POSITIVE_INFINITY
                   Double/NEGATIVE_INFINITY
                   '(Double/NaN Double/POSITIVE_INFINITY Double/NEGATIVE_INFINITY)
                   [Double/NaN Double/POSITIVE_INFINITY Double/NEGATIVE_INFINITY]
                   {Double/NaN Double/NaN
                    Double/POSITIVE_INFINITY Double/POSITIVE_INFINITY
                    Double/NEGATIVE_INFINITY Double/NEGATIVE_INFINITY}])]
              [5 gen/any-printable]])]
      (is (string? (expound/expound-str (constantly false) form))))))

#?(:cljs
   (deftest form-containing-incomparables
     (checking
      "for any value including NaN, or Infinity, expound returns a string"
      num-tests
      [form (gen/frequency
             [[1 (gen/elements
                  [js/NaN
                   js/Infinity
                   js/-Infinity
                   '(js/NaN js/Infinity js/-Infinity)
                   [js/NaN js/Infinity js/-Infinity]
                   {js/NaN js/NaN
                    js/Infinity js/Infinity
                    js/-Infinity js/-Infinity}])]
              [5 gen/any-printable]])]
      (is (string? (expound/expound-str (constantly false) form))))))

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

(deftest multispec-in-compound-spec
  (testing "multispec combined with s/and"
    (is (= (pf "-- Missing spec -------------------

Cannot find spec for

   {:pet/type :fish}

 Spec multimethod:      `expound.alpha-test/pet`
 Dispatch function:     `:pet/type`
 Dispatch value:        `:fish`


-- Relevant specs -------

:multispec-in-compound-spec/pet1:
  (pf.spec.alpha/and
   pf.core/map?
   (pf.spec.alpha/multi-spec expound.alpha-test/pet :pet/type))

-------------------------
Detected 1 error\n")
           (expound/expound-str :multispec-in-compound-spec/pet1 {:pet/type :fish}))))
  (testing "multispec combined with s/or"
    (is (= (pf "-- Missing spec -------------------

Cannot find spec for

   {:pet/type :fish}

 Spec multimethod:      `expound.alpha-test/pet`
 Dispatch function:     `:pet/type`
 Dispatch value:        `:fish`

Cannot find spec for

   {:pet/type :fish}

 Spec multimethod:      `expound.alpha-test/animal`
 Dispatch function:     `:animal/type`
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

(s/fdef results-str-fn1
        :args (s/cat :x nat-int? :y nat-int?)
        :ret pos-int?)
(defn results-str-fn1 [x y]
  (+ x y))

(s/fdef results-str-fn2
        :args (s/cat :x nat-int? :y nat-int?)
        :fn #(let [x (-> % :args :x)
                   y (-> % :args :y)
                   ret (-> % :ret)]
               (< x ret)))
(defn results-str-fn2 [x y]
  (+ x y))

(s/fdef results-str-fn3
        :args (s/cat :x #{0} :y #{0})
        :ret nat-int?)
(defn results-str-fn3 [x y]
  (+ x y))

(s/fdef results-str-fn4
        :args (s/cat :x int?)
        :ret (s/coll-of int?))
(defn results-str-fn4 [x]
  [x :not-int])

(s/fdef results-str-fn5
        :args (s/cat :x #{1} :y #{1})
        :ret string?)
(defn results-str-fn5
  [x y]
  #?(:clj (throw (Exception. "Ooop!"))
     :cljs (throw (js/Error. "Oops!"))))

(s/fdef results-str-fn6
        :args (s/cat :f fn?)
        :ret any?)
(defn results-str-fn6
  [f]
  (f 1))

(s/fdef results-str-missing-fn
        :args (s/cat :x int?))

(s/fdef results-str-missing-args-spec
        :ret int?)
(defn results-str-missing-args-spec [] 1)

(deftest explain-results
  (testing "explaining results with non-expound printer"
    (is (thrown-with-msg?
         #?(:cljs :default :clj Exception)
         #"Cannot print check results"
         (binding [s/*explain-out* s/explain-printer]
           (expound/explain-results-str (orch.st/with-instrument-disabled (st/check `results-str-fn1)))))))

  (testing "single bad result (failing return spec)"
    (is (= (pf
            "== Checked expound.alpha-test/results-str-fn1 

-- Function spec failed -----------

  (expound.alpha-test/results-str-fn1 0 0)

returned an invalid value.

  0

should satisfy

  pos-int?

-------------------------
Detected 1 error
")
           (binding [s/*explain-out* expound/printer]
             (expound/explain-results-str (orch.st/with-instrument-disabled (st/check `results-str-fn1)))))))
  (testing "single bad result (failing fn spec)"
    (is (= (pf "== Checked expound.alpha-test/results-str-fn2 

-- Function spec failed -----------

  (expound.alpha-test/results-str-fn2 0 0)

failed spec. Function arguments and return value

  {:args {:x 0, :y 0}, :ret 0}

should satisfy

  (fn
   [%%]
   (let
    [x
     (-> %% :args :x)
     y
     (-> %% :args :y)
     ret
     (-> %% :ret)]
    (< x ret)))

-------------------------
Detected 1 error
")
           (binding [s/*explain-out* expound/printer]
             (expound/explain-results-str (orch.st/with-instrument-disabled (st/check `results-str-fn2)))))))
  (testing "single valid result"
    (is (= "== Checked expound.alpha-test/results-str-fn3 

Success!
"
           (binding [s/*explain-out* expound/printer]
             (expound/explain-results-str (st/check `results-str-fn3))))))
  #?(:clj
     (testing "multiple results"
       (is (= "== Checked expound.alpha-test/results-str-fn2 

-- Function spec failed -----------

  (expound.alpha-test/results-str-fn2 0 0)

failed spec. Function arguments and return value

  {:args {:x 0, :y 0}, :ret 0}

should satisfy

  (fn
   [%]
   (let
    [x
     (-> % :args :x)
     y
     (-> % :args :y)
     ret
     (-> % :ret)]
    (< x ret)))

-------------------------
Detected 1 error


== Checked expound.alpha-test/results-str-fn3 

Success!
"
              (binding [s/*explain-out* expound/printer]
                (expound/explain-results-str (orch.st/with-instrument-disabled (st/check [`results-str-fn2 `results-str-fn3]))))))))
  (testing "check-fn"
    (is (= "== Checked <unknown> ========================

-- Function spec failed -----------

  (<unknown> 0 0)

failed spec. Function arguments and return value

  {:args {:x 0, :y 0}, :ret 0}

should satisfy

  (fn
   [%]
   (let
    [x
     (-> % :args :x)
     y
     (-> % :args :y)
     ret
     (-> % :ret)]
    (< x ret)))

-------------------------
Detected 1 error
"
           (binding [s/*explain-out* expound/printer]
             (expound/explain-result-str (st/check-fn `results-str-fn1 (s/spec `results-str-fn2)))))))
  #?(:clj (testing "custom printer"
            (is (= "== Checked expound.alpha-test/results-str-fn4 

-- Function spec failed -----------

  (expound.alpha-test/results-str-fn4 0)

returned an invalid value.

  [0 :not-int]
     ^^^^^^^^

should satisfy

  int?

-------------------------
Detected 1 error
"
                   (binding [s/*explain-out* (expound/custom-printer {:show-valid-values? true})]
                     (expound/explain-results-str (orch.st/with-instrument-disabled (st/check `results-str-fn4))))))))
  (testing "exceptions raised during check"
    (is (= "== Checked expound.alpha-test/results-str-fn5 

  (expound.alpha-test/results-str-fn5 1 1)

 threw error"
           (binding [s/*explain-out* expound/printer]
             (take-lines 5 (expound/explain-results-str (orch.st/with-instrument-disabled (st/check `results-str-fn5))))))))
  (testing "colorized output"
    (is (= (pf "<CYAN>== Checked expound.alpha-test/results-str-fn5 <NONE>

<RED>  (expound.alpha-test/results-str-fn5 1 1)<NONE>

 threw error")
           (binding [s/*explain-out* (expound/custom-printer {:theme :figwheel-theme})]
             (readable-ansi (take-lines 5 (expound/explain-results-str (orch.st/with-instrument-disabled (st/check `results-str-fn5)))))))))

  (testing "failure to generate"
    (is (=
         #?(:clj "== Checked expound.alpha-test/results-str-fn6 

Unable to construct generator for [:f] in

  (clojure.spec.alpha/cat :f clojure.core/fn?)
"
            ;; CLJS doesn't contain correct data for check failure

            :cljs "== Checked expound.alpha-test/results-str-fn6 

Unable to construct gen at: [:f] for: fn? in

  (cljs.spec.alpha/cat :f cljs.core/fn?)
")
         (binding [s/*explain-out* expound/printer]
           (expound/explain-results-str (orch.st/with-instrument-disabled (st/check `results-str-fn6)))))))
  (testing "no-fn failure"
    (is (= #?(:clj "== Checked expound.alpha-test/results-str-missing-fn 

Failed to check function.

  expound.alpha-test/results-str-missing-fn

is not defined
"
              :cljs "== Checked <unknown> ========================

Failed to check function.

  <unknown>

is not defined
")
           (binding [s/*explain-out* expound/printer]
             (expound/explain-results-str (orch.st/with-instrument-disabled (st/check `results-str-missing-fn)))))))
  (testing "no args spec"
    (is (= (pf "== Checked expound.alpha-test/results-str-missing-args-spec 

Failed to check function.

  (pf.spec.alpha/fspec :ret pf.core/int?)

should contain an :args spec
")
           (binding [s/*explain-out* expound/printer]
             (expound/explain-results-str (orch.st/with-instrument-disabled (st/check `results-str-missing-args-spec))))))))

#?(:clj (deftest explain-results-gen
          (checking
           "all functions can be checked and printed"
           num-tests
           [sym-to-check (gen/elements (st/checkable-syms))]
          ;; Just confirm an error is not thrown
           (is (string?
                (expound/explain-results-str
                 (orch.st/with-instrument-disabled
                   (with-out-str
                     (st/check sym-to-check
                               {:clojure.spec.test.check/opts {:num-tests 10}})))))))))

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
         (binding [s/*explain-out* (expound/custom-printer {:theme :none})]
           (s/explain-str :colorized-output/strings ["" :a ""]))))
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
         (binding [s/*explain-out* (expound/custom-printer {:theme :figwheel-theme})]
           (readable-ansi (s/explain-str :colorized-output/strings ["" :a ""]))))))
