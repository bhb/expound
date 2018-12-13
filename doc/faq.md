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

