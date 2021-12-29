# Development

## Socket REPL

`clj -J-Dclojure.server.repl="{:port 5555 :accept clojure.core.server/repl}"`

Then connect with `inf-clojure-connect`

## Clojurescript REPL (node)

```
lein with-profile +test-web,+cljs-repl repl
```

```
M-x cider-connect
(require 'cljs.repl.node)
(cider.piggieback/cljs-repl (cljs.repl.node/repl-env))
```

## Running CLJS tests

### Karma

`lein with-profile test-web cljsbuild auto test`
`ls ./resources/public/test-web/test.js | entr -s 'sleep 1; bin/tests'`

## Running CLJ tests

`lein with-profile +test-common test`

or

`lein with-profile +test-common test-refresh :changes-only`

or (if you want to save a second or two)

`clj -Atest:test-deps`

or

`bin/kaocha --watch --plugin profiling`

## Code coverage

`bin/kaocha --plugin cloverage --cov-exclude-call expound.alpha/def`

## Updating packages

You must have `lein-ancient` installed in your `~/.lein/profiles.clj`

e.g. `lein ancient :all` (or in my setup, `lein with-profile +tools ancient :all`

## Readability and linting

`./bin/inconsistent-aliases` shows namespace aliases that are different across the codebase.

`./bin/lint` lints the code with `joker`

`lein hiera` generates a graph of namespace dependencies

## Release

1. Update version in `project.clj`
1. Update `CHANGELOG.md` (including section for release and links at bottom)
1. Update version in `README.md`
1. Update version in `doc/compatibility.md`
1. Update version in `package.json`
1. `npm install`
1. `git tag -a v0.7.2 -m "version 0.7.2"`
1. `git push --tags`


### clojars

Double check version is changed in `project.clj` and `lein deploy clojars` (use deploy token instead of password)

### NPM

Double check version is changed in `package.json` and `npm publish --access=public`
