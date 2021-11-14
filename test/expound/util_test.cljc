(ns expound.util-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :as ct :refer [deftest is use-fixtures]]
   [expound.test-utils :as test-utils]
   [expound.util :as util]))

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

(deftest test-spec-vals
  (s/def ::foo-pred (fn [_] true))
  (s/def ::foo-string string?)

  (s/def ::bar ::foo-pred)
  (s/def ::baz ::bar)

  (is (= (util/spec-vals ::bar)
         [::bar ::foo-pred '(clojure.core/fn [_] true)]))

  (s/def ::bar ::foo-string)
  (is (= (util/spec-vals ::bar)
         [::bar ::foo-string 'clojure.core/string?]))

  (is (= (util/spec-vals ::foo-string)
         [::foo-string 'clojure.core/string?]))

  (is (= (util/spec-vals ::lone)
         [::lone]))

  (s/def ::foo-pred nil)
  (s/def ::foo-string nil)
  (s/def ::bar nil)
  (s/def ::baz nil))
