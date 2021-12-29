(defproject expound "0.8.11-SNAPSHOT"
  :description "Human-optimized error messages for clojure.spec"
  :url "https://github.com/bhb/expound"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git" :url "https://github.com/bhb/expound"}
  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"]
                 [org.clojure/spec.alpha "0.2.176" :scope "provided"]]
  :deploy-repositories [["releases" :clojars]]
  :jar-exclusions [#"^public/.*"]
  :plugins [
            [lein-cljfmt "0.8.0"]
            [lein-cljsbuild "1.1.8" :exclusions [[org.clojure/clojure]]]
            [lein-hiera "1.1.0"]
            ]
  :cljsbuild {:builds
              [{:id "test"
                :source-paths ["src" "test"]
                :compiler {;; If you change output-to or output-dir,
                           ;; you must update karma.conf.js to match
                           :asset-path "test-web/out"
                           :output-to "resources/public/test-web/test.js"
                           :output-dir "resources/public/test-web/out"
                           :main "expound.test-runner"
                           :optimizations :none
                           :verbose true
                           :compiler-stats true}}]}
  :profiles {:dev {:dependencies [
                                  [binaryage/devtools "1.0.4"]
                                  [cider/piggieback "0.5.3"]
                                  [orchestra "2021.01.01-1"]
                                  [org.clojure/core.specs.alpha "0.2.62"]
                                  [vvvvalvalval/scope-capture "0.3.2"]
                                  [org.clojure/test.check "1.1.1"]
                                  [metosin/spec-tools "0.10.5"]
                                  [ring/ring-core "1.9.4"]
                                  [ring/ring-spec "0.0.4"] ; to test specs
                                  [org.onyxplatform/onyx-spec "0.13.0.0"] ; to test specs
                                  [cider/cider-nrepl "0.27.4"]
                                  ]
                   :injections [(require 'sc.api)]
                   :plugins [
                             [io.aviso/pretty "1.1.1"]
                             ;; I am NOT adding cider-nrepl here because
                             ;; using it as a plugin seems to add it as a top-level dependency
                             ;; when I build the package!
                             ]
                   ;; need to add dev source path here to get user.clj loaded
                   :source-paths ["src" "dev"]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl
                                                     ;; Manually add all middlewear
                                                     ;; https://docs.cider.mx/cider-nrepl/usage.html#_via_leiningen
                                                     cider.nrepl/wrap-apropos
                                                     cider.nrepl/wrap-classpath
                                                     cider.nrepl/wrap-clojuredocs
                                                     cider.nrepl/wrap-complete
                                                     cider.nrepl/wrap-debug
                                                     cider.nrepl/wrap-format
                                                     cider.nrepl/wrap-info
                                                     cider.nrepl/wrap-inspect
                                                     cider.nrepl/wrap-macroexpand
                                                     cider.nrepl/wrap-ns
                                                     cider.nrepl/wrap-spec
                                                     cider.nrepl/wrap-profile
                                                     cider.nrepl/wrap-refresh
                                                     cider.nrepl/wrap-resource
                                                     cider.nrepl/wrap-stacktrace
                                                     cider.nrepl/wrap-test
                                                     cider.nrepl/wrap-trace
                                                     cider.nrepl/wrap-out
                                                     cider.nrepl/wrap-undef
                                                     cider.nrepl/wrap-version
                                                     cider.nrepl/wrap-xref
                                         ]}
                   ;; need to add the compliled assets to the :clean-targets
                   :clean-targets ^{:protect false} ["resources/public/test-web"
                                                     :target-path]}
             :check {:global-vars {*unchecked-math*     :warn-on-boxed
                                   *warn-on-reflection* true}}
             :kaocha [:test-common
                      {:dependencies [[lambdaisland/kaocha "1.60.972"]
                                      [lambdaisland/kaocha-cloverage "1.0-45"]]}]
             :test-common {:dependencies [[org.clojure/test.check "1.1.1"]
                                          [orchestra "2021.01.01-1"]
                                          [pjstadig/humane-test-output "0.11.0"]
                                          [com.gfredericks/test.chuck "0.2.13"]
                                          [io.aviso/pretty "1.1.1"]
                                          [org.clojure/core.specs.alpha "0.2.62"]
                                          [com.stuartsierra/dependency "1.0.0"]
                                          [ring/ring-core "1.9.4"]
                                          [ring/ring-spec "0.0.4"] ; to test specs
                                          [org.onyxplatform/onyx-spec "0.13.0.0"] ; to test specs
                                          [metosin/spec-tools "0.10.5"]
                                          [com.bhauman/spell-spec "0.1.2"]]
                           :middleware [io.aviso.lein-pretty/inject]
                           :injections [(require 'pjstadig.humane-test-output)
                                        (pjstadig.humane-test-output/activate!)]
                           }
             :test-web [:test-common
                        {:source-paths ["test"]
                         :dependencies [[karma-reporter "3.1.0"]]}]
             :cljs-repl {:dependencies [[cider/piggieback "0.5.3"]]}
             :clj-1.9.0 {:dependencies [[org.clojure/clojure "1.9.0" :upgrade false]
                                        [metosin/spec-tools "0.7.1" :upgrade false]]}
             :clj-1.10.0 {:dependencies [[org.clojure/clojure "1.10.0" :upgrade false]
                                         [metosin/spec-tools "0.7.1" :upgrade false]]}
             :cljs-1.10.238 {:dependencies  [[org.clojure/clojurescript "1.10.238" :upgrade false]]}
             :cljs-1.10.339 {:dependencies [[org.clojure/clojurescript "1.10.339" :upgrade false]]}
             :cljs-1.10.439 {:dependencies [[org.clojure/clojurescript "1.10.439" :upgrade false]]}
             :cljs-1.10.516 {:dependencies [[org.clojure/clojurescript "1.10.516" :upgrade false]]}
             :spec-0.2.168  {:dependencies [[org.clojure/spec.alpha "0.2.168" :upgrade false]]}
             :spec-0.2.176  {:dependencies [[org.clojure/spec.alpha "0.2.176" :upgrade false]]}
             :orch-2019.02.06-1 {:dependencies [[orchestra "2019.02.06-1" :upgrade false]]}
             :orch-2020.07.12-1 {:dependencies [[orchestra "2020.07.12-1" :upgrade false]]}
             }
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "run-tests-once" ["with-profile" "test-web" "cljsbuild" "once" "test"]
            "run-tests-auto" ["do"
                              ["with-profile" "test-web" "cljsbuild" "once" "test"]
                              ["with-profile" "test-web" "cljsbuild" "auto" "test"]]}
  :test-refresh {:refresh-dirs ["src" "test"]
                 :watch-dirs ["src" "test"]})
