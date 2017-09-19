# Expound

Expound formats `clojure.spec` error messages in a way that is optimized for humans to read.

Expound is in alpha while `clojure.spec` is in alpha.

Expound is tested with Clojure 1.9.0-alpha19 and Clojurescript 1.9.908. Clojurescript 1.9.542 only supports using `expound` and `expound-str` functions directly.

## Usage

[![Clojars Project](https://img.shields.io/clojars/v/expound.svg)](https://clojars.org/expound)

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

(s/explain :example/place {})
;; val: {} fails spec: :example/place predicate: (contains? % :city)
;; val: {} fails spec: :example/place predicate: (contains? % :state)
;; :clojure.spec.alpha/spec  :example/place
;; :clojure.spec.alpha/value  {}

(expound/expound :example/place {})
;; -- Spec failed --------------------

;;   {}

;; should contain keys: `:city`,`:state`

;; -- Relevant specs -------

;; :example/place:
;;   (clojure.spec.alpha/keys
;;    :req-un
;;    [:example.place/city :example.place/state])

;; -------------------------
;; Detected 1 error

(s/explain :example/place {:city "Denver", :state :CO})
;; In: [:state] val: :CO fails spec: :example.place/state at: [:state] predicate: string?
;; :clojure.spec.alpha/spec  :example/place
;; :clojure.spec.alpha/value  {:city "Denver", :state :CO}

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

(Setting `*explain-out*` does not work correctly in ClojureScript versions prior to `1.9.562` due to differences in `explain-data`)

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

;; Or set it globally
(set! s/*explain-out* expound/printer)
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

### Configuring the printer

By default, `printer` will omit valid values and replace them with `...`

```clojure
(set! s/*explain-out* expound/printer)
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

You can even provide your own function to display the invalid value.

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

### Using Orchestra

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
;;
;;
;; -------------------------
;; Detected 1 error
```

## Related work

- [Inspectable](https://github.com/jpmonettas/inspectable) - Tools to explore specs and spec failures at the REPL
- [Pretty-Spec](https://github.com/jpmonettas/pretty-spec) - Pretty printer for specs
- [Phrase](https://github.com/alexanderkiel/phrase) - Use specs to create error messages for users

## Prior Art

* Error messages in [Elm](http://elm-lang.org/), in particular the [error messages catalog](https://github.com/elm-lang/error-message-catalog)
* Error messages in [Figwheel](https://github.com/bhauman/lein-figwheel), in particular the config error messages generated from [strictly-specking](https://github.com/bhauman/strictly-specking)
* [Clojure Error Message Catalog](https://github.com/yogthos/clojure-error-message-catalog)
* [The Usability of beginner-oriented Clojure error messages](http://wiki.science.ru.nl/tfpie/images/6/6e/TFPIE16-slides-emachkasova.pdf)

## License

Copyright © 2017 Ben Brinckerhoff

Distributed under the Eclipse Public License version 1.0, just like Clojure.
