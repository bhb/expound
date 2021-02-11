(ns bb
  (:require [babashka.deps :as deps]))

(deps/add-deps
 '{:deps {borkdude/spartan.spec {:git/url "https://github.com/borkdude/spartan.spec"
                                 :sha "d3b4e98ec2b8504868e5a6193515c5d23df15264"}
          expound/expound {:local/root "."}}})

(require 'spartan.spec
         '[clojure.spec.alpha :as s]
         '[expound.alpha :as expound])

(expound/expound int? "1")
