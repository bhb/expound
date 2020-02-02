# ADR 003: Easier editing of error messages

## Context

### Expound only returns strings

Besides a fixed set of [printer options](https://github.com/bhb/expound#printer-options), users can only manipulate error messages by parsing and manipulating the string returned by `expound-str`.

### Expound locates "problem" values within a "context" value

Expound error messages display the "problem" value within the entire "context" value. For instance, if `"456"` should be an integer within `{:ids [123 "456" 789]}`, Expound will print:

```
  {:ids [... "456" ...]}
             ^^^^^

should satisfy

  int?
```

The entire context value is invalid because the problem value is invalid.

## Problem Statement

Manipulating Expound error messages by parsing strings is error prone and brittle. Expound does not consider the format of the error messages to be part of the API and so changes that break client string-parsing code is likely to break at some point.

Why might a user want to manipulate the error message?

* Shrinking the context value to create shorter error messages
* Shrinking the problem value to create shorter error messages
* Adding data to the context value to help locate the problem value
* [Adding additional information](https://github.com/bhb/expound/issues/148)

### Specific use cases

#### [#129](https://github.com/bhb/expound/issues/129)

When a map matches some user-defined pattern (e.g. contains some keys) and/or when the problem is of a certain problem type (e.g. the "missing keys" type, which currently shows a table of missing keys), Expound should map information that is not relevant.

For instance, it's possible the following error message is unnecessarily long:

```
  {:tag ...,
   :children [{:tag ..., :children [{:tag :group, :props {:on-tap {}}}]}]}
                                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

should satisfy

  nil?
```

since the nested value `:on-tap {}` adds no information about the non-nilness of `{:tag :group, :props {:on-tap {}}}`

Considerations:

* For some problem types, hiding the number of keys could hide relevant information - e.g. a predicate that checked length of map
* Some information about the value may not just be used to understand why it failed predicate, but also to locate the value. For instance, consider an array with many maps, in which every map contains an `:id` key - we may not want to hide the `:id` value if it helps user locate the invalid value.

#### [#110](https://github.com/bhb/expound/issues/110)

When sequences are long, the error message is long due to repeated "..." values. For instance,

```
  [...
   ...
   ...
   ...
   ...
   ...
   ...
   ...
   33 ]
   ^^

should satisfy

  string?
```

could potentially be minimized to:

```
  [...
   < 7 more >
   33 ]
   ^^

should satisfy

  string?
```

Considerations:

* This depends on value, not necessarily on type of problem
* For certain values with many values that are similar, only showing the failing value is insufficient, but the `print-valid-values` option shows too much data.
* This could be solved via a user-provided whitelist of values to show
* Perhaps add notion of "distance" from problem? Does this depend entirely on type of data?

#### [#108](https://github.com/bhb/expound/issues/108)

When value is very large (especially nested values), printing the "upper" problem values creates very long error messages.

#### [#148](https://github.com/bhb/expound/issues/148)

Given the values and the error, allow user to inject content between value and the error (or really, anywhere)

#### "Embed spec errors" (No issue filed)

> My case: user creates a router, a lot of rules are run (syntax, conflicts, dependencies, route data specs etc.). Spec validation is just one part and… I would like to return enought context information what and where it failed.  Currently:

```
(require '[reitit.core :as r])
(require '[clojure.spec.alpha :as s])
(require '[expound.alpha :as e])

(s/def ::role #{:admin :manager})
(s/def ::roles (s/coll-of ::role :into #{}))

(r/router
  ["/api" {:handler identity
           ::roles #{:adminz}}]
  {::rs/explain e/expound-str
   :validate rs/validate-spec!})
; CompilerException clojure.lang.ExceptionInfo: Invalid route data:
;
; -- On route -----------------------
;
; "/api"
;
; -- Spec failed --------------------
;
; {:handler ..., :user/roles #{:adminz}}
;                              ^^^^^^^
;
; should be one of: `:admin`,`:manager`
;
```

Given some context, add additional context around the spec.

#### Customize printing for record or value (No issue filed)

> Can I prevent Expound from printing a certain record? It generates something like 140k lines of output which is not helpful at all. I defined a `print-method` so that prints something simple with `prn` but that does not seem to affect Expound.

Given some value (or properties of value), allow user to specify custom output (to shrink size of output).

### Non-problems

* Minor formatting tweaks to error messages e.g. changes in indentation
* No requests to change the specific characters i.e. hide values with ",,," instead of "." or underline with "---" instead of "^^^"

## Constraints

It's not clear that users would use an solution that allowed for easier editing of error messages: they might instead just want more fixes or features that solve the problem automatically.

If Expound tries to do the right thing in all cases, it will get complex to maintain and to use since users will have to guess how/why it's showing or omitting certain information.

## Prior Art

### [Hiccup](https://github.com/weavejester/hiccup)

Hiccup is a data format for representing HTML

**Tradeoffs in the context of this problem:**

* \+ Optional second element (map) is a place to add classes and IDs, which add semantic meaning
* \- Not intended to cover aspects like indentation and whitespace, since HTML/CSS is responsible for that

### [Fipp](https://github.com/brandonbloom/fipp/blob/master/doc/primitives.md) 
Fipp is a better pretty printer for Clojure and ClojureScript

**Tradeoffs in the context of this problem:**

* \+ Focused on pretty printing, so primitives reflect that
* \- No place to add meaning which would allow users to find content based on ID or class
* ? Potentially more general than this particular problem requires
* ? Fipp is very fast, but Expound doesn't require that much speed

### [Clojure pretty printer](https://clojure.github.io/clojure/doc/clojure/pprint/PrettyPrinting.html)

Expound currently uses Clojure's built-in pretty-print to display values.

> More advanced formats, including formats that don't look like Clojure data at all like XML and JSON, can be rendered by creating custom dispatch functions.

- <https://clojuredocs.org/clojure.pprint>

Flowchart of how to use <https://stackoverflow.com/a/49455269>

* \- Need slightly different implementation for ClojureScript and Clojure
* \- Seems to only work on types, need a new type for each role in data structure
* \+ Wouldn't have to worry about writing layout engine
* \+ Configurable by users if they didn't like how Expound formats tree

## Possible solutions: general approach

### Include Fipp

Expound could depend on Fipp for pretty-printing. All functions that generate a string would return Fipp data structures.

**Tradeoffs**

* \+ Don't need to reimplement algorithm
* \+ Can rely on existing documentation for data primitives
* ? Does Fipp provide code to extract/manipulate the primitives? Do we want to provide this so Expound clients can manipulate data structure?
* \- Take on two new deps: Fipp and `org.clojure/core.rrb-vector`.
  *  Fipp is well-maintained, but there are complex issues like [this](https://github.com/brandonbloom/fipp/issues/60)
* \- Doesn't provide way to add semantic information about elements beyond formatting
* \- Requires a wrapping layer if we don't want clients to be exposed to Fipp internals

### Write a custom data description

I could come up with my own custom data language to describe the errors.

* \- Need to write more code
* \+ Can make it custom to error messages
* \- Need to write more documentation for format
* \+ Creates a more stable API for changes (format is stable, although content of strings are not)
* \- Creates a more complex API (more functions)
* \- Requires more testing for backwards compatibility
* \+ Speeds up building internal features
* \+ Provides solution for advanced users that doesn't rely on string manipulation
   * Encourages libraries to use Expound as dependency
   * Provides temporary solution for advanced use cases (can still build configuration if I want, but not necessary for all cases)
   * May provide long-term fix for use cases I don't want to move into Expound

### Don't expose data description

I could make an internal data API that is not exposed to users.

**Tradeoffs**

* \+ No need to add new functions (which won't be used my most users anyway)
* \+ Still allows faster iteration on internal features
* \- If I want to solve the issues linked above, I'll need to either add complexity to the Expound rules OR add more configuration
   * Additional configuration adds potential for bugs as number of combinations grows
   * I'm not sure users use the configuration I expose now
* \+ No additional API to test for breaking changes
* \- String format becomes an API that users cannot rely on
* \- Does not encourage tools to use Expound internally

## Possible solutions: data langauge for displaying problem and context values

### Return original value plus mapping from `:in` path to value

It's tricky to embed metadata within the original data, so we provide a lookup table that provides information to assist with formatting.

We also need to pass along the category of failure because we might alter the output.

The deal-breaker here is that I can't meaningfully traverse the original data structure in order and get relevant metadata. Metadata needs to be included because there's no mechanism to look it up later.

### Use Clojure metadata

[Clojure's built-in metadata](https://clojure.org/reference/metadata) can be added to collections and symbols, whereas problem and context values can be (and contain) scalars, so metadata won't work for this problem.

### Wrap all data in custom metadata

The format is TBD, but the idea is to wrap each piece of data in a map that adds additional attributes that are used to control how it is displayed.

* "Role": is the data a collection, a key, an index, or a scalar?
    * A collection is always printed
    * A key is always printed (this helps locate the problem value)
    * A index *may* be printed (if it is on the `:in` path of the problem value)
    * A scalar will be printed if it is the problem value, otherwise it will not
* "Distance": How "far away" is the value from the problem value?
  * Generally values that are closer will be printed to help locate the problem value
* "Depth": How "deep" is the value relative to the problem value?
   * When printing the problem value, we may want to only printed nested values to a certain depth

## Decision

## Status

Draft
