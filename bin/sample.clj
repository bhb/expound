;; Usage: clj bin/sample.clj

(ns expound.sample
  "Tests are great, but sometimes just skimming output is useful
  for seeting how output appears in practice"
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as expound]))

(defn go []
  (set! s/*explain-out* (expound/custom-printer {:color-theme :dark-screen-theme}))

  (s/explain string? 1)

  (s/def :simple-type-based-spec/str string?)

  (s/explain :simple-type-based-spec/str "")

  (s/def :set-based-spec/tag #{:foo :bar})
  (s/def :set-based-spec/nilable-tag (s/nilable :set-based-spec/tag))
  (s/def :set-based-spec/set-of-one #{:foobar})

  (s/def :set-based-spec/one-or-two (s/or
                                     :one (s/cat :a #{:one})
                                     :two (s/cat :b #{:two})))

  (s/explain :set-based-spec/tag :baz)
  (s/explain :set-based-spec/one-or-two [:three])
  (s/explain :set-based-spec/nilable-tag :baz)
  (s/explain :set-based-spec/set-of-one :baz))

(go)

