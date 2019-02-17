# Compatibility

|                                           | clj 1.9 + cljs 1.10.339 | clj 1.10 + cljs 1.10.439 | clj 1.10 + cljs 1.10.516 | clj 1.10 + cljs 1.10.520
|-------------------------------------------|-------------------------|--------------------------|--------------------------|--------------------------|
| `expound`                                 | yes                     | yes                      | yes                      | yes                      |
| `explain`                                 | yes                     | yes                      | yes                      | yes                      |
| `explain-results`                         | yes                     | yes                      | yes                      | yes                      |
| expound errors for instrumented functions | yes                     | no                       | yes                      | yes                      |
| expound errors for macros                 | no                      | no                       | no                       | no                       |

## Relevant JIRAs

* 1.10.439 - https://dev.clojure.org/jira/browse/CLJS-2913
* 1.10.516 - https://dev.clojure.org/jira/browse/CLJS-3050
