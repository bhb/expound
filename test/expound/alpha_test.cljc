(ns expound.alpha-test
  (:require [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [clojure.test.check.generators :as gen]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [expound.alpha :as expound]
            [expound.test-utils :as test-utils]
            [clojure.string :as string]
            #?(:clj [orchestra.spec.test :as orch.st]
               :cljs [orchestra-cljs.spec.test :as orch.st])))

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

(def any-printable-wo-nan (gen/such-that (complement test-utils/contains-nan?) gen/any-printable))

(comment
  (require '[clojure.string :as string])
  )

(defn pf
  "Fixes platform-specific namespaces and also formats using printf syntax"
  [s & args]
  (apply expound/format
         #?(:cljs (string/replace s "pf." "cljs.")
            :clj (string/replace s "pf." "clojure."))
         args))

(defn get-args [& args] args)
(deftest highlighted-value
  (testing "atomic value"
    (is (= "\"Fred\"\n^^^^^^"
           (expound/highlighted-value
            {}
            "Fred"
            []))))
  (testing "value in vector"
    (is (= "[... :b ...]\n     ^^"
           (expound/highlighted-value
            {}
            [:a :b :c]
            [1]))))
  (testing "long, composite values are pretty-printed"
    (is (= (str "{:letters {:a \"aaaaaaaa\",
           :b \"bbbbbbbb\",
           :c \"cccccccd\",
           :d \"dddddddd\",
           :e \"eeeeeeee\"}}"
                #?(:clj  "\n          ^^^^^^^^^^^^^^^"
                   :cljs "\n          ^^^^^^^^^^^^^^^^"))
           ;; ^- the above works in clojure - maybe not CLJS?
           (expound/highlighted-value
            {}
            {:letters
             {:a "aaaaaaaa"
              :b "bbbbbbbb"
              :c "cccccccd"
              :d "dddddddd"
              :e "eeeeeeee"}}
            [:letters]))))
  (testing "args to function"
    (is (= "(1 ... ...)\n ^"
           (expound/highlighted-value
            {}
            (get-args 1 2 3)
            [0]))))
  (testing "show all values"
    (is (= "(1 2 3)\n ^"
           (expound/highlighted-value
            {:show-valid-values? true}
            (get-args 1 2 3)
            [0])))))

;; https://github.com/bhb/expound/issues/8
(deftest expound-output-ends-in-newline
  (is (= "\n" (str (last (expound/expound-str string? 1)))))
  (is (= "\n" (str (last (expound/expound-str string? ""))))))

(deftest expound-prints-expound-str
  (is (=
       (expound/expound-str string? 1)
       (with-out-str (expound/expound string? 1)))))

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

(deftest set-based-spec
  (testing "prints valid options"
    (is (= "-- Spec failed --------------------

  :baz

should be one of: `:bar`,`:foo`

-- Relevant specs -------

:set-based-spec/tag:
  #{:bar :foo}

-------------------------
Detected 1 error\n"
           (expound/expound-str :set-based-spec/tag :baz))))
  ;; FIXME - we should fix nilable and or specs better so they are clearly grouped
  (testing "nilable version"
    (is (= (pf "-- Spec failed --------------------

  :baz

should be one of: `:bar`,`:foo`

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
           (expound/expound-str :set-based-spec/nilable-tag :baz)))))

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
           (expound/expound-str :or-spec/vals [0 "hi" :kw "bye"])))))

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
                  :clj "(fn [%] (pos? (count %)))")
               )
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
                :clj "(fn [%] (pos? (count %)))")
             )
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
                :clj "Integer/MAX_VALUE")
             )
         (expound/expound-str :coll-of-spec/big-int-coll [])))))

