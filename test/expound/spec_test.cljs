(ns expound.spec-test
  (:require [cljs.test :as ct :refer [is testing deftest use-fixtures]]
            [com.gfredericks.test.chuck.clojure-test :refer-macros [checking]]
            [clojure.test.check.generators :as gen]
            [cljs.spec.alpha :as s]
            [expound.spec :as e.s]
            [expound.test-utils :as test-utils]))

(use-fixtures :once test-utils/check-spec-assertions)

(def any-printable-wo-nan (gen/such-that (complement test-utils/contains-nan?) gen/any-printable))

(deftest highlighted-form
  (testing "atomic value"
    (is (= "\"Fred\"\n^^^^^^"
           (e.s/highlighted-form
            "Fred"
            []))))
  (testing "value in vector"
    (is (= "[... :b ...]\n     ^^"
           (e.s/highlighted-form
            [:a :b :c]
            [1]))))
  (testing "long, composite values are pretty-printed"
    (is (= "{:letters {:a \"aaaaaaaa\",
           :b \"bbbbbbbb\",
           :c \"cccccccd\",
           :d \"dddddddd\",
           :e \"eeeeeeee\"}}
          ^^^^^^^^^^^^^^^^"
           (e.s/highlighted-form
            {:letters
             {:a "aaaaaaaa"
              :b "bbbbbbbb"
              :c "cccccccd"
              :d "dddddddd"
              :e "eeeeeeee"}}
            [:letters])))))

(s/def :simple-type-based-spec/str string?)

(deftest simple-type-based-spec
  (testing "valid value"
    (is (= "Success!\n"
           (e.s/pretty-explain-str :simple-type-based-spec/str ""))))

  (testing "invalid value"
    (is (=
         "-- Spec failed --------------------

  1

should satisfy

  string?

-- Relevant specs -------

:simple-type-based-spec/str:
  cljs.core/string?

-------------------------
Detected 1 error"
         (e.s/pretty-explain-str :simple-type-based-spec/str 1)))))

