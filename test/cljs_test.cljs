;; https://github.com/bhb/expound/issues/183
;; is easy to miss because if any of the dev/test dependencies
;; include 'goog.string.format', the tests will happen to pass, but
;; the bug can still occur for clients.
;;
;; To avoid this issue, I added this test that includes no other libraries.

(require '[clojure.spec.alpha :as s])
(require '[expound.alpha :as expound])

(s/def :example.place/city string?)
(s/def :example.place/state string?)
(s/def :example/place (s/keys :req-un [:example.place/city :example.place/state]))

(expound/expound :example/place {:city "Denver", :state :CO} {:print-specs? false})
