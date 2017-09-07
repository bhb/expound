(ns expound.problems-test
  (:require [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [clojure.spec.alpha :as s]
            [expound.problems :as problems]
            [expound.test-utils :as test-utils]
            [clojure.string :as string]))

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

(s/def :spec-with-conformer/a-b-seq (s/cat :a #{\A} :b #{\B}))
(s/def :spec-with-confomer/a-b-str (s/and
                                    string?
                                    (s/conformer seq)
                                    :spec-with-conformer/a-b-seq))
#_(deftest highlighted-value
  (testing "spec with seq conformer"
    (is (= :foobar
           (problem/highlighted-value
            (first
             (problems/annotate
              (s/explain-data :spec-with-conformer/a-b-seq "ac"))))
           )))
  )