(s/def :cat-spec/kw (s/cat :k keyword? :v any?))
(deftest cat-spec
  (testing "too few elements"
    (is (= (pf "-- Syntax error -------------------

  []

should have additional elements. The next element is named `:k` and satisfies

  keyword?

-- Relevant specs -------

:cat-spec/kw:
  (pf.spec.alpha/cat :k pf.core/keyword? :v pf.core/any?)

-------------------------
Detected 1 error\n")
           (expound/expound-str :cat-spec/kw [])))
    (is (= (pf "-- Syntax error -------------------

  [:foo]

should have additional elements. The next element is named `:v` and satisfies

  any?

-- Relevant specs -------

:cat-spec/kw:
  (pf.spec.alpha/cat :k pf.core/keyword? :v pf.core/any?)

-------------------------
Detected 1 error\n")
           (expound/expound-str :cat-spec/kw [:foo]))))
  (testing "too many elements"
    (is (= (pf "-- Syntax error -------------------

Value has extra input

  [... ... :bar ...]
           ^^^^

-- Relevant specs -------

:cat-spec/kw:
  (pf.spec.alpha/cat :k pf.core/keyword? :v pf.core/any?)

-------------------------
Detected 1 error\n")
           (expound/expound-str :cat-spec/kw [:foo 1 :bar :baz])))))

(comment
  "-- Syntax error -------------------\n\nValue has extra input\n\n  [... ... :bar ...]\n           ^^^^\n\n-- Relevant specs -------\n\n:cat-spec/kw:\n  (clojure.spec.alpha/cat :k clojure.core/keyword? :v clojure.core/any?)\n\n-------------------------\nDetected 1 error\n"
  "-- Syntax error -------------------\n\nValue has extra input\n\n  [:foo 1 :bar :baz]\n          ^^^^\n\n-- Relevant specs -------\n\n:cat-spec/kw:\n  (clojure.spec.alpha/cat :k clojure.core/keyword? :v clojure.core/any?)\n\n-------------------------\nDetected 1 error\n"
  )

(s/def :keys-spec/name string?)
(s/def :keys-spec/age int?)
(s/def :keys-spec/user (s/keys :req [:keys-spec/name]
                               :req-un [:keys-spec/age]))
(deftest keys-spec
  (testing "missing keys"
    (is (= (pf "-- Spec failed --------------------

  {}

should contain keys: `:keys-spec/name`,`:age`

-- Relevant specs -------

:keys-spec/user:
  %s

-------------------------
Detected 1 error\n"
               #?(:cljs "(cljs.spec.alpha/keys :req [:keys-spec/name] :req-un [:keys-spec/age])"
                  :clj "(clojure.spec.alpha/keys\n   :req\n   [:keys-spec/name]\n   :req-un\n   [:keys-spec/age])"
                  )
               )
           (expound/expound-str :keys-spec/user {}))))
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

should contain keys: `:multi-spec/value`

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
     :children [{:tag ..., :props {:on-tap {}}}]}]}
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

should contain keys: `:cat-wrapped-in-or-spec/type`

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
;; TODO - force a specific type of into/kind one for each test
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
(s/def :specs/string string?)
(s/def :specs/vector vector?)
(s/def :specs/int int?)
(s/def :specs/boolean boolean?)
(s/def :specs/keyword keyword?)
(s/def :specs/map map?)
(s/def :specs/symbol symbol?)
(s/def :specs/pos-int pos-int?)
(s/def :specs/neg-int neg-int?)
(s/def :specs/zero #(and (number? %) (zero? %)))

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
   (expound/expound-str simple-spec form)))

#_(deftest generated-coll-of-specs
    (checking
     "'coll-of' spec"
     30
     [simple-spec simple-spec-gen
      every-args (s/gen :specs/every-args)
      :let [spec (apply-coll-of simple-spec every-args)]
      :let [sp-form (s/form spec)]
      form gen/any-printable]
     (expound/expound-str spec form)))

#_(deftest generated-and-specs
    (checking
     "'and' spec"
     30
     [simple-spec1 simple-spec-gen
      simple-spec2 simple-spec-gen
      :let [spec (s/and simple-spec1 simple-spec2)]
      :let [sp-form (s/form spec)]
      form gen/any-printable]
     (expound/expound-str spec form)))

