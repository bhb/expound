;; bb -cp "$(clojure -Abb -Spath)" -f bb.clj

;; The current reason this won't work is that we use defrecord internally so
;; bb is not compatible

;; https://github.com/borkdude/babashka#differences-with-clojure

(ns bb
  (:require [babashka.deps :as deps]
                        )
  )


(deps/add-deps
 '{:deps {borkdude/spartan.spec {:git/url "https://github.com/borkdude/spartan.spec"
                                 :sha "0babad3d29a548ce6527c8536d6f49d585c008b2"}
          expound/expound {:local/root ".."}}})

(require 'spartan.spec
         '[clojure.spec.alpha :as s]
         '[expound.alpha :as expound])


(expound/expound int? "1")



