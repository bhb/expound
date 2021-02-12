(defproject expound "0.8.9"
  :description "Human-optimized error messages for clojure.spec"
  :url "https://github.com/bhb/expound"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git" :url "https://github.com/bhb/expound"}
  :dependencies [[org.clojure/clojure "1.10.1" :scope "provided"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"]
                 [org.clojure/spec.alpha "0.2.176" :scope "provided"]]
  :deploy-repositories [["releases" :clojars]]
  :jar-exclusions [#"^public/.*"]
  :plugins [
            [com.jakemccrary/lein-test-refresh "0.23.0"]
            [lein-cljfmt "0.6.4"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]
            [lein-figwheel "0.5.18"]
            [lein-hiera "1.1.0"]
            ]
  :cljsbuild {:builds
              [{:id "test"
                :source-paths ["src" "test"]
                ;;:notify-command ["./bin/tests"]
                :figwheel true
                :compiler {;; If you change output-to or output-dir,
                           ;; you must update karma.conf.js to match
                           :asset-path "test-web/out"
                           :output-to "resources/public/test-web/test.js"
                           :output-dir "resources/public/test-web/out"
                           :main "expound.test-runner"
                           :optimizations :none
                           :verbose true
                           :compiler-stats true}}]}
  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             :server-port 3446 ;; default is 3449

             ;; :server-ip "127.0.0.1"

             ;; :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             :nrepl-port 7800

             ;; Show config errors, but disable interactive validation so
             ;; we can check for errors in CI.
             :validate-interactive :quit

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this

             ;; doesn't work for you just run your own server :) (see lein-ring)

             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you are using emacsclient you can just use
             ;; :open-file-command "emacsclient"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"

             ;; to pipe all the output to the repl
             ;; :server-logfile false
}
  :profiles {:dev {:dependencies [
                                  [binaryage/devtools "0.9.10"]
                                  [figwheel-sidecar "0.5.18"]
                                  [cider/piggieback "0.4.0"]
                                  [orchestra "2020.09.18-1"]
                                  [org.clojure/core.specs.alpha "0.2.44"]
                                  [vvvvalvalval/scope-capture "0.3.2"]
                                  [org.clojure/test.check "0.10.0"]
                                  [metosin/spec-tools "0.8.2"]
                                  [ring/ring-core "1.6.3"] ; required to make ring-spec work, may cause issues with figwheel?
                                  [ring/ring-spec "0.0.4"] ; to test specs
                                  [org.onyxplatform/onyx-spec "0.13.0.0"] ; to test specs
                                  [com.gfredericks/test.chuck "0.2.10"]
                                  [cider/cider-nrepl "0.22.4"]
                                  ]
                   :injections [(require 'sc.api)]
                   :plugins [
                             [io.aviso/pretty "0.1.37"]
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
                      {:dependencies [[lambdaisland/kaocha "0.0-565"]
                                      [lambdaisland/kaocha-cloverage "0.0-41"]]}]
             :test-common {:dependencies [[org.clojure/test.check "0.10.0-alpha3"]
                                          [pjstadig/humane-test-output "0.9.0"]
                                          [com.gfredericks/test.chuck "0.2.10"]
                                          [orchestra "2020.09.18-1"]
                                          [io.aviso/pretty "0.1.37"]
                                          [org.clojure/core.specs.alpha "0.2.44"]
                                          [com.stuartsierra/dependency "0.2.0"]
                                          [ring/ring-core "1.6.3"] ; required to make ring-spec work, may cause issues with figwheel?
                                          [ring/ring-spec "0.0.4"] ; to test specs
                                          [org.onyxplatform/onyx-spec "0.13.0.0"] ; to test specs
                                          [vvvvalvalval/scope-capture "0.3.1"]
                                          [metosin/spec-tools "0.7.1"]
                                          [com.bhauman/spell-spec "0.1.1"]]
                           :middleware [io.aviso.lein-pretty/inject]
                           :injections [(require 'pjstadig.humane-test-output)
                                        (pjstadig.humane-test-output/activate!)]
                           }
             :test-web [:test-common
                        {:source-paths ["test"]
                         :dependencies [[figwheel-sidecar "0.5.18"]
                                        [karma-reporter "3.1.0"]]}]
             :cljs-repl {:dependencies [[cider/piggieback "0.4.0"]]}
             :clj-1.9.0 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :clj-1.10.0 {:dependencies [[org.clojure/clojure "1.10.0"]]}
             :cljs-1.10.238 {:dependencies  [[org.clojure/clojurescript "1.10.238"]]}
             :cljs-1.10.339 {:dependencies [[org.clojure/clojurescript "1.10.339"]]}
             :cljs-1.10.439 {:dependencies [[org.clojure/clojurescript "1.10.439"]]}
             :cljs-1.10.516 {:dependencies [[org.clojure/clojurescript "1.10.516"]]}
             :spec-0.2.168  {:dependencies [[org.clojure/spec.alpha "0.2.168"]]}
             :spec-0.2.176  {:dependencies [[org.clojure/spec.alpha "0.2.176"]]}
             :orch-2019.02.06-1 {:dependencies [[orchestra "2019.02.06-1"]]}
             :orch-2020.07.12-1 {:dependencies [[orchestra "2020.07.12-1"]]}
             }
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "run-tests-once" ["with-profile" "test-web" "cljsbuild" "once" "test"]
            "run-tests-auto" ["do"
                              ["with-profile" "test-web" "cljsbuild" "once" "test"]
                              ["with-profile" "test-web" "cljsbuild" "auto" "test"]]}
  :test-refresh {:refresh-dirs ["src" "test"]
                 :watch-dirs ["src" "test"]})
