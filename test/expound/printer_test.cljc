(ns expound.printer-test
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [expound.alpha :as expound]
            [expound.printer :as printer]
            [expound.test-utils :as test-utils]))

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

(deftest pprint-fn
  (is (= "string?"
         (printer/pprint-fn (::s/spec (s/explain-data string? 1)))))
  (is (= "expound.alpha/expound"
         (printer/pprint-fn expound/expound)))
  (is (= "<anonymous function>"
         (printer/pprint-fn #(inc %))))
  (is (= "<anonymous function>"
         (printer/pprint-fn (constantly true))))
  (is (= "<anonymous function>"
         (printer/pprint-fn (comp vec str)))))

(s/def :print-spec-keys/field1 string?)
(s/def :print-spec-keys/field2 (s/coll-of :print-spec-keys/field1))
(s/def :print-spec-keys/field3 int?)
(s/def :print-spec-keys/key-spec (s/keys
                                  :req [:print-spec-keys/field1]
                                  :req-un [:print-spec-keys/field2]))
(s/def :print-spec-keys/key-spec2 (s/keys
                                   :req-un [(and
                                             :print-spec-keys/field1
                                             (or
                                              :print-spec-keys/field2
                                              :print-spec-keys/field3))]))

(defn copy-key [m k1 k2]
  (assoc m k2 (get m k1)))

(deftest print-spec-keys
  (is (=
       "|                     key |              spec |
|-------------------------+-------------------|
|                 :field2 | (coll-of string?) |
| :print-spec-keys/field1 |           string? |"
       (printer/print-spec-keys
        (map #(copy-key % :via :expound/via)
             (::s/problems
              (s/explain-data
               :print-spec-keys/key-spec
               {}))))))
  (is (= nil
         (printer/print-spec-keys
          (map #(copy-key % :via :expound/via)
               (::s/problems
                (s/explain-data
                 (s/keys
                  :req [:print-spec-keys/field1]
                  :req-un [:print-spec-keys/field2])
                 {}))))))

  (is (=
       "|                     key |    spec |
|-------------------------+---------|
| :print-spec-keys/field1 | string? |"
       (printer/print-spec-keys
        (map #(copy-key % :via :expound/via)
             (::s/problems
              (s/explain-data
               (s/keys
                :req [:print-spec-keys/field1]
                :req-un [:print-spec-keys/field2])
               {:field2 [""]}))))))

  (is (=
       "|                     key |              spec |
|-------------------------+-------------------|
| :print-spec-keys/field1 |           string? |
| :print-spec-keys/field2 | (coll-of string?) |"
       (printer/print-spec-keys
        (map #(copy-key % :via :expound/via)
             (::s/problems
              (s/explain-data
               (s/keys
                :req [:print-spec-keys/field1
                      :print-spec-keys/field2])
               {}))))))
  (is (= "|     key |              spec |
|---------+-------------------|
| :field1 |           string? |
| :field2 | (coll-of string?) |
| :field3 |              int? |"
         (printer/print-spec-keys
          (map #(copy-key % :via :expound/via)
               (::s/problems
                (s/explain-data
                 :print-spec-keys/key-spec2
                 {})))))))
