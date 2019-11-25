# Spec problems

Notes on the format of Spec's problems (as of alpha1).

Each problem has several keys:

*  `:in` is a vector of "keys" to navigate to the bad value in the original data structure. This is roughly analogous to the arguments to `get-in`, but the "keys" don't exactly match what is expected for `get-in` (in particular, they can locate the key of a map data structure, which `get-in` can't do. `get-in` also doesn't work with lists).
* `:via` is a vector of the specs names (keywords) followed to reach the spec that failed. This may be "missing" specs if they are not named.
* `:path` is a vector of "keys" AND the alternate branch names followed when picking amongst named alternates
* `:pred` is the symbol of the predicate that failed
* `:val` is the bad value

For example, given:

```clojure
(s/def :example/id (s/or :num pos-int?
                           :uuid uuid? ))

(s/def :example/entity (s/keys :req-un [:example/id]))
```

When you call `(s/explain-data :example/entity {:id -1})`, you'll get two problems:

```clojure
{:clojure.spec.alpha/spec :example/entity,
 :clojure.spec.alpha/value {:id -1}
 :clojure.spec.alpha/problems
 '(
   ;; problem 1
   {:path [:id :num],
    :pred clojure.core/pos-int?,
    :val -1,
    :via [:example/entity :example/id],
    :in [:id]}
   ;; problem 2
   {:path [:id :uuid],
    :pred clojure.core/uuid?,
    :val -1,
    :via [:example/entity :example/id],
    :in [:id]}),
}
```    

doc/spec_problems.md