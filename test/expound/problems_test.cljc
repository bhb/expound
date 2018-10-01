(ns expound.problems-test
  (:require [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [clojure.spec.alpha :as s]
            [expound.problems :as problems]
            [expound.test-utils :as test-utils]
            [clojure.string :as string]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck :as chuck]
            [expound.paths :as paths] ;; TODO - remove
))

(def num-tests 100)

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

(deftest highlighted-value-on-alt
  (is (= "[... 0]\n     ^"
         (problems/highlighted-value
          {}
          (first
           (:expound/problems
            (problems/annotate
             (s/explain-data
              (clojure.spec.alpha/alt :a int?
                                      :b (clojure.spec.alpha/spec (clojure.spec.alpha/cat :c int?)))
              [1 0]))))))))

(deftest highlighted-value-on-coll-of
  ;; sets
  (is (= "#{1 3 2 :a}\n        ^^"
         (problems/highlighted-value
          {}
          (first
           (:expound/problems
            (problems/annotate
             (s/explain-data
              (s/coll-of integer?)
              #{1 :a 2 3})))))))
  (is (= "#{:a}\n  ^^"
         (problems/highlighted-value
          {}
          (first
           (:expound/problems
            (problems/annotate
             (s/explain-data
              (s/coll-of integer?)
              #{:a})))))))

  ;; lists
  (is (= "(... :a ... ...)\n     ^^"
         (problems/highlighted-value
          {}
          (first
           (:expound/problems
            (problems/annotate
             (s/explain-data
              (s/coll-of integer?)
              '(1 :a 2 3))))))))
  (is (= "(:a)\n ^^"
         (problems/highlighted-value
          {}
          (first
           (:expound/problems
            (problems/annotate
             (s/explain-data
              (s/coll-of integer?)
              '(:a))))))))

  ;; vectors
  (is (= "[... :a ... ...]\n     ^^"
         (problems/highlighted-value
          {}
          (first
           (:expound/problems
            (problems/annotate
             (s/explain-data
              (s/coll-of integer?)
              [1 :a 2 3])))))))

  (is (= "[:a]\n ^^"
         (problems/highlighted-value
          {}
          (first
           (:expound/problems
            (problems/annotate
             (s/explain-data
              (s/coll-of integer?)
              [:a])))))))

    ;; maps
  (is (= "[1 :a]\n^^^^^^"
         (problems/highlighted-value
          {}
          (first
           (:expound/problems
            (problems/annotate
             (s/explain-data
              (s/coll-of integer?)
              {1 :a 2 3})))))))

  (is (= "[:a 1]\n^^^^^^"
         (problems/highlighted-value
          {}
          (first
           (:expound/problems
            (problems/annotate
             (s/explain-data
              (s/coll-of integer?)
              {:a 1}))))))))

(s/def :annotate-test/div-fn (s/fspec
                              :args (s/cat :x int? :y pos-int?)))
(defn my-div [x y]
  (assert (pos? (/ x y))))

(deftest annotate-test
  (is (= {:expound/in [0]
          :val '(0 1)
          :reason "Assert failed: (pos? (/ x y))"}
         (-> (s/explain-data (s/coll-of :annotate-test/div-fn) [my-div])
             problems/annotate
             :expound/problems
             first
             (select-keys [:expound/in :val :reason])))))

;; 1.9.562 doesn't implement map-entry?
(when-not #?(:clj false :cljs (= *clojurescript-version* "1.9.562"))
  (defn nth-value [form i]
    (let [seq (remove map-entry? (tree-seq coll? seq form))]
      (nth seq (mod i (count seq))))))

(when-not #?(:clj false :cljs (= *clojurescript-version* "1.9.562"))
  ;; TODO - move to paths
  (deftest paths-to-value-test
    (checking
     "value-in is inverse of paths-to-value"
     (chuck/times num-tests)
     [form test-utils/any-printable-wo-nan
      i gen/pos-int
      :let [x (nth-value form i)
            paths (paths/paths-to-value form x [] [])]]
     (is (not (empty? paths)))
     (doseq [path paths]
       (is (= x
              (problems/value-in form
                                 path)))))))

