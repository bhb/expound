(ns expound.reg-test
  (:require #?(:cljs [expound.alpha :as expound :include-macros true]
               :clj [expound.alpha :as expound])
            [clojure.test :as ct :refer [is testing deftest use-fixtures]]))

;; This exposes https://github.com/bhb/expound/issues/123
#_(deftest def-with-message-test
    (expound/def ::foobar int? "should be an int")
    (is (= "should be an int"
           (expound/error-message ::foobar))))
