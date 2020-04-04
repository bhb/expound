# Project Update 5: 2020-02-01 - 2020-02-15


I'm taking a break from designing [a better way to customize Expound error messages](https://github.com/bhb/expound/issues/189) to work on a version of Expound that will work with `clojure/spec-alpha2` AKA `clojure.alpha.spec` AKA `spec2`.

The new Expound namespace is `expound.alpha2`. This version will only work with `spec2` and will NOT be backwards compatible with `expound.alpha`. Both versions will coexist in the same JAR, so you can use whichever one you want, depending on the version of Spec you use.

In addition to supporting `spec2`, `expound.alpha2` will include a number of changes (all of which are subject to change):

* [Hide "Relevant specs" section by default](https://twitter.com/bbrinck/status/1204595098207444993)
* Remove deprecated `expound/def` macro (you can still use `defmsg`)
* Make option names more consistent: one option includes the verb "show" while another includes "print" and I don't think there's any meaningful difference
* Remove headers like "Spec failed" which add almost no information
* Include new API for customizing error messages
* Rework internal multi-methods and namespaces to simplify code

I'm happy to report that my `spec2` branch has a few passing tests, only 85 or so failing tests to go.
