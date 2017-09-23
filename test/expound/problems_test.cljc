(ns expound.problems-test
  (:require [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [clojure.spec.alpha :as s]
            [expound.problems :as problems]
            [expound.test-utils :as test-utils]
            [clojure.string :as string]))

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

(defn get-args [& args] args)

(s/def :highlighted-value/nested-map-of (s/map-of keyword? (s/map-of keyword? keyword?)))

(s/def :highlighted-value/city string?)
(s/def :highlighted-value/address (s/keys :req-un [:highlighted-value/city]))
(s/def :highlighted-value/house (s/keys :req-un [:highlighted-value/address]))

(deftest highlighted-value
  (testing "atomic value"
    (is (= "\"Fred\"\n^^^^^^"
           (problems/highlighted-value
            {}
            {:expound/form "Fred"
             :expound/in []}))))
  (testing "value in vector"
    (is (= "[... :b ...]\n     ^^"
           (problems/highlighted-value
            {}
            {:expound/form [:a :b :c]
             :expound/in [1]}))))
  (testing "long, composite values are pretty-printed"
    (is (= (str "{:letters {:a \"aaaaaaaa\",
           :b \"bbbbbbbb\",
           :c \"cccccccd\",
           :d \"dddddddd\",
           :e \"eeeeeeee\"}}"
                #?(:clj  "\n          ^^^^^^^^^^^^^^^"
                   :cljs "\n          ^^^^^^^^^^^^^^^^"))
           ;; ^- the above works in clojure - maybe not CLJS?
           (problems/highlighted-value
            {}
            {:expound/form
             {:letters
              {:a "aaaaaaaa"
               :b "bbbbbbbb"
               :c "cccccccd"
               :d "dddddddd"
               :e "eeeeeeee"}}
             :expound/in [:letters]}))))
  (testing "args to function"
    (is (= "(1 ... ...)\n ^"
           (problems/highlighted-value
            {}
            {:expound/form (get-args 1 2 3)
             :expound/in [0]}))))
  (testing "show all values"
    (is (= "(1 2 3)\n ^"
           (problems/highlighted-value
            {:show-valid-values? true}
            {:expound/form (get-args 1 2 3)
             :expound/in [0]}))))

  (testing "special replacement chars are not used"
    (is (= "\"$ $$ $1 $& $` $'\"\n^^^^^^^^^^^^^^^^^^"
           (problems/highlighted-value
            {}
            (first
             (:expound/problems
              (problems/annotate
               (s/explain-data keyword? "$ $$ $1 $& $` $'"))))))))

  (testing "nested map-of specs"
    (is (= "{:a {:b 1}}\n        ^"
           (problems/highlighted-value
            {}
            (first
             (:expound/problems
              (problems/annotate
               (s/explain-data :highlighted-value/nested-map-of {:a {:b 1}})))))))
    (is (= "{:a {\"a\" ...}}\n     ^^^"
           (problems/highlighted-value
            {}
            (first
             (:expound/problems
              (problems/annotate
               (s/explain-data :highlighted-value/nested-map-of {:a {"a" :b}})))))))
    (is (= "{1 ...}\n ^"
           (problems/highlighted-value
            {}
            (first
             (:expound/problems
              (problems/annotate
               (s/explain-data :highlighted-value/nested-map-of {1 {:a :b}}))))))))

  (testing "nested keys specs"
    (is (= "{:address {:city 1}}\n                 ^"
           (problems/highlighted-value
            {}
            (first
             (:expound/problems
              (problems/annotate
               (s/explain-data :highlighted-value/house {:address {:city 1}})))))))
    (is (= "{:address {\"city\" \"Denver\"}}\n          ^^^^^^^^^^^^^^^^^"
           (problems/highlighted-value
            {}
            (first
             (:expound/problems
              (problems/annotate
               (s/explain-data :highlighted-value/house {:address {"city" "Denver"}})))))))
    (is (= "{\"address\" {:city \"Denver\"}}\n^^^^^^^^^^^^^^^^^^^^^^^^^^^^"
           (problems/highlighted-value
            {}
            (first
             (:expound/problems
              (problems/annotate
               (s/explain-data :highlighted-value/house {"address" {:city "Denver"}})))))))))
