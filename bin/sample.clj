;; Usage: cd bin; clj sample.clj | less -R

(ns expound.sample
  "Tests are great, but sometimes just skimming output is useful
  for seeting how output appears in practice"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [expound.alpha :as expound]))

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
  (display-explain :set-based-spec/set-of-one :baz)

  (s/def :nested-type-based-spec/str string?)
  (s/def :nested-type-based-spec/strs (s/coll-of :nested-type-based-spec/str))

  (display-explain :nested-type-based-spec/strs ["one" "two" 33])

  (s/def :nested-type-based-spec-special-summary-string/int int?)
  (s/def :nested-type-based-spec-special-summary-string/ints (s/coll-of :nested-type-based-spec-special-summary-string/int))

  (display-explain :nested-type-based-spec-special-summary-string/ints [1 2 "..."])

  (s/def :or-spec/str-or-int (s/or :int int? :str string?))
  (s/def :or-spec/vals (s/coll-of :or-spec/str-or-int))

  (s/def :or-spec/str string?)
  (s/def :or-spec/int int?)
  (s/def :or-spec/m-with-str (s/keys :req [:or-spec/str]))
  (s/def :or-spec/m-with-int (s/keys :req [:or-spec/int]))
  (s/def :or-spec/m-with-str-or-int (s/or :m-with-str :or-spec/m-with-str
                                          :m-with-int :or-spec/m-with-int))

  (display-explain :or-spec/str-or-int :kw)
  (display-explain :or-spec/vals [0 "hi" :kw "bye"])
  (display-explain (s/or
                    :strs (s/coll-of string?)
                    :ints (s/coll-of int?))
                   50)

  (display-explain
   (s/or
    :letters #{"a" "b"}
    :ints #{1 2})
   50)

  (display-explain :or-spec/m-with-str-or-int {})

  (display-explain (s/or :m-with-str1 (s/keys :req [:or-spec/str])
                         :m-with-int2 (s/keys :req [:or-spec/str])) {}) (s/def :and-spec/name (s/and string? #(pos? (count %))))
  (s/def :and-spec/names (s/coll-of :and-spec/name))

  (display-explain :and-spec/name "")

  (display-explain :and-spec/names ["bob" "sally" "" 1])

  (s/def :coll-of-spec/big-int-coll (s/coll-of int? :min-count 10))

  (display-explain :coll-of-spec/big-int-coll [])

  (s/def :cat-spec/kw (s/cat :k keyword? :v any?))
  (s/def :cat-spec/set (s/cat :type #{:foo :bar} :str string?))
  (s/def :cat-spec/alt* (s/alt :s string? :i int?))
  (s/def :cat-spec/alt (s/+ :cat-spec/alt*))
  (s/def :cat-spec/alt-inline (s/+ (s/alt :s string? :i int?)))
  (s/def :cat-spec/any (s/cat :x (s/+ any?))) ;; Not a useful spec, but worth testing

  (display-explain :cat-spec/kw [])

  (display-explain :cat-spec/set [])

  (display-explain :cat-spec/alt [])

  (display-explain :cat-spec/alt-inline [])

  (display-explain :cat-spec/any [])

  (display-explain :cat-spec/kw [:foo 1 :bar :baz]))

(go)

