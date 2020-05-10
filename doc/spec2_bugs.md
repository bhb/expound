# Spec2 bugs

A list of bugs in spec2. I will report them as spec2 gets more stable.

### Using binding in spec fn

```clojure
;; make sure `n` is not defined
(let [n 1]
    (s/def ::foobar #(< n %))
    )
;; Expected: no error
;; Actual: 'Unable to resolve symbol: n in this context'    
```

Note: this might be doable with a different strategy e.g.

```clojure
(let [max-v 10]
  (s2/register ::foo
    (s2/resolve-spec `(s2/int-in 1 (inc ~max-v))))
  (s2/valid? ::foo 20))
```

### Bug with s/nest

```clojure
(s/def :alt-spec/one-many-int (s/cat :bs (s/alt :one int?
                                                  :many (s/nest (s/+ int?)))))

(s/explain :alt-spec/one-many-int [["2"]])
;; Attempting to call unbound fn: #'clojure.core/unquote
```

### Bug with using symbols in specs e.g.

```
> (s/def ::is-foo #{foo})
:expound.alpha2.core-test/is-foo
> (s/form ::is-foo)
#{foo}
> (s/explain ::is-foo 'foo)
Success!
nil
> (s/def ::is-or #{or})
:expound.alpha2.core-test/is-or
> (s/form ::is-or)
#{clojure.core/or}
> (s/explain ::is-or 'or)
or - failed: #{clojure.core/or} spec: :expound.alpha2.core-test/is-or
nil
```

From Alex Miller: "there is actually a known issue around sets of symbols (kind of a collision with symbol as function reference, which need qualification)"

### Bug with Clojure 1.9.0

`lein with-profile test-common,clj-1.9.0 test` fails (but moving to 1.10.0 works)

