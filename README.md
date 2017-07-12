# Expound

Expound formats `clojure.spec` errors in a way that is optimized for humans to read.

Expound is in alpha while `clojure.spec` is in alpha.

## Usage

INSERT jar info here

Replace calls to `clojure.spec.alpha/explain-str` with `expound.alpha/expound`

```clojure
(require '[clojure.spec.alpha :as s])
(require '[expound.alpha :as expound])

(s/def :example.place/city string?)
(s/def :example.place/state string?)
(s/def :example/place (s/keys :req-un [:example.place/city :example.place/state]))

(println (s/explain-str :example/place {}))
;; val: {} fails spec: :example/place predicate: (contains? % :city)
;; val: {} fails spec: :example/place predicate: (contains? % :state)
;; :clojure.spec.alpha/spec  :example/place
;; :clojure.spec.alpha/value  {}

(println (expound/expound :example/place {}))
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

(println (s/explain-str :example/place {:city "Denver", :state :CO}))
;; In: [:state] val: :CO fails spec: :example.place/state at: [:state] predicate: string?
;; :clojure.spec.alpha/spec  :example/place
;; :clojure.spec.alpha/value  {:city "Denver", :state :CO}

(println (expound/expound :example/place {:city "Denver", :state :CO}))
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

## Prior Art

* Error messages in [Elm](http://elm-lang.org/), in particular the [error messages catalog](https://github.com/elm-lang/error-message-catalog)
* Error messages in [Figwheel](https://github.com/bhauman/lein-figwheel), in particular the config error messages generated from [strictly-specking](https://github.com/bhauman/strictly-specking)
* [Clojure Error Message Catalog](https://github.com/yogthos/clojure-error-message-catalog)
* [The Usability of beginner-oriented Clojure error messages](http://wiki.science.ru.nl/tfpie/images/6/6e/TFPIE16-slides-emachkasova.pdf)

## License

Copyright © 2017 Ben Brinckerhoff

Distributed under the Eclipse Public License version 1.0, just like Clojure.
