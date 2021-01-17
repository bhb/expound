(ns expound
  (:require [babashka.deps :as deps]))

(deps/add-deps
 '{:deps {borkdude/spartan.spec {:git/url "https://github.com/borkdude/spartan.spec"
                                 :sha "bf4ace4a857c29cbcbb934f6a4035cfabe173ff1"}
          expound/expound {:local/root ".."}}})

;; Loading spartan.spec will create a namespace clojure.spec.alpha for compatibility:
(require 'spartan.spec)
(require '[clojure.spec.alpha :as s])

;; Expound expects some vars to be there, like `with-gen`. Spartan prints warnings that these are used, but doesn't implement them yet.
(binding [*err* (java.io.StringWriter.)]
  (require '[expound.alpha :as expound]))

(s/def ::a (s/cat :i int? :j string?))

(expound/expound ::a [1 2])
