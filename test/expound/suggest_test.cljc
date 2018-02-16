(ns expound.suggest-test
  (:require [clojure.test :as ct :refer [is testing deftest use-fixtures]]
            [expound.test-utils :as test-utils]
            [clojure.spec.alpha :as s]
            [expound.suggest :as suggest]))

(use-fixtures :once
  test-utils/check-spec-assertions
  test-utils/instrument-all)

(s/fdef example-fn
        :args (s/cat :a simple-symbol?
                     :b string?
                     :c simple-keyword?
                     :d int?
                     :e qualified-symbol?))
(defn example-fn [a b c d])

#?(:cljs

   (deftest valid-args
     (is (= ::suggest/no-spec-found
            (suggest/valid-args '(cljs.core/let1 [foo/bar 1]))))
     (is (= '(expound.suggest-test/example-fn b "b" :c 1 a/b)
            (suggest/valid-args `(example-fn a/b "b" :c 1 a/b))))) :clj
   (deftest valid-args
     (is (= ::suggest/no-spec-found
            (suggest/valid-args '(clojure.core/let1 [foo/bar 1]))))

     (is (=
          '(clojure.core/let [bar 1])
          (suggest/valid-args `(let [foo/bar 1]))))

     (is (=
          '(clojure.core/let [bar 1])
          (suggest/valid-args `(let ["bar" 1]))))

     (is (= '(expound.suggest-test/example-fn b "b" :c 1 a/b)
            (suggest/valid-args `(example-fn a/b "b" :c 1 a/b))))

     (is (= '(expound.suggest-test/example-fn b "b" :c 1 a/b)
            (suggest/valid-args `(example-fn ~'b b :c 1 a/b))))

     (is (= '(expound.suggest-test/example-fn b "b" :c 2 a/b)
            (suggest/valid-args `(example-fn ~'b :b :c 2 a/b))))

     (is (= '(expound.suggest-test/example-fn b "b" :c 1 a/b)
            (suggest/valid-args `(example-fn ~'b :a/b :c 1 a/b))))

     (is (= '(expound.suggest-test/example-fn b "b" :c 1 a/b)
            (suggest/valid-args `(example-fn ~'b "b" c 1 a/b))))

     (is (= '(expound.suggest-test/example-fn b "b" :c 1 a/b)
            (suggest/valid-args `(example-fn ~'b "b" :a/c 1 a/b))))

     (is (= '(expound.suggest-test/example-fn b "b" :c 1 a/b)
            (suggest/valid-args `(example-fn ~'b "b" "c" 1 a/b))))

     (is (= '(expound.suggest-test/example-fn b "b" :c 0 a/b)
            (suggest/valid-args `(example-fn ~'b "b" :c "foo" a/b))))

     ;; TODO - try this with weird int-in on inst-in
     ;; TODO - next up, if conversion doesn't work, try simplifying
     ;; HERE - try two problems with same sequence
))
