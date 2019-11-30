# Compatibility

|                                           | clj 1.9 + cljs 1.10.339 | clj 1.10 + cljs 1.10.439 | clj 1.10 + cljs 1.10.516 | clj 1.10 + cljs 1.10.520
|-------------------------------------------|-------------------------|--------------------------|--------------------------|--------------------------|
| `expound`                                 | yes                     | yes                      | yes                      | yes                      |
| `explain`                                 | yes                     | yes                      | yes                      | yes                      |
| `explain-results`                         | yes                     | yes                      | yes                      | yes                      |
| expound errors for instrumented functions | yes                     | no                       | yes                      | yes                      |
| expound errors for macros [^1]            | yes                     | yes                      | yes                      | yes                      |

## Relevant JIRAs

* 1.10.439 - https://dev.clojure.org/jira/browse/CLJS-2913
* 1.10.516 - https://dev.clojure.org/jira/browse/CLJS-3050

[^1]: Due to the way that macros are expanded in ClojureScript, you'll need to configure Expound in *Clojure*. This does not apply to self-hosted ClojureScript.

    (Note the `-e` arg below)

    `clj -Srepro -Sdeps '{:deps {expound {:mvn/version "0.8.1"} org.clojure/test.check {:mvn/version "0.9.0"} org.clojure/clojurescript {:mvn/version "1.10.520"}}}' -e "(require '[expound.alpha :as expound]) (set! clojure.spec.alpha/*explain-out* expound.alpha/printer)" -m cljs.main -re node`

    Now you will get Expound errors during macro-expansion:

    ```
    ClojureScript 1.10.520
    cljs.user=> (require '[clojure.core.specs.alpha])
    nil
    cljs.user=> (let [x])
    Execution error - invalid arguments to cljs.analyzer/do-macroexpand-check at (analyzer.cljc:3772).
    -- Spec failed --------------------

      ([x])
       ^^^

    should satisfy

      even-number-of-forms?

    -- Relevant specs -------

    :cljs.core.specs.alpha/bindings:
      (clojure.spec.alpha/and
       clojure.core/vector?
       cljs.core.specs.alpha/even-number-of-forms?
       (clojure.spec.alpha/* :cljs.core.specs.alpha/binding))

    -------------------------
    Detected 1 error
    cljs.user=>
    ```
