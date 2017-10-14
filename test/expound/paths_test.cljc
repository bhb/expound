(ns expound.paths-test
  (:require [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [expound.paths :as paths]
            [expound.test-utils :as test-utils]))

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

(deftest compare-paths-test
  (checking
   "path to a key comes before a path to a value"
   10
   [m (gen/map gen/simple-type-printable gen/simple-type-printable)
    k gen/simple-type-printable]
   (is (= -1 (paths/compare-paths [(paths/->KeyPathSegment k)] [k])))
   (is (= 1 (paths/compare-paths [k] [(paths/->KeyPathSegment k)])))))

