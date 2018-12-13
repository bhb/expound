# FAQ

## How do I use Expound to print all spec errors?

### Using `set!`

In most cases, you'll want to use `set!` to install the Expound printer:

```clojure
(set! s/*explain-out* expound/printer)
```

### Using `alter-var-root`

If you using Expound within a REPL, `alter-var-root` will not install the Expound printer properly - you should use `set!` as described above.

If you using the Expound printer in a non-REPL environment, `set!` will only change s/*explain-out* in the *current thread*. If your program spawns additional threads (e.g. a web server), you can set s/*explain-out* for all threads with:


```clojure
(alter-var-root #'s/*explain-out* (constantly expound/printer))
```

`set!` will also *not work* within an uberjar. Use `alter-var-root` instead.

## Why don't I see Expound errors for instrumentation or macroexpansion errors?

As of `clojure.spec.alpha` 0.2.176 and ClojureScript 1.10.439 (which contains `cljs.spec.alpha`), spec no longer includes the spec error in the exception message. Instead, the error will now include more data about the spec error, but this isn't printed by default in older REPLs.

Clojure 1.10.0 updates the REPL to print this new error data. As of this writing, the Clojurescript REPL is in the process of being updated.

To summarize, spec errors will be printed at the REPL with these combinations:

* `clojure.spec.alpha` 0.2.168 will work with Clojure 1.9.0
* `clojure.spec.alpha` 0.2.176 will work with Clojure 1.10.0
* Clojurescript 1.10.238 and 1.10.339