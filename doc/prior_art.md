# Prior Art

Notes from reading other articles or blog posts about error messages.

## ["Finding and fixing errors" (Urn)](http://urn-lang.com/tutorial/03-finding-errors.html)

Urn prints line numbers and underlines error. 

Explanantions are printed to the right of `^^^`

## [Urn](https://github.com/SquidDev/urn)

Urn assertions show value of expressions below the expressions themselves 

```
> (import test ())
out = nil
> (affirm (eq? '("foo" "bar" "")
.              (string/split "foo-bar" "-")))
[ERROR] <stdin>:1 (compile#111{split,temp}:46): Assertion failed
(eq? (quote ("foo" "bar" "")) (string/split "foo-bar" "-"))
     |                        |
     |                        ("foo" "bar")
     ("foo" "bar" "")
 ```

Urn points of syntax errors (underlining good and bad stuff), e.g.

```
> (]
[ERROR] Expected ')', got ']'
  => <stdin>:[1:2 .. 1:2] ("]")
 1 │ (]
   │ ^... block opened with '('
 1 │ (]
   │  ^ ']' used here
> 
```

## ["Way, Way, Waaaay Nicer Error Messages!" (ReasonML)](https://reasonml.github.io/blog/2017/08/25/way-nicer-error-messages.html)

“We’ve four a bug for you!” is friendly but not minimalist. I imagine it would quickly become noise.

Line number and bad data colorized. 

Prints types and definitions “defined as” e.g. 

`jsPayload (defined as Js.t {* age : int, name : string})`

Also use color to demarcate regions of errors by colorizing headings

Tracks error messages in custom [repository](https://github.com/reasonml-community/error-message-improvement/issues).

## ["Shape of errors to come" (Rust)](https://blog.rust-lang.org/2016/08/10/Shape-of-errors-to-come.html)

Rust errors have numbers for more explanation E0499.

Explanation are inline. Combines source when lines for two different regions overlap.

Displays other “points of interest”

“Undefined or not in scope” - that's a good, concise error message.

Rust explain (based on error number) is not generic, it uses your example in explanation!

## ["Measuring the Effectiveness of Error Messages Designed for Novice Programmers" (Racket)](http://cs.brown.edu/~sk/Publications/Papers/Published/mfk-measur-effect-error-msg-novice-sigcse/)

DrRacket reports only one error at a time.

In this experiment, they record edits to analyze how effective errors are. 

No analysis in this article of what factors caused bad error messages.

## ["Mind Your Language: 
On Novices' Interactions with Error Messages" (Racket)](http://cs.brown.edu/~sk/Publications/Papers/Published/mfk-mind-lang-novice-inter-error-msg/paper.pdf)

> Yet, ask any experienced programmer about the quality of error messages in their programming environments, and you will often get an embarrassed laugh.

Users sense that error must be in highlighted region (although this is not always true!)

> We also noticed that students tended to look for a recommended course of action in the wording of the error message.

> For instance, once the error message mentions a missing part, students felt prompted to provide the missing part, though this might not be the correct fix.

> Error messages should not propose solutions. Even though some errors have likely fixes (missing close parentheses in particular places, for example), those fixes will not cover all cases

> Error messages should not prompt students towards incorrect edits

If function call and function don’t match, show both, because either could be source of problem. 

The terms "Expected" and "found" imply that function definition is correct, which may be misleading.

Could beginner mode ask questions if solution is ambiguous? Or provide multiple solutions?

> IDE developers should provide guides (not just documentation buried in some help menu) about the semantics of notations such as source highlighting.


## ["Error Message Conventions" (Racket)](https://docs.racket-lang.org/reference/exns.html)

Racket’s error message convention is to produce error messages with the following shape:

```
‹srcloc›: ‹name›: ‹message›;
 ‹continued-message› ...
  ‹field›: ‹detail›
  ...
```

## ["Compilers as Assistants" (Elm)](http://elm-lang.org/blog/compilers-as-assistants)

Elm can detect likely typos.

Elm hides unrelated fields in data.

Elm uses the term “mismatch”, or “does not match”

Avoids cascading errors using [this approach](https://news.ycombinator.com/item?id=9808317)

## ["Compiler Errors for Humans" (Elm)](http://elm-lang.org/blog/compiler-errors-for-humans)

"When we read code, color is a huge usability improvement”
"When we read prose, layout has a major impact on our experience."