#_(deftest generated-or-specs
    (checking
     "'or' spec"
     30
     [simple-spec1 simple-spec-gen
      simple-spec2 simple-spec-gen
      :let [spec (s/or :or1 simple-spec1 :or2 simple-spec2)]
      :let [sp-form (s/form spec)]
      form gen/any-printable]
     (expound/expound-str spec form)))

;; TODO - get these two tests running!
#_(deftest generated-map-of-specs
    (checking
     "'map-of' spec"
     25
     [simple-spec1 simple-spec-gen
      simple-spec2 simple-spec-gen
      every-args (s/gen :specs/every-args)
      :let [spec (apply-map-of simple-spec1 simple-spec2 every-args)
            sp-form (s/form spec)]
      form any-printable-wo-nan]
     (expound/expound-str spec form)))

;; TODO - keys
;; TODO - cat + alt, + ? *
;; TODO - nilable
;; TODO - test coll-of that is a set . can i should a bad element of a set?

#_(deftest compare-paths-test
    (checking
     "path to a key comes before a path to a value"
     10
     [m (gen/map gen/simple-type-printable gen/simple-type-printable)
      k gen/simple-type-printable]
     (is (= -1 (expound/compare-paths [(expound/->KeyPathSegment k)] [k])))
     (is (= 1 (expound/compare-paths [k] [(expound/->KeyPathSegment k)])))))

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

:test-assert/name:
  pf.core/string?

-------------------------
Detected 1 error\n")
         (binding [s/*explain-out* expound/printer]
           (s/explain-str :test-assert/name :hello)))))


(s/def :test-instrument/name string?)
(s/fdef test-instrument-adder
        :args (s/cat :x int? :y int?)
        :fn #(> (:ret %) (-> % :args :x))
        :ret pos-int?)
(defn test-instrument-adder [x y]
  (+ x y))

(defn no-linum [s]
  (string/replace s #".cljc:\d+" ".cljc:LINUM"))

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

should have additional elements. The next element is named `:y` and satisfies

  int?



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

should have additional elements. The next element is named `:y` and satisfies

  int?



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



-------------------------
Detected 1 error\n"
            (no-linum
             (:cause
               (Throwable->map (try
                                 (binding [s/*explain-out* (expound/custom-printer {:show-valid-values? true})]
                                   (test-instrument-adder "" :x))
                                 (catch Exception e e))))))))
  (st/unstrument `test-instrument-adder))

;; TODO - clojurescript tests
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


(comment
(require '[expound.alpha :as expound])
(require '[clojure.spec.alpha :as s])

(s/def :example.place/city string?)
(s/def :example.place/state string?)
(s/def :example/place (s/keys :req-un [:example.place/city :example.place/state]))

(set! s/*explain-out* expound/printer)
(s/explain :example/place {:city "Denver" :state :CO :country "USA"})

;; -- Spec failed --------------------
;;
;;  {:city ..., :state :CO, :country ...}
;;                     ^^^
;;
;;should satisfy
;;
;;  string?

;;;;;;
;; You can configure Expound to show valid values:
;;;;;

(set! s/*explain-out* (expound/custom-printer {:show-valid-values? true}))
(s/explain :example/place {:city "Denver" :state :CO :country "USA"})

;; -- Spec failed --------------------
;;
;; {:city "Denver", :state :CO, :country "USA"}
;; ^^^
;;
;; should satisfy
;;
;;   string?

;;;; or even provide your own implementation of `value-str-fn` a function which
;; must match the following spec:

;;; TODO - ADD SPEC HERE

(defn my-value-str [_spec-name form path value]
  (str "In context: " (pr-str form) "\n"
       "Invalid value: " (pr-str value)))

(set! s/*explain-out* (expound/custom-printer {:value-str-fn my-value-str}))
(s/explain :example/place {:city "Denver" :state :CO :country "USA"})

;; -- Spec failed --------------------
;;
;;   In context: {:city "Denver", :state :CO, :country "USA"}
;;   Invalid value: :CO
;;
;; should satisfy
;;
;;   string?


  )

;; -- Spec failed --------------------

;;   In context: {:city "Denver", :state :CO}

;;   value: :CO

;; should satisfy

;;   string?

