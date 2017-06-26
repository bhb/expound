(ns expound.test-runner
  (:require [jx.reporter.karma :refer-macros [run-tests run-all-tests]]
            [expound.spec-test]
            [expound.test-utils]))

(enable-console-print!)

;; runs all tests in all namespaces
;; This is what runs by default
(defn ^:export run-all [karma]
  (run-all-tests karma))

;; runs all tests in all namespaces - only namespaces with names matching
;; the regular expression will be tested
;; You can use this by changing client.args in karma.conf.js
(defn ^:export run-all-regex [karma]
  (run-all-tests karma #".*-test$"))

;; runs all tests in the given namespaces
;; You can use this by changing client.args in karma.conf.js
(defn ^:export run [karma]
  (run-tests karma 'expound.spec-test))


(comment
  "-- Spec failed --------------------\n\n  {:tag ...,\n   :children\n   [{:tag ...,\n     :children\n     [{:tag ..., :props {:on-tap {}}}]}]}\n
                     ^^\n\nshould satisfy\n\n  vector?\n\n-- Relevant specs -------\n\n:recursive-spec/on-tap:\n  (cljs.spec.alpha/coll-of cljs.core/map? :kind cljs.core/vector?)\n:
recursive-spec/props:\n  (cljs.spec.alpha/keys :opt-un [:recursive-spec/on-tap])\n:recursive-spec/children:\n  (cljs.spec.alpha/coll-of\n   (cljs.spec.alpha/nilable :recursive-spec/
el)\n   :kind\n   cljs.core/vector?)\n:recursive-spec/el:\n  (cljs.spec.alpha/keys\n   :req-un\n   [:recursive-spec/tag]\n   :opt-un\n   [:recursive-spec/props :recursive-spec/child
ren])\n\n-------------------------\nDetected 1 error"

  (println "-- Spec failed --------------------\n\n  {:tag ...,\n   :children\n   [{:tag ...,\n     :children [{:tag ..., :props {:on-tap {}}}]}]}\n                                           ^^\n\nshould satisfy\n\n  vector?\n\n-- Relevant specs -------\n\n:recursive-spec/on-tap:\n  (cljs.spec.alpha/coll-of cljs.core/
map? :kind cljs.core/vector?)\n:recursive-spec/props:\n  (cljs.spec.alpha/keys :opt-un [:recursive-spec/on-tap])\n:recursive-spec/children:\n  (cljs.spec.alpha/coll-of\n   (cljs.spe
c.alpha/nilable :recursive-spec/el)\n   :kind\n   cljs.core/vector?)\n:recursive-spec/el:\n  (cljs.spec.alpha/keys\n   :req-un\n   [:recursive-spec/tag]\n   :opt-un\n   [:recursive-
spec/props :recursive-spec/children])\n\n-------------------------\nDetected 1 error")
  )
