(ns expound.suggest-test
  (:require [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [expound.test-utils :as test-utils]
            [clojure.spec.alpha :as s]
            [expound.suggest :as suggest]))

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

(s/fdef example-fn1
        :args (s/cat :a simple-symbol?
                     :b string?
                     :c simple-keyword?
                     :d int?
                     :e qualified-symbol?))
(defn example-fn1 [a b c d])

(s/fdef example-fn2
        :args (s/cat :a #{:foo :bar}
                     :b (s/int-in 50 100)))
(defn example-fn2 [a b])

(deftest suggest
  (testing "if spec matches"
    (is (= "a"
           (suggest/suggestion string? "a"))))
  (testing "conversions"
    (is (= 'b
           (suggest/suggestion simple-symbol? 'a/b)))
    (is (= "b"
           (suggest/suggestion string? 'b)))
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
    (is (= 0
           (suggest/suggestion int? "foo"))))
  (testing "simplification of existing options"
    (is (= :bar
           (suggest/suggestion #{:foo :bar} :baz)))
    (is (= 50
           (suggest/suggestion (s/int-in 50 60) 100)))
    (is (= #inst "2018-01-01"
           (suggest/suggestion (s/inst-in #inst "2018-01-01" #inst "2018-12-31")
                               #inst "2017-01-01"))))
  #_(testing "multiple problems in single spec"
      (is (= ["a" :b 0]
             (suggest/suggestion (s/cat
                                  :s string?
                                  :kw keyword?
                                  :i int?)
                                 [:a "b" "c"])))))

#?(:cljs
   (deftest valid-args
     (is (= ::suggest/no-spec-found
            (suggest/valid-args '(cljs.core/let1 [foo/bar 1]))))
     (is (= '(expound.suggest-test/example-fn1 b "b" :c 1 a/b)
            (suggest/valid-args `(example-fn1 a/b "b" :c 1 a/b))))) :clj
   (deftest valid-args
     (is (= ::suggest/no-spec-found
            (suggest/valid-args '(clojure.core/let1 [foo/bar 1]))))

     (is (=
          '(clojure.core/let [bar 1])
          (suggest/valid-args `(let [foo/bar 1]))))

     (is (=
          '(clojure.core/let [bar 1])
          (suggest/valid-args `(let ["bar" 1]))))))
