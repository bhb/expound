(defproject expound "0.3.2-SNAPSHOT"
  :description "Human-optimized error messages for clojure.spec"
  :url "https://github.com/bhb/expound"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha19" :scope "provided"]
                 [org.clojure/clojurescript "1.9.946" :scope "provided"]
                 [org.clojure/spec.alpha "0.1.123" :scope "provided"]]

  :plugins [[com.jakemccrary/lein-test-refresh "0.20.0"]
            [lein-cljfmt "0.5.7"]
            [lein-cljsbuild "1.1.5" :exclusions [[org.clojure/clojure]]]
            [lein-figwheel "0.5.14"]]

  :cljsbuild {:builds
              [{:id "test"
                :source-paths ["src" "test"]
                ;;:notify-command ["./bin/tests"]
                :figwheel true
                :compiler { ;; If you change output-to or output-dir,
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
  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.2"]
                                  [figwheel-sidecar "0.5.14"]
                                  [com.cemerick/piggieback "0.2.1"]
                                  [orchestra "2017.07.04-1"]
                                  [org.clojure/core.specs.alpha "0.1.24"]
                                  [io.aviso/pretty "0.1.34"]
                                  [vvvvalvalval/scope-capture "0.1.0"]]
                   :plugins [[io.aviso/pretty "0.1.34"]]
                   ;; need to add dev source path here to get user.clj loaded
                   :source-paths ["src" "dev"]
                   ;; for CIDER
                   ;; :plugins [[cider/cider-nrepl "0.12.0"]]
                   :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   ;; need to add the compliled assets to the :clean-targets
                   :clean-targets ^{:protect false} ["resources/public/test-web"
                                                     :target-path]}
             :test-common {:dependencies [[org.clojure/test.check "0.9.0"]
                                          [com.gfredericks/test.chuck "0.2.7"]
                                          [orchestra "2017.08.13"]
                                          [org.clojure/core.specs.alpha "0.1.24"]
                                          [com.stuartsierra/dependency "0.2.0"]
                                          [ring/ring-core "1.6.2"] ; required to make ring-spec work, may cause issues with figwheel?
                                          [ring/ring-spec "0.0.3"] ; to test specs
                                          [org.onyxplatform/onyx-spec "0.11.0.2"] ; to test specs
                                          ]}
             :test-web [:test-common
                        {:source-paths ["test"]
                         :dependencies [[figwheel-sidecar "0.5.14"]
                                        [karma-reporter "1.0.1"]]}]
             :cljs-repl {:dependencies [[com.cemerick/piggieback "0.2.2"]]}

             :clj-1.9.0-alpha17 {:dependencies [[org.clojure/clojure "1.9.0-alpha17"]]}
             :clj-1.9.0-alpha18 {:dependencies [[org.clojure/clojure "1.9.0-alpha18"]]}
             :clj-1.9.0-alpha19 {:dependencies [[org.clojure/clojure "1.9.0-alpha19"]]}
             :clj-1.9.0-beta1 {:dependencies [[org.clojure/clojure "1.9.0-beta1"]]}
             :cljs-1.9.562 {:dependencies [[org.clojure/clojurescript "1.9.562"]]}
             :cljs-1.9.908 {:dependencies  [[org.clojure/clojurescript "1.9.908"]]}
             :cljs-1.9.946 {:dependencies  [[org.clojure/clojurescript "1.9.946"]]}}
  :aliases {"run-tests-once" ["with-profile" "test-web" "cljsbuild" "once" "test"]
            "run-tests-auto" ["do"
                              ["with-profile" "test-web" "cljsbuild" "once" "test"]
                              ["with-profile" "test-web" "cljsbuild" "auto" "test"]]})
