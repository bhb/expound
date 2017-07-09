(ns expound.test-utils
  (:require [cljs.spec.alpha :as s]
            [cljs.test :as ct]
            [com.gfredericks.test.chuck.clojure-test :as chuck]))

;; test.chuck defines a reporter for the shrunk results, but only for the
;; default reporter (:cljs.test/default). Since karma uses its own reporter,
;; we need to provide an implementation of the report multimethod for
;; the karma reporter and shrunk results

(defmethod ct/report [:jx.reporter.karma/karma ::chuck/shrunk] [m]
  (let [f (get (methods ct/report) [::ct/default ::chuck/shrunk])]
    (f m)))

(def check-spec-assertions
  {:before #(s/check-asserts true)
   :after #(s/check-asserts false)})

(defn nan? [x]
  (and (number? x)
       (js/isNaN x)))

(defn contains-nan? [x]
  (boolean (some nan? (tree-seq coll? identity x))))
