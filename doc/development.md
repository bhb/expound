# Development

## Socket REPL

`clj -J-Dclojure.server.repl="{:port 5555 :accept clojure.core.server/repl}"`

Then connect with `inf-clojure-connect`

## Clojurescript REPL

```
lein with-profile +test-web,+cljs-repl repl
```

```
M-x cider-connect
(use 'figwheel-sidecar.repl-api)
(start-figwheel!)
(cljs-repl)
```

```
open http://localhost:3446/index.html
```

## Running CLJS tests

### Karma

`lein with-profile test-web cljsbuild auto test`
`ls ./resources/public/test-web/test.js | entr -s 'sleep 1; bin/tests'`

### Figwheel

`clojure -A:test:figwheel-repl`

Open <http://localhost:9500/figwheel-extra-main/auto-testing>


## Running CLJ tests

`lein with-profile +test-common test`

or

`lein with-profile +test-common test-refresh :changes-only`

or (to see which tests are slow)

`lein with-profile +test-common eftest`

or (if you want to save a second or two)

`clj -Atest:test-deps`

or

`bin/kaocha --watch --plugin profiling`

## Code coverage

`bin/kaocha --plugin cloverage --cov-exclude-call expound.alpha/def`

## Readability and linting

`./bin/inconsistent-aliases` shows namespace aliases that are different across the codebase.

`./bin/lint` lints the code with `joker`

`lein hiera` generates a graph of namespace dependencies

## Release

### clojars

Double check version is changed in `project.clj` and `lein deploy clojars`

### NPM

Double check version is changed in `package.json` and `npm publish --access=public`
