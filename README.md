# Expound

[![Clojars Project](https://img.shields.io/clojars/v/expound.svg)](https://clojars.org/expound)
[![cljdoc badge](https://cljdoc.org/badge/expound/expound)](https://cljdoc.org/d/expound/expound/CURRENT)
[![CircleCI](https://circleci.com/gh/bhb/expound.svg?style=shield)](https://circleci.com/gh/bhb/expound)

**If you are using recent versions of ClojureScript, please check the [compatibility guide](doc/compatibility.md)**

Expound formats `clojure.spec` error messages in a way that is optimized for humans to read.

For example, Expound will replace a Spec error message like:

```
val: {} fails spec: :example/place predicate: (contains? % :city)
val: {} fails spec: :example/place predicate: (contains? % :state)
:clojure.spec.alpha/spec  :example/place
:clojure.spec.alpha/value  {}
```

with

```
-- Spec failed --------------------

 {}

should contain keys: `:city`,`:state`
```

Expound is in alpha while `clojure.spec` is in alpha.

**Expound depends on projects supported by [Clojurists Together](https://www.clojuriststogether.org/). If you find this project useful, please consider making a monthly donation to Clojurists Together (or ask your employer to do so).**

## Installation

### Leiningen/Boot

`[expound "0.7.2"]`

#### deps.edn

`expound {:mvn/version "0.7.2"}`

### Lumo

`npm install @bbrinck/expound`

## Usage

[API docs](https://cljdoc.xyz/d/expound/expound/CURRENT)

```
> brew install clojure
> clj -Sdeps '{:deps {friendly {:git/url "https://gist.github.com/bhb/2686b023d074ac052dbc21f12f324f18" :sha "bb5806bd655d743f3b48b36ce83c0085a8d7c54a"}}}' -m friendly
user=> (require '[expound.alpha :as expound])
nil
user=> (expound/expound string? 1)
nil
-- Spec failed --------------------

  1

should satisfy

  string?

-------------------------
Detected 1 error
user=>
```

### `expound`

Replace calls to `clojure.spec.alpha/explain` with `expound.alpha/expound` and to `clojure.spec.alpha/explain-str` with `expound.alpha/expound-str`.

```clojure
(require '[clojure.spec.alpha :as s])
;; for clojurescript:
;; (require '[cljs.spec.alpha :as s])
(require '[expound.alpha :as expound])

(s/def :example.place/city string?)
(s/def :example.place/state string?)
(s/def :example/place (s/keys :req-un [:example.place/city :example.place/state]))
(expound/expound :example/place {:city "Denver", :state :CO})
;; -- Spec failed --------------------

;;   {:city ..., :state :CO}
;;                      ^^^

;; should satisfy

;;   string?

;; -- Relevant specs -------

;; :example.place/state:
;;   clojure.core/string?
;; :example/place:
;;   (clojure.spec.alpha/keys
;;    :req-un
;;    [:example.place/city :example.place/state])

;; -------------------------
;; Detected 1 error
```

### `*explain-out*`

To use other Spec functions, set `clojure.spec.alpha/*explain-out*` (or `cljs.spec.alpha/*explain-out*` for ClojureScript) to `expound/printer`.

```clojure
(require '[clojure.spec.alpha :as s])
;; for clojurescript:
;; (require '[cljs.spec.alpha :as s])
(require '[expound.alpha :as expound])

(s/def :example.place/city string?)
(s/def :example.place/state string?)

;;  Use `assert`
(s/check-asserts true) ; enable asserts

;; Set var in the scope of 'binding'
(binding [s/*explain-out* expound/printer]
  (s/assert :example.place/city 1))

(set! s/*explain-out* expound/printer)
;; (or alter-var-root - see doc/faq.md)
(s/assert :example.place/city 1)

;; Use `instrument`
(require '[clojure.spec.test.alpha :as st])

(s/fdef pr-loc :args (s/cat :city :example.place/city
                            :state :example.place/state))
(defn pr-loc [city state]
  (str city ", " state))

(st/instrument `pr-loc)
(pr-loc "denver" :CO)

;; You can use `explain` without converting to expound
(s/explain :example.place/city 123)
```

Due to the way that macros are expanded in ClojureScript, you'll need to configure Expound in *Clojure* to use Expound during macro-expansion. This does not apply to self-hosted ClojureScript. Note the `-e` arg when starting ClojureScript:

`clj -Srepro -Sdeps '{:deps {expound {:mvn/version "0.7.2"} org.clojure/test.check {:mvn/version "0.9.0"} org.clojure/clojurescript {:mvn/version "1.10.520"}}}' -e "(require '[expound.alpha :as expound]) (set! clojure.spec.alpha/*explain-out* expound.alpha/printer)" -m cljs.main -re node`

### Printing results for `check`

Re-binding `s/*explain-out*` has no effect on the results of `cljs.spec.test.alpha/summarize-results`, but Expound provides the function `expound/explain-results` to print the results from `clojure.spec.test.alpha/check`.

```clojure
(require '[expound.alpha :as expound]
         '[clojure.spec.test.alpha :as st]
         '[clojure.spec.alpha :as s]
         '[clojure.test.check])

(s/fdef ranged-rand
  :args (s/and (s/cat :start int? :end int?)
               #(< (:start %) (:end %)))
  :ret int?
  :fn (s/and #(>= (:ret %) (-> % :args :start))
             #(< (:ret %) (-> % :args :end))))
(defn ranged-rand
  "Returns random int in range start <= rand < end"
  [start end]
  (+ start (long (rand (- start end)))))

(set! s/*explain-out* expound/printer)
;; (or alter-var-root - see doc/faq.md)
(expound/explain-results (st/check `ranged-rand))
;;== Checked user/ranged-rand =================
;;
;;-- Function spec failed -----------
;;
;;  (user/ranged-rand -3 0)
;;
;;failed spec. Function arguments and return value
;;
;;  {:args {:start -3, :end 0}, :ret -5}
;;
;;should satisfy
;;
;;  (fn
;;   [%]
;;   (>= (:ret %) (-> % :args :start)))
```

### Error messages for predicates

#### Adding error messages

If a value fails to satisfy a predicate, Expound will print the name of the function (or `<anonymous function>` if the function has no name). To improve the error message, you can use `expound.alpha/defmsg` to add a human-readable error message to the spec.

```clojure
(s/def :ex/name string?)
(expound/defmsg :ex/name "should be a string")
(expound/expound :ex/name :bob)
;; -- Spec failed --------------------
;;
;; :bob
;;
;; should be a string
```

#### Built-in predicates with error messages

Expound provides a default set of type-like predicates with error messages. For example:

```clojure
(expound/expound :expound.specs/pos-int -1)
;; -- Spec failed --------------------
;;
;; -1
;;
;; should be a positive integer
```

You can see the full list of available specs with `expound.specs/public-specs`.

### Printer options

Create a custom printer by changing the following options e.g.

```clojure
(set! s/*explain-out* (expound/custom-printer {:show-valid-values? true :print-specs? false :theme :figwheel-theme}))
;; (or alter-var-root - see doc/faq.md)
```

| name | spec |  default | description |
|------|------|----------|-------------|
| `:show-valid-values?` | `boolean?` | `false` | If `false`, replaces valid values with `...` (example below) |
| `:value-str-fn` | `ifn?` | provided function | Function to print bad values (example below) |
| `:print-specs?` | `boolean?` | `true` | If true, display "Relevant specs" section. Otherwise, omit that section. |
| `:theme` | `#{:figwheel-theme :none}` | `:none` | Enables color theme. |


#### `:show-valid-values?`

By default, `printer` will omit valid values and replace them with `...`

```clojure
(set! s/*explain-out* expound/printer)
;; (or alter-var-root - see doc/faq.md)
(s/explain :example/place {:city "Denver" :state :CO :country "USA"})

;; -- Spec failed --------------------
;;
;;   {:city ..., :state :CO, :country ...}
;;                      ^^^
;;
;; should satisfy
;;
;;   string?
```

You can configure Expound to show valid values:

```clojure
(set! s/*explain-out* (expound/custom-printer {:show-valid-values? true}))
;; (or alter-var-root - see doc/faq.md)
(s/explain :example/place {:city "Denver" :state :CO :country "USA"})

;; -- Spec failed --------------------
;;
;; {:city "Denver", :state :CO, :country "USA"}
;;                         ^^^
;;
;; should satisfy
;;
;;   string?
```

##### `:value-str-fn`

You can provide your own function to display the invalid value.

```clojure
;; Your implementation should meet the following spec:
(s/fdef my-value-str
        :args (s/cat
               :spec-name (s/nilable #{:args :fn :ret})
               :form any?
               :path :expound/path
               :value any?)
        :ret string?)
(defn my-value-str [_spec-name form path value]
  (str "In context: " (pr-str form) "\n"
       "Invalid value: " (pr-str value)))

(set! s/*explain-out* (expound/custom-printer {:value-str-fn my-value-str}))
;; (or alter-var-root - see doc/faq.md)
(s/explain :example/place {:city "Denver" :state :CO :country "USA"})

;; -- Spec failed --------------------
;;
;;   In context: {:city "Denver", :state :CO, :country "USA"}
;;   Invalid value: :CO
;;
;; should satisfy
;;
;;   string?
```

### Manual clojure.test/report override

Clojure test allows you to declare a custom multi-method for its `clojure.test/report` function. This is particularly useful in ClojureScript, where a test runner can take care of the boilerplate code:

```clojure
(ns pkg.test-runner
  (:require [cljs.spec.alpha :as s]
            [cljs.test :as test :refer-macros [run-tests]]
            [expound.alpha :as expound]
            ;; require your namespaces here
            [pkg.namespace-test]))

(enable-console-print!)

(set! s/*explain-out* expound/printer)
;; (or alter-var-root - see doc/faq.md)

;; We try to preserve the clojure.test output format
(defmethod test/report [:cljs.test/default :error] [m]
  (test/inc-report-counter! :error)
  (println "\nERROR in" (test/testing-vars-str m))
  (when (seq (:testing-contexts (test/get-current-env)))
    (println (test/testing-contexts-str)))
  (when-let [message (:message m)] (println message))
  (let [actual (:actual m)
        ex-data (ex-data actual)]
    (if (:cljs.spec.alpha/failure ex-data)
      (do (println "expected:" (pr-str (:expected m)))
          (print "  actual:\n")
          (print (.-message actual)))
      (test/print-comparison m))))

;; run tests, (stest/instrument) either here or in the individual test files.
(run-tests 'pkg.namespace-test)
```

### Using Expound as printer for Orchestra

Use [Orchestra](https://github.com/jeaye/orchestra) with Expound to get human-optimized error messages when checking your `:ret` and `:fn` specs.

```clojure
(require '[orchestra.spec.test :as st])

(s/fdef location
        :args (s/cat :city :example.place/city
                     :state :example.place/state)
        :ret string?)
(defn location [city state]
  ;; incorrect implementation
  nil)

(st/instrument)
(set! s/*explain-out* expound/printer)
;; (or alter-var-root - see doc/faq.md)
(location "Seattle" "WA")

;;ExceptionInfo Call to #'user/location did not conform to spec:
;; form-init3240528896421126128.clj:1
;;
;; -- Spec failed --------------------
;;
;; Return value
;;
;; nil
;;
;; should satisfy
;;
;; string?
;;
;; -------------------------
;; Detected 1 error
```
## Conformers

Expound will not give helpful errors (and in some cases, will throw an exception) if you use conformers to transform values. Although using conformers in this way is fairly common, my understanding is that this is not an [intended use case](https://dev.clojure.org/jira/browse/CLJ-2116).

If you want to use Expound with conformers, you'll need to write a custom printer. See "Printer options" above.

## Related work

- [Inspectable](https://github.com/jpmonettas/inspectable) - Tools to explore specs and spec failures at the REPL
- [Pretty-Spec](https://github.com/jpmonettas/pretty-spec) - Pretty printer for specs
- [Phrase](https://github.com/alexanderkiel/phrase) - Use specs to create error messages for users
- [Pinpointer](https://github.com/athos/Pinpointer) - spec error reporter based on a precise error analysis

## Prior Art

* Error messages in [Elm](http://elm-lang.org/), in particular the [error messages catalog](https://github.com/elm-lang/error-message-catalog)
* Error messages in [Figwheel](https://github.com/bhauman/lein-figwheel), in particular the config error messages generated from [strictly-specking](https://github.com/bhauman/strictly-specking)
* [Clojure Error Message Catalog](https://github.com/yogthos/clojure-error-message-catalog)
* [The Usability of beginner-oriented Clojure error messages](http://wiki.science.ru.nl/tfpie/images/6/6e/TFPIE16-slides-emachkasova.pdf)
* ["Illuminated Macros" - Chris Houser / Jonathan Claggett](https://www.youtube.com/watch?v=o75g9ZRoLaw)
* [seqex](https://github.com/jclaggett/seqex)
* ["Improving Clojure's Error Messages with Grammars" - Colin Fleming](https://www.youtube.com/watch?v=kt4haSH2xcs)

## Contributing

Pull requests are welcome, although please open an issue first to discuss the proposed change. I also answer questions on the #expound channel on [clojurians Slack](http://clojurians.net/).

If you are working on the code, please read the [Development Guide](doc/development.md)

## License

Copyright © 2017-2018 Ben Brinckerhoff

Distributed under the Eclipse Public License version 1.0, just like Clojure.
