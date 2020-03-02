# Project Update 5: 2020-02-16 - 2020-02-29

I've been working on `expound.alpha2` and I'm happy to report I have an early version that works with `clojure.alpha.spec` AKA `spec2`. 

If you are experimenting with `spec2` and want to use Expound, you can use commits from the [`spec2` branch](https://github.com/bhb/expound/pull/186). I won't be releasing JARs for some time, but you can use `tools.deps` or [`lein-git-down`](https://github.com/reifyhealth/lein-git-down) to depend on specific commits.

Here's an example using the "movie-times-user" example from the ["Schema and select" wiki page](https://github.com/clojure/spec-alpha2/wiki/Schema-and-select)

```clojure
(ns example)
(require '[clojure.alpha.spec :as s])
(require '[expound.alpha2 :as expound])

(s/def ::street string?)
(s/def ::city string?)
(s/def ::state string?) ;; simplified
(s/def ::zip int?) ;; simplified
(s/def ::addr (s/schema [::street ::city ::state ::zip]))
(s/def ::id int?)
(s/def ::first string?)
(s/def ::last string?)
(s/def ::user (s/schema [::id ::first ::last ::addr]))
(s/def ::movie-times-user (s/select ::user [::id ::addr {::addr [::zip]}]))

(s/explain ::movie-times-user {})
;; {} - failed: (fn [m] (contains? m :example/id)) spec: :example/movie-times-user
;; {} - failed: (fn [m] (contains? m :example/addr)) spec: :example/movie-times-user

(expound/expound ::movie-times-user {})

;;-- Spec failed --------------------
;;
;;  {}
;;
;;should satisfy
;;
;;  (fn [m] (contains? m :example/id))
;;
;;or
;;
;;  (fn [m] (contains? m :example/addr))
;;
;;-- Relevant specs -------
;;
;;:example/movie-times-user:
;;  (clojure.alpha.spec/select
;;   :example/user
;;   [:example/id :example/addr #:example{:addr [:example/zip]}])
;;
;;————————————
;;Detected 1 error
```

As you can see, there are many rough edges, but it should work for common cases. Please feel free to [report issues](https://github.com/bhb/expound/issues)!

### Caveats

* The API for `expound.alpha2` is in flux: breaking changes are expected!
* No ClojureScript support
* `fspec` is currently broken
* Many new features in spec2 may not work, but most of the features ported from spec1 should work fine (except `fspec`, see above)