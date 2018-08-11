# Development

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

`lein with-profile test-web cljsbuild auto test`
`ls ./resources/public/test-web/test.js | entr -s 'sleep 1; bin/tests'`

## Running CLJ tests

`lein with-profile +test-common test`

or

`lein with-profile +test-common test-refresh :changes-only`

or (to see which tests are slow)

`lein with-profile +test-common eftest`

or (if you want to save a second or two)

`clj -Atest`

## Release

### clojars

Double check version is changed in `project.clj` and `lein deploy clojars`

### NPM

Double check version is changed in `package.json` and `npm publish --access=public`
