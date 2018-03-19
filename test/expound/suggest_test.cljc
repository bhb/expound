(ns expound.suggest-test
  (:require [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [expound.test-utils :as test-utils]
            [clojure.spec.alpha :as s]
            [expound.suggest :as suggest]))

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

(deftest suggest
  (testing "if spec matches"
    (is (= "a"
           (suggest/suggestion string? "a"))))
  (testing "conversions"
    (is (= 'b
           (suggest/suggestion simple-symbol? 'a/b)))
    (is (= 'symbol
           (suggest/suggestion simple-symbol? "a b")))
    (is (= "a"
           (suggest/suggestion string? 'a)))
    (is (= "b"
           (suggest/suggestion string? :b)))
    (is (= "b"
           (suggest/suggestion string? :a/b)))
    (is (= :c
           (suggest/suggestion simple-keyword? 'c)))
    (is (= :c
           (suggest/suggestion simple-keyword? :a/c)))
    (is (= :c
           (suggest/suggestion simple-keyword? "c")))
    (is (= :keyword
           (suggest/suggestion simple-keyword? "a b")))
    (is (= 0
           (suggest/suggestion int? "foo")))
    (is (= "abcd"
           (suggest/suggestion string? ["abcd"])))
    (is (= ["abcd"]
           (suggest/suggestion (s/coll-of string?) "abcd")))
    (is (= #{100}
           (suggest/suggestion (s/coll-of int? :kind set?) 100))))
  (testing "simplification of existing options"
    (is (= :bar
           (suggest/suggestion #{:foo :bar} :baz)))
    (is (= 50
           (suggest/suggestion (s/int-in 50 60) 100)))
    (is (<= -1
            (compare #inst "2018-01-01"
                     (suggest/suggestion (s/inst-in #inst "2018-01-01" #inst "2018-12-31")
                                         #inst "2017-01-01")))))

  (testing "multiple problems in single spec"
    (is (= ["a" :b 0]
           (suggest/suggestion (s/cat
                                :s string?
                                :kw keyword?
                                :i int?)
                               [:a "b" "c"]))))
  (testing "deleting an element"
    (is (= ["a" "b"]
           (suggest/suggestion (s/cat
                                :s1 string?
                                :s2 string?)
                               ["a" "b" "c"]))))
  (testing "adding an element"
    (is (= ["a" ""]
           (suggest/suggestion (s/cat
                                :s1 string?
                                :s2 string?)
                               ["a"])))))

(deftest suggest-different-spec-types
  (testing "colls"
    (is (= ["1"]
           (suggest/suggestion (s/coll-of string?) [1])))
    ;; TODO - this really should only have two elements
    (is (= ["" "" ""]
           (suggest/suggestion (s/coll-of string? :min-count 2) [])))
    ;; TODO - it would be better if "1" was in results
    (is (= ["" "" ""]
           (suggest/suggestion (s/coll-of string? :min-count 2) [1])))))

(s/fdef example-fn1
        :args (s/cat :a simple-symbol?
                     :b string?
                     :c int?))
(defn example-fn1 [a b c])

(s/fdef example-fn2
        :args (s/cat :a (s/and string?
                               #(re-matches #"^some string that will not match$" %))))
(defn example-fn2 [a])

#?(:cljs
   (deftest valid-args
     (is (= ::suggest/no-spec-found
            (suggest/valid-args '(clojure.core/fake-let [foo/bar 1]))))
     (is (= '(expound.suggest-test/example-fn1 a "b" 0)
            (suggest/valid-args '(expound.suggest-test/example-fn1 "a" "b" "c")))))
   :clj
   (deftest valid-args
     (is (= ::suggest/no-spec-found
            (suggest/valid-args '(clojure.core/fake-let [foo/bar 1]))))
     (is (= ::suggest/no-suggestion
            (suggest/valid-args '(expound.suggest-test/example-fn2 "a"))))

     (is (= '(expound.suggest-test/example-fn1 a "b" 0)
            (suggest/valid-args '(expound.suggest-test/example-fn1 "a" "b" "c"))))
     (is (=
          '(clojure.core/let [bar 1])
          (suggest/valid-args `(let [foo/bar 1]))))
     (is (=
          '(clojure.core/let [bar 1])
          (suggest/valid-args `(let ["bar" 1]))))))

;; From "Improving Clojure's Error Messages with Grammars"
;; https://youtu.be/kt4haSH2xcs?t=10m20s
#?(:clj
   (deftest defn-examples
     (is (= `(defn ~'foo [])
            (suggest/valid-args `(defn ~'foo))))

     (is (= `(defn ~'foo ([~'arg1] ~'arg2))
            (suggest/valid-args `(defn ~'foo (~'arg1 ~'arg2)))))

     (is (= `(defn ~'foo [~'arg1 ~'arg2] ~'arg2)
            (suggest/valid-args `(defn ~'foo (~'arg1 ~'arg2) ~'arg2))))

     (is (= `(defn ~'foo [~'a])
            (suggest/valid-args `(defn ~'foo ~'a))))

     (is (= `(defn ~'foo [~'a ~'b])
            (suggest/valid-args `(defn ~'foo ([~'a] 1) [~'a ~'b]))))

     (is (= `(defn ~'testname "bad docstring" [~'arg1 ~'arg2])
            (suggest/valid-args `(defn "bad docstring" ~'testname [~'arg1 ~'arg2]))))

     (is (= `(defn ~'a "asdf" ([~'a] 1) {:a :b})
            (suggest/valid-args `(defn ~'a "asdf" ([~'a] 1) {:a :b} ([] 1)))))))
