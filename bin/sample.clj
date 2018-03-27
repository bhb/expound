;; Usage: cd bin; clj sample.clj | less -R

(ns expound.sample
  "Tests are great, but sometimes just skimming output is useful
  for seeting how output appears in practice"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [expound.alpha :as expound]
            ))

(defmacro display-explain [spec value]
  `(do
    (println "=====" '(s/explain ~spec ~value) "=========")
    (s/explain ~spec ~value)
    (println "\n\n")))


(defn go []
  (set! s/*explain-out* (expound/custom-printer {:color-theme :dark-screen-theme}))
  (st/instrument)
  
  (display-explain string? 1)

  (s/def :simple-type-based-spec/str string?)

  (s/def :set-based-spec/tag #{:foo :bar})
  (s/def :set-based-spec/nilable-tag (s/nilable :set-based-spec/tag))
  (s/def :set-based-spec/set-of-one #{:foobar})

  (s/def :set-based-spec/one-or-two (s/or
                                     :one (s/cat :a #{:one})
                                     :two (s/cat :b #{:two})))

  (display-explain :set-based-spec/tag :baz)
  (display-explain :set-based-spec/one-or-two [:three])
  (display-explain :set-based-spec/nilable-tag :baz)
  (display-explain :set-based-spec/set-of-one :baz))

(go)

