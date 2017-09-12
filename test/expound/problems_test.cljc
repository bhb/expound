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
(deftest highlighted-value
  (testing "atomic value"
    (is (= "\"Fred\"\n^^^^^^"
           (problems/highlighted-value
            {}
            "Fred"
            []))))
  (testing "value in vector"
    (is (= "[... :b ...]\n     ^^"
           (problems/highlighted-value
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
           (problems/highlighted-value
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
           (problems/highlighted-value
            {}
            (get-args 1 2 3)
            [0]))))
  (testing "show all values"
    (is (= "(1 2 3)\n ^"
           (problems/highlighted-value
            {:show-valid-values? true}
            (get-args 1 2 3)
            [0])))))

(s/def :spec-with-conformer/a-b-seq (s/cat :a #{\A} :b #{\B}))
(s/def :spec-with-confomer/a-b-str (s/and
                                    string?
                                    (s/conformer seq)
                                    :spec-with-conformer/a-b-seq))
#_(deftest highlighted-value
  (testing "spec with seq conformer"
    (is (= :foobar
           (problems/annotate
            (s/explain-data :spec-with-conformer/a-b-seq "ac"))
           #_(problems/highlighted-value1
            {}
            (first
             (problems/annotate
              (s/explain-data :spec-with-conformer/a-b-seq "ac")))
            )
           )))
  )