(s/def :set-based-spec/tag #{:foo :bar})
(s/def :set-based-spec/nilable-tag (s/nilable :set-based-spec/tag))

(deftest set-based-spec
  (testing "prints valid options"
    (is (= "-- Spec failed --------------------

  :baz

should be one of: `:bar`,`:foo`

-- Relevant specs -------

:set-based-spec/tag:
  #{:bar :foo}

-------------------------
Detected 1 error"
           (e.s/pretty-explain-str :set-based-spec/tag :baz))))
  ;; FIXME - we should fix nilable and or specs better so they are clearly grouped
  (testing "nilable version"
    (is (= "-- Spec failed --------------------

  :baz

should be one of: `:bar`,`:foo`

-- Relevant specs -------

:set-based-spec/tag:
  #{:bar :foo}
:set-based-spec/nilable-tag:
  (cljs.spec.alpha/nilable :set-based-spec/tag)

-- Spec failed --------------------

  :baz

should satisfy

  nil?

-- Relevant specs -------

:set-based-spec/nilable-tag:
  (cljs.spec.alpha/nilable :set-based-spec/tag)

-------------------------
Detected 2 errors"
           (e.s/pretty-explain-str :set-based-spec/nilable-tag :baz)))))

(s/def :nested-type-based-spec/str string?)
(s/def :nested-type-based-spec/strs (s/coll-of :nested-type-based-spec/str))

(deftest nested-type-based-spec
  (is (=
       "-- Spec failed --------------------

  [... ... 33]
           ^^

should satisfy

  string?

-- Relevant specs -------

:nested-type-based-spec/str:
  cljs.core/string?
:nested-type-based-spec/strs:
  (cljs.spec.alpha/coll-of :nested-type-based-spec/str)

-------------------------
Detected 1 error"
       (e.s/pretty-explain-str :nested-type-based-spec/strs ["one" "two" 33]))))

(s/def :nested-type-based-spec-special-summary-string/int int?)
(s/def :nested-type-based-spec-special-summary-string/ints (s/coll-of :nested-type-based-spec-special-summary-string/int))

(deftest nested-type-based-spec-special-summary-string
  (is (=
       "-- Spec failed --------------------

  [... ... \"...\"]
           ^^^^^

should satisfy

  int?

-- Relevant specs -------

:nested-type-based-spec-special-summary-string/int:
  cljs.core/int?
:nested-type-based-spec-special-summary-string/ints:
  (cljs.spec.alpha/coll-of
   :nested-type-based-spec-special-summary-string/int)

-------------------------
Detected 1 error"
       (e.s/pretty-explain-str :nested-type-based-spec-special-summary-string/ints [1 2 "..."]))))

(s/def :or-spec/str-or-int (s/or :int int? :str string?))
(s/def :or-spec/vals (s/coll-of :or-spec/str-or-int))

(deftest or-spec
  (testing "simple value"
    (is (= "-- Spec failed --------------------

  :kw

should satisfy

  int?

or

  string?

-- Relevant specs -------

:or-spec/str-or-int:
  (cljs.spec.alpha/or :int cljs.core/int? :str cljs.core/string?)

-------------------------
Detected 1 error"
           (e.s/pretty-explain-str :or-spec/str-or-int :kw))))
  (testing "collection of values"
    (is (= "-- Spec failed --------------------

  [... ... :kw ...]
           ^^^

should satisfy

  int?

or

  string?

-- Relevant specs -------

:or-spec/str-or-int:
  (cljs.spec.alpha/or :int cljs.core/int? :str cljs.core/string?)
:or-spec/vals:
  (cljs.spec.alpha/coll-of :or-spec/str-or-int)

-------------------------
Detected 1 error"
           (e.s/pretty-explain-str :or-spec/vals [0 "hi" :kw "bye"])))))

(s/def :and-spec/name (s/and string? #(pos? (count %))))
(s/def :and-spec/names (s/coll-of :and-spec/name))
(deftest and-spec
  (testing "simple value"
    (is (= "-- Spec failed --------------------

  \"\"

should satisfy

  (pos? (count %))

-- Relevant specs -------

:and-spec/name:
  (cljs.spec.alpha/and
   cljs.core/string?
   (cljs.core/fn [%] (cljs.core/pos? (cljs.core/count %))))

-------------------------
Detected 1 error"
           (e.s/pretty-explain-str :and-spec/name ""))))

  (testing "shows both failures in order"
    (is (=
         "-- Spec failed --------------------

  [... ... \"\" ...]
           ^^

should satisfy

  (pos? (count %))

-- Relevant specs -------

:and-spec/name:
  (cljs.spec.alpha/and
   cljs.core/string?
   (cljs.core/fn [%] (cljs.core/pos? (cljs.core/count %))))
:and-spec/names:
  (cljs.spec.alpha/coll-of :and-spec/name)

-- Spec failed --------------------

  [... ... ... 1]
               ^

should satisfy

  string?

-- Relevant specs -------

:and-spec/name:
  (cljs.spec.alpha/and
   cljs.core/string?
   (cljs.core/fn [%] (cljs.core/pos? (cljs.core/count %))))
:and-spec/names:
  (cljs.spec.alpha/coll-of :and-spec/name)

-------------------------
Detected 2 errors"
         (e.s/pretty-explain-str :and-spec/names ["bob" "sally" "" 1])))))

(s/def :coll-of-spec/big-int-coll (s/coll-of int? :min-count 10))

(deftest coll-of-spec
  (testing "min count"
    (is (=
         "-- Spec failed --------------------

  []

should satisfy

  (cljs.core/<= 10 (cljs.core/count %) 9007199254740991)

-- Relevant specs -------

:coll-of-spec/big-int-coll:
  (cljs.spec.alpha/coll-of cljs.core/int? :min-count 10)

-------------------------
Detected 1 error"
         (e.s/pretty-explain-str :coll-of-spec/big-int-coll [])))))

(s/def :cat-spec/kw (s/cat :k keyword? :v any?))
(deftest cat-spec
  (testing "too few elements"
    (is (= "-- Syntax error -------------------

  []

should have additional elements. The next element is named `:k` and satisfies

  keyword?

-- Relevant specs -------

:cat-spec/kw:
  (cljs.spec.alpha/cat :k cljs.core/keyword? :v cljs.core/any?)

-------------------------
Detected 1 error"
           (e.s/pretty-explain-str :cat-spec/kw [])))
    (is (= "-- Syntax error -------------------

  [:foo]

should have additional elements. The next element is named `:v` and satisfies

  any?

-- Relevant specs -------

:cat-spec/kw:
  (cljs.spec.alpha/cat :k cljs.core/keyword? :v cljs.core/any?)

-------------------------
Detected 1 error"
           (e.s/pretty-explain-str :cat-spec/kw [:foo]))))
  (testing "too many elements"
    (is (= "-- Syntax error -------------------

Value has extra input

  [... ... :bar ...]
           ^^^^

-- Relevant specs -------

:cat-spec/kw:
  (cljs.spec.alpha/cat :k cljs.core/keyword? :v cljs.core/any?)

-------------------------
Detected 1 error"
           (e.s/pretty-explain-str :cat-spec/kw [:foo 1 :bar :baz])))))

(s/def :keys-spec/name string?)
(s/def :keys-spec/age int?)
(s/def :keys-spec/user (s/keys :req [:keys-spec/name]
                               :req-un [:keys-spec/age]))
(deftest keys-spec
  (testing "missing keys"
    (is (= "-- Spec failed --------------------

  {}

should contain keys: `:keys-spec/name`,`:age`

-- Relevant specs -------

:keys-spec/user:
  (cljs.spec.alpha/keys :req [:keys-spec/name] :req-un [:keys-spec/age])

-------------------------
Detected 1 error"
           (e.s/pretty-explain-str :keys-spec/user {}))))
  (testing "invalid key"
    (is (= "-- Spec failed --------------------

  {:age ..., :keys-spec/name :bob}
                             ^^^^

should satisfy

  string?

-- Relevant specs -------

:keys-spec/name:
  cljs.core/string?
:keys-spec/user:
  (cljs.spec.alpha/keys :req [:keys-spec/name] :req-un [:keys-spec/age])

-------------------------
Detected 1 error"
           (e.s/pretty-explain-str :keys-spec/user {:age 1 :keys-spec/name :bob})))))

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
         "-- Missing spec -------------------

Cannot find spec for

   {}

 Spec multimethod:      `expound.spec-test/el-type`
 Dispatch function:     `:multi-spec/el-type`
 Dispatch value:        `nil`


-- Relevant specs -------

:multi-spec/el:
  (cljs.spec.alpha/multi-spec
   expound.spec-test/el-type
   :multi-spec/el-type)

-------------------------
Detected 1 error"
         (e.s/pretty-explain-str :multi-spec/el {}))))
  (testing "invalid dispatch value"
    (is (=
         "-- Missing spec -------------------

Cannot find spec for

   {:multi-spec/el-type :image}

 Spec multimethod:      `expound.spec-test/el-type`
 Dispatch function:     `:multi-spec/el-type`
 Dispatch value:        `:image`


-- Relevant specs -------

:multi-spec/el:
  (cljs.spec.alpha/multi-spec
   expound.spec-test/el-type
   :multi-spec/el-type)

-------------------------
Detected 1 error"
         (e.s/pretty-explain-str :multi-spec/el {:multi-spec/el-type :image}))))

  (testing "valid dispatch value, but other error"
    (is (=
         "-- Spec failed --------------------

  {:multi-spec/el-type :text}

should contain keys: `:multi-spec/value`

-- Relevant specs -------

:multi-spec/el:
  (cljs.spec.alpha/multi-spec
   expound.spec-test/el-type
   :multi-spec/el-type)

-------------------------
Detected 1 error"
         (e.s/pretty-explain-str :multi-spec/el {:multi-spec/el-type :text})))))

(s/def :recursive-spec/tag #{:text :group})
(s/def :recursive-spec/on-tap (s/coll-of map? :kind vector?))
(s/def :recursive-spec/props (s/keys :opt-un [:recursive-spec/on-tap]))
(s/def :recursive-spec/el (s/keys :req-un [:recursive-spec/tag]
                                  :opt-un [:recursive-spec/props :recursive-spec/children]))
(s/def :recursive-spec/children (s/coll-of (s/nilable :recursive-spec/el) :kind vector?))

(deftest recursive-spec
  (testing "only shows problem with data at 'leaves' (not problems with all parents in tree)"
    (is (= "-- Spec failed --------------------

  {:tag ...,
   :children
   [{:tag ...,
     :children [{:tag ..., :props {:on-tap {}}}]}]}
                                           ^^

should satisfy

  vector?

-- Relevant specs -------

:recursive-spec/on-tap:
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
   [:recursive-spec/props :recursive-spec/children])

-------------------------
Detected 1 error"
           (e.s/pretty-explain-str
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
  (is (= "-- Spec failed --------------------

  {\"foo\" \"hi\"}

should contain keys: `:cat-wrapped-in-or-spec/type`

-- Relevant specs -------

:cat-wrapped-in-or-spec/kv-or-string:
  (cljs.spec.alpha/or
   :map
   (cljs.spec.alpha/keys :req [:cat-wrapped-in-or-spec/type])
   :kv
   :cat-wrapped-in-or-spec/kv)

-- Spec failed --------------------

  {\"foo\" \"hi\"}

should satisfy

  sequential?

-- Relevant specs -------

:cat-wrapped-in-or-spec/kv:
  (cljs.spec.alpha/and
   cljs.core/sequential?
   (cljs.spec.alpha/cat :k cljs.core/keyword? :v cljs.core/any?))
:cat-wrapped-in-or-spec/kv-or-string:
  (cljs.spec.alpha/or
   :map
   (cljs.spec.alpha/keys :req [:cat-wrapped-in-or-spec/type])
   :kv
   :cat-wrapped-in-or-spec/kv)

-------------------------
Detected 2 errors"
         (e.s/pretty-explain-str :cat-wrapped-in-or-spec/kv-or-string {"foo" "hi"}))))

(s/def :map-of-spec/name string?)
(s/def :map-of-spec/age pos-int?)
(s/def :map-of-spec/name->age (s/map-of :map-of-spec/name :map-of-spec/age))
(deftest map-of-spec
  (is (= "-- Spec failed --------------------

  {\"Sally\" \"30\"}
           ^^^^

should satisfy

  pos-int?

-- Relevant specs -------

:map-of-spec/age:
  cljs.core/pos-int?
:map-of-spec/name->age:
  (cljs.spec.alpha/map-of :map-of-spec/name :map-of-spec/age)

-------------------------
Detected 1 error"
         (e.s/pretty-explain-str :map-of-spec/name->age {"Sally" "30"})))
  (is (= "-- Spec failed --------------------

  {:sally ...}
   ^^^^^^

should satisfy

  string?

-- Relevant specs -------

:map-of-spec/name:
  cljs.core/string?
:map-of-spec/name->age:
  (cljs.spec.alpha/map-of :map-of-spec/name :map-of-spec/age)

-------------------------
Detected 1 error"
         (e.s/pretty-explain-str :map-of-spec/name->age {:sally 30}))))

(s/def :specs.coll-of/into #{[] '() #{}})
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
(s/def :specs/string string?)
(s/def :specs/vector vector?)
(s/def :specs/int int?)
(s/def :specs/boolean boolean?)
(s/def :specs/keyword keyword?)
(s/def :specs/map map?)
(s/def :specs/symbol symbol?)
(s/def :specs/pos-int pos-int?)
(s/def :specs/neg-int neg-int?)
(s/def :specs/zero zero?)

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
                                      :specs/zero])
                       (gen/set gen/simple-type-printable)]))

(deftest generated-simple-spec
  (checking
   "simple spec"
   30
   [simple-spec simple-spec-gen
    :let [sp-form (s/form simple-spec)]
    form gen/any-printable]
   (e.s/pretty-explain-str simple-spec form)))

(deftest generated-coll-of-specs
  (checking
   "'coll-of' spec"
   30
   [simple-spec simple-spec-gen
    every-args (s/gen :specs/every-args)
    :let [spec (apply-coll-of simple-spec every-args)]
    :let [sp-form (s/form spec)]
    form gen/any-printable]
   (e.s/pretty-explain-str spec form)))

(deftest generated-and-specs
  (checking
   "'and' spec"
   30
   [simple-spec1 simple-spec-gen
    simple-spec2 simple-spec-gen
    :let [spec (s/and simple-spec1 simple-spec2)]
    :let [sp-form (s/form spec)]
    form gen/any-printable]
   (e.s/pretty-explain-str spec form)))

(deftest generated-or-specs
  (checking
   "'or' spec"
   30
   [simple-spec1 simple-spec-gen
    simple-spec2 simple-spec-gen
    :let [spec (s/or :or1 simple-spec1 :or2 simple-spec2)]
    :let [sp-form (s/form spec)]
    form gen/any-printable]
   (e.s/pretty-explain-str spec form)))

(deftest generated-map-of-specs
  (checking
   "'map-of' spec"
   25
   [simple-spec1 simple-spec-gen
    simple-spec2 simple-spec-gen
    every-args (s/gen :specs/every-args)
    :let [spec (apply-map-of simple-spec1 simple-spec2 every-args)
          sp-form (s/form spec)]
    form any-printable-wo-nan]
   (e.s/pretty-explain-str spec form)))

;; TODO - keys
;; TODO - cat + alt, + ? *
;; TODO - nilable
;; TODO - test coll-of that is a set . can i should a bad element of a set?

(deftest compare-paths-test
  (checking
   "path to a key comes before a path to a value"
   10
   [m (gen/map gen/simple-type-printable gen/simple-type-printable)
    k gen/simple-type-printable]
   (is (= -1 (e.s/compare-paths [(e.s/->KeyPathSegment k)] [k])))
   (is (= 1 (e.s/compare-paths [k] [(e.s/->KeyPathSegment k)])))))
