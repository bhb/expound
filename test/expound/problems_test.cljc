(ns expound.problems-test
  (:require [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [clojure.test.check.generators :as gen]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [expound.problems :as problems]
            [expound.test-utils :as test-utils]
            [clojure.string :as string]
            #?(:clj [orchestra.spec.test :as orch.st]
               :cljs [orchestra-cljs.spec.test :as orch.st])))

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

#_(s/def :add-in/map (s/map-of string? int?))
#_(deftest add-in
  (testing "adds path to map key"
    (is (= {}
           (problems/add-in (s/explain-data :add-in/map {:foo 2}))))))
