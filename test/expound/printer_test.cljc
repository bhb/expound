(ns expound.printer-test
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [expound.alpha :as expound]
            [expound.printer :as printer]))

(deftest pprint-fn
  (is (= "string?"
         (printer/pprint-fn (::s/spec (s/explain-data string? 1)))))
  (is (=
       "expound.alpha/expound"
       (printer/pprint-fn expound/expound))))

