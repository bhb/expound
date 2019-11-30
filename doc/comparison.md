# Comparison

Expound's error messages are more verbose than the `clojure.spec` messages, which can help you quickly determine why a value fails a spec. Here are some examples.


---

### Nested data structures


If the invalid value is nested, Expound will help locate the problem


#### Specs


```clojure
(s/def :db/id clojure.core/pos-int?)
(s/def :db/ids (s/coll-of :db/id))
(s/def :app/request (s/keys :req-un [:db/ids]))
```

#### Value


```clojure
{:ids [123 "456" 789]}
```

#### `clojure.spec` message


```
"456" - failed: pos-int? in: [:ids 1] at: [:ids] spec: :db/id
```

#### Expound message


```
-- Spec failed --------------------

  {:ids [... "456" ...]}
             ^^^^^

should satisfy

  pos-int?

-------------------------
Detected 1 error
```

---

### Missing keys


If a key is missing from a map, Expound will display the associated spec


#### Specs


```clojure
(s/def :address.west-coast/city clojure.core/string?)
(s/def :address.west-coast/state #{"CA" "WA" "OR"})
(s/def :app/address (s/keys :req-un [:address.west-coast/city :address.west-coast/state]))
```

#### Value


```clojure
{}
```

#### `clojure.spec` message


```
{} - failed: (contains? % :city) spec: :app/address
{} - failed: (contains? % :state) spec: :app/address
```

#### Expound message


```
-- Spec failed --------------------

  {}

should contain keys: :city, :state

| key    | spec              |
|========+===================|
| :city  | string?           |
|--------+-------------------|
| :state | #{"CA" "WA" "OR"} |

-------------------------
Detected 1 error
```

---

### Set-based specs


If a value doesn't match a set-based spec, Expound will list the possible values


#### Specs


```clojure
(s/def :address.west-coast/city clojure.core/string?)
(s/def :address.west-coast/state #{"CA" "WA" "OR"})
(s/def :app/address (s/keys :req-un [:address.west-coast/city :address.west-coast/state]))
```

#### Value


```clojure
{:city "Seattle", :state "ID"}
```

#### `clojure.spec` message


```
"ID" - failed: #{"CA" "WA" "OR"} in: [:state] at: [:state] spec: :address.west-coast/state
```

#### Expound message


```
-- Spec failed --------------------

  {:city ..., :state "ID"}
                     ^^^^

should be one of: "CA", "OR", "WA"

-------------------------
Detected 1 error
```

---

### Grouping


Expound will group alternatives


#### Specs


```clojure
(s/def :address.west-coast/zip (s/or :str clojure.core/string? :num clojure.core/pos-int?))
```

#### Value


```clojure
:98109
```

#### `clojure.spec` message


```
:98109 - failed: string? at: [:str] spec: :address.west-coast/zip
:98109 - failed: pos-int? at: [:num] spec: :address.west-coast/zip
```

#### Expound message


```
-- Spec failed --------------------

  :98109

should satisfy

  string?

or

  pos-int?

-------------------------
Detected 1 error
```

---

### Predicate descriptions


If you provide human-readable descriptions of predicates, Expound will display them


#### Specs


```clojure
(def expound.comparison/email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(s/def :app.user/email (s/and clojure.core/string? (fn* [p1__1607__1608__auto__] (clojure.core/re-matches expound.comparison/email-regex p1__1607__1608__auto__))))
(expound.alpha/defmsg :app.user/email "should be a valid email address")
```

#### Value


```clojure
"@example.com"
```

#### `clojure.spec` message


```
"@example.com" - failed: (re-matches email-regex %) spec: :app.user/email
```

#### Expound message


```
-- Spec failed --------------------

  "@example.com"

should be a valid email address

-------------------------
Detected 1 error
```

---

### Too few elements in a sequence


If you are missing elements, Expound will describe what must come next


#### Specs


```clojure
(s/def :app/ingredient (s/cat :quantity clojure.core/number? :unit clojure.core/keyword?))
```

#### Value


```clojure
[100]
```

#### `clojure.spec` message


```
() - failed: Insufficient input at: [:unit] spec: :app/ingredient
```

#### Expound message


```
-- Syntax error -------------------

  [100]

should have additional elements. The next element ":unit" should satisfy

  keyword?

-------------------------
Detected 1 error
```

---

### Too many elements in a sequence


If you have extra elements, Expound will point out which elements should be removed


#### Specs


```clojure
(s/def :app/ingredient (s/cat :quantity clojure.core/number? :unit clojure.core/keyword?))
```

#### Value


```clojure
[100 :teaspoon :sugar]
```

#### `clojure.spec` message


```
(:sugar) - failed: Extra input in: [2] spec: :app/ingredient
```

#### Expound message


```
-- Syntax error -------------------

  [... ... :sugar]
           ^^^^^^

has extra input

-------------------------
Detected 1 error
```
