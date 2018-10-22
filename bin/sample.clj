;; Usage: cd bin; clj sample.clj --check-results | less -R
;; or
;;        cd bin; clj sample.clj | less -R

(ns expound.sample
  "Tests are great, but sometimes just skimming output is useful
  for seeting how output appears in practice"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [expound.alpha :as expound]
            [orchestra.spec.test :as orch.st]))

(defmacro display-explain [spec value]
  `(do
     (println "===== " '(s/explain ~spec ~value) " =========")
     (s/explain ~spec ~value)
     (println "\n\n")))

(defmacro display-try [form]
  `(try
     (println "===== " '~form " =========")
     ~form
     (catch Exception e#
       (println (.getMessage e#)))))

(defn go [check-results?]
  (set! s/*explain-out* (expound/custom-printer {:theme :figwheel-theme}))
  (st/instrument)
  (s/check-asserts true)

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

  (display-explain :cat-spec/kw [:foo 1 :bar :baz])

  (s/def :keys-spec/name string?)
  (s/def :keys-spec/age int?)
  (s/def :keys-spec/user (s/keys :req [:keys-spec/name]
                                 :req-un [:keys-spec/age]))

  (s/def :key-spec/state string?)
  (s/def :key-spec/city string?)
  (s/def :key-spec/zip pos-int?)

  (s/def :keys-spec/user2 (s/keys :req [(and :keys-spec/name
                                             :keys-spec/age)]
                                  :req-un [(or
                                            :key-spec/zip
                                            (and
                                             :key-spec/state
                                             :key-spec/city))]))

  (s/def :keys-spec/user3 (s/keys :req-un [(or
                                            :key-spec/zip
                                            (and
                                             :key-spec/state
                                             :key-spec/city))]))

  (display-explain :keys-spec/user {})

  (display-explain :keys-spec/user2 {})

  (display-explain :keys-spec/user3 {})
  (display-explain (s/keys :req-un [:keys-spec/name :keys-spec/age]) {})

  (display-explain :keys-spec/user {:age 1 :keys-spec/name :bob})

  (s/def :multi-spec/value string?)
  (s/def :multi-spec/children vector?)
  (defmulti el-type :multi-spec/el-type)
  (defmethod el-type :text [x]
    (s/keys :req [:multi-spec/value]))
  (defmethod el-type :group [x]
    (s/keys :req [:multi-spec/children]))
  (s/def :multi-spec/el (s/multi-spec el-type :multi-spec/el-type))

  (display-explain :multi-spec/el {})
  (display-explain :multi-spec/el {:multi-spec/el-type :image})

  (display-explain :multi-spec/el {:multi-spec/el-type :text})

  (s/def :recursive-spec/tag #{:text :group})
  (s/def :recursive-spec/on-tap (s/coll-of map? :kind vector?))
  (s/def :recursive-spec/props (s/keys :opt-un [:recursive-spec/on-tap]))
  (s/def :recursive-spec/el (s/keys :req-un [:recursive-spec/tag]
                                    :opt-un [:recursive-spec/props :recursive-spec/children]))
  (s/def :recursive-spec/children (s/coll-of (s/nilable :recursive-spec/el) :kind vector?))

  (display-explain
   :recursive-spec/el
   {:tag :group
    :children [{:tag :group
                :children [{:tag :group
                            :props {:on-tap {}}}]}]})

  (s/def :cat-wrapped-in-or-spec/kv (s/and
                                     sequential?
                                     (s/cat :k keyword? :v any?)))
  (s/def :cat-wrapped-in-or-spec/type #{:text})
  (s/def :cat-wrapped-in-or-spec/kv-or-string (s/or
                                               :map (s/keys :req [:cat-wrapped-in-or-spec/type])
                                               :kv :cat-wrapped-in-or-spec/kv))

  (display-explain :cat-wrapped-in-or-spec/kv-or-string {"foo" "hi"})

  (s/def :test-assert/name string?)

  (display-try
   (s/assert :test-assert/name :hello))

  (s/fdef test-instrument-adder
          :args (s/cat :x int? :y int?)
          :fn #(> (:ret %) (-> % :args :x))
          :ret pos-int?)
  (defn test-instrument-adder [x y]
    (+ x y))

  (st/instrument `test-instrument-adder)

  (display-try
   (test-instrument-adder "" :x))

  (orch.st/instrument `test-instrument-adder)

  (display-try
   (test-instrument-adder "" :x))

  (display-try
   (test-instrument-adder 1))

  (display-try
   (test-instrument-adder -1 -2))

  (display-try
   (test-instrument-adder 1 0)) (s/def :alt-spec/int-or-str (s/alt :int int? :string string?))
  (display-explain :alt-spec/int-or-str [:hi])

  (s/def :duplicate-preds/str-or-str (s/or
                                    ;; Use anonymous functions to assure
                                    ;; non-equality
                                      :str1 #(string? %)
                                      :str2 #(string? %)))

  (display-explain :duplicate-preds/str-or-str 1)

  (s/def :fspec-test/div (s/fspec
                          :args (s/cat :x int? :y pos-int?)))

  (defn my-div [x y]
    (assert (not (zero? (/ x y)))))

  (display-explain :fspec-test/div my-div)

  (display-explain (s/coll-of :fspec-test/div) [my-div])

  (s/def :fspec-ret-test/my-int pos-int?)
  (s/def :fspec-ret-test/plus (s/fspec
                               :args (s/cat :x int? :y pos-int?)
                               :ret :fspec-ret-test/my-int))

  (defn my-plus [x y]
    (+ x y))

  (display-explain :fspec-ret-test/plus my-plus)

  (display-explain (s/coll-of :fspec-ret-test/plus) [my-plus])

  (s/def :fspec-fn-test/minus (s/fspec
                               :args (s/cat :x int? :y int?)
                               :fn (s/and
                                    #(< (:ret %) (-> % :args :x))
                                    #(< (:ret %) (-> % :args :y)))))

  (defn my-minus [x y]
    (- x y)) (display-explain :fspec-fn-test/minus my-minus)

  (display-explain (s/coll-of :fspec-fn-test/minus) [my-minus])

  (display-explain (s/coll-of (s/fspec :args (s/cat :x int?) :ret int?)) [:foo])

  (display-explain (s/coll-of (s/fspec :args (s/cat :x int?) :ret int?)) [#{}])

  (display-explain (s/coll-of (s/fspec :args (s/cat :x int?) :ret int?)) [[]]) (defmulti pet :pet/type)
  (defmethod pet :dog [_]
    (s/keys))
  (defmethod pet :cat [_]
    (s/keys))

  (defmulti animal :animal/type)
  (defmethod animal :dog [_]
    (s/keys))
  (defmethod animal :cat [_]
    (s/keys))

  (s/def :multispec-in-compound-spec/pet1 (s/and
                                           map?
                                           (s/multi-spec pet :pet/type)))

  (s/def :multispec-in-compound-spec/pet2 (s/or
                                           :map1 (s/multi-spec pet :pet/type)
                                           :map2 (s/multi-spec animal :animal/type)))

  (display-explain :multispec-in-compound-spec/pet1 {:pet/type :fish})

  (display-explain :multispec-in-compound-spec/pet2 {:pet/type :fish})

  (expound/def :predicate-messages/string string? "should be a string")
  (expound/def :predicate-messages/vector vector? "should be a vector")

  (display-explain :predicate-messages/string :hello)

  (display-explain (s/or :s :predicate-messages/string
                         :v :predicate-messages/vector) 1)

  (display-explain (s/or :p pos-int?
                         :s :predicate-messages/string
                         :v vector?) 'foo)
  (def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")

  (expound/def :predicate-messages/email (s/and string? #(re-matches email-regex %)) "should be a valid email address")
  (expound/def :predicate-messages/score (s/int-in 0 100) "should be between 0 and 100")

  (display-explain
   :predicate-messages/email
   "sally@")

  (display-explain
   :predicate-messages/score
   101)

  (println "----- check results -----")

  (when check-results?
    (doseq [sym-to-check (st/checkable-syms)]
      (println "trying to check" sym-to-check "...")
      (try
        (st/with-instrument-disabled
          (orch.st/with-instrument-disabled
            (expound/explain-results (st/check sym-to-check {:clojure.spec.test.check/opts {:num-tests 5}}))))
        (catch Exception e
          (println "caught exception: " (.getMessage e))))))

  (s/fdef some-func
          :args (s/cat :x int?))

  (st/with-instrument-disabled
    (orch.st/with-instrument-disabled
      (expound/explain-results (st/check `some-func)))) (s/fdef results-str-fn1
                                                                :args (s/cat :x nat-int? :y nat-int?)
                                                                :ret pos-int?)
  (defn results-str-fn1 [x y]
    (+ x y))

  (s/fdef results-str-fn2
          :args (s/cat :x nat-int? :y nat-int?)
          :fn #(let [x (-> % :args :x)
                     y (-> % :args :y)
                     ret (-> % :ret)]
                 (< x ret)))
  (defn results-str-fn2 [x y]
    (+ x y))

  (expound/explain-result (st/check-fn `resultsf-str-fn1 (s/spec `results-str-fn2)))

  (s/def ::sorted-pair (s/and (s/cat :x int? :y int?) #(< (-> % :x) (-> % :y))))
  (s/explain ::sorted-pair [1 0]))


(go (= "--check-results" (first *command-line-args*)))
(shutdown-agents)
