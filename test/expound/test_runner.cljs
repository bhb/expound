(ns expound.test-runner
  (:require [jx.reporter.karma :refer-macros [run-tests run-all-tests]]
            [expound.alpha-test]
            [expound.alpha.paths-test]
            [expound.alpha.printer-test]
            [expound.alpha.problems-test]
            [expound.test-utils]
            [expound.specs-test]
            [expound.spell-spec-test]))

(enable-console-print!)

;; runs all tests in all namespaces
;; This is what runs by default
(defn ^:export run-all [karma]
  (jx.reporter.karma/run-all-tests karma))

;; runs all tests in all namespaces - only namespaces with names matching
;; the regular expression will be tested
;; You can use this by changing client.args in karma.conf.js
#_(defn ^:export run-all-regex [karma]
    (run-all-tests karma #".*-test$"))

;; runs all tests in the given namespaces
;; You can use this by changing client.args in karma.conf.js
#_(defn ^:export run [karma]
    (run-tests karma 'expound.alpha-test))
