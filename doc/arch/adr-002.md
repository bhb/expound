# ADR 002: Locating non-conforming values in context (with conformers)

## Context

Expound currently has code to highlight the non-conforming value (NCV) within a larger value (the "context"). For instance, given the context `{:city "Denver", :state :CO}` with a spec requiring both city and state to be strings, Expound will print out:

```
   {:city ..., :state :CO}
                      ^^^

 should satisfy

   string?
```

However, the current solution requires that the non-conforming value exists atomically in the context. This assumption is not true if the spec uses a `conformer` to modify the value.

For instance, it is possible to use a conformer to verify a string with a regex spec:

```clojure
(s/def ::string-AB-seq (s/cat :a #{\A} :b #{\B}))

(s/def ::string-AB
  (s/and
   ;; conform as sequence (seq function)
   (s/conformer seq)
   ;; re-use previous sequence spec
   ::string-AB-seq))

(s/conform ::string-AB "AC")
```

In this case, the NCV is `\C` but context will not actually contain the character as an atomic value.

### How are conformers used in practice?

Since conformers are [not intended to be used for coercion](https://dev.clojure.org/jira/browse/CLJ-2116?focusedCommentId=45123&page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel#comment-45123), how do users use conformers in practice?

- As mentioned above, treating string as regex
- Treating string as collection of values, e.g. collection of pos-ints (this is less powerful, but perhaps more succinct than a regex)
- A string which actually represents a simple int (no collection)
- An int which should be string (e.g. an ID)
- [Converting any collection into a set](https://dev.clojure.org/jira/browse/CLJS-1919)
- [A string that really should be treated as a UUID](http://cjohansen.no/a-unified-specification/)

It's worth noting that the spec authors don't intend conformers to be used in this way.

> "The guide page intentionally does not mention conformers as we consider them to be primarily useful for writing new custom composite spec types (not for general data transformation)" - Alex Miller, #clojure-spec, Clojurians slack

### Implementation notes

At least in Clojure (not sure about CLJS), we do get access to the conformer via the 'pred', e.g.

`(clojure.spec.alpha/conformer expound.problems/numberify clojure.core/str)`

but this is only in the case where the conformer is included in the pred that fails - if the conforming happens at a level "above" the final failing predicate, we don't see it (see the example specs above)


```clojure
(s/explain-data ::string-AB "AC")

;;  #:clojure.spec.alpha{:problems [{:path [:b], :pred #{\B}, :val \C, :via [:expound.problems/string-AB :expound.problems/string-AB-seq], :in [1]}], :spec :expound.problems/string-AB, :value "AC"}
```

### Existing solution

We recursively walk the context data structure, using the "annotated" path (one with various records like `KeyPathSegment` and `KeyValuePathSegment` to indicate when, for example, the key of a map is invalid). When we reach the end of the path (i.e. there are no more path segments to traverse), we mark the value as "relevant" meaning it is the NCV.

## Possible solutions

### Modify the existing algorithm

We could modify the current algorithm to allow extensions to the `summary-form` function such that values could be walked in a custom way e.g. strings could be further walked by index of char.

This extension point is a bit tricky, since it's not enough to just mark the bad value, we also need to understand the position of the bad value in the context.

**Tradeoffs**

- \+ Minimal changes
- \+ We don't have a good sense of how conformers are used, so this defers work until we get more data
- \+ Works for treating single characters as invalid
- \- Premature generalization? I'm not sure this will always work
- \- Not sure it would work for treating groups of characters as invalid
- \- Error message is confusing in the collection->set case (or int to string) case because the NCV won't match the type at all.
- \- Error message is also confusing in some cases e.g. of course "a" is not an int, it's a string, but the issue is that's it's not an int when passed through conformer 'numberify'

### Always walk the conformed value

We could conform the value first, then use the path instead of "in"

**Tradeoffs**

- \+ Easier to walk data structure, since it's in the right structure
- \- Harder to understand error, since data structure does not match what was entered
- \- Very verbose in common cases like "alt" and "or", or regex operations.

### Don't support transformations via conformers, but at least give a useful error message

If we can't follow a path segment into a value, we could at least give a better error message.

**Tradeoffs**

- \+ Easier to understand than current error message
- \+ Avoids trying to support an unsupported feature
- \- Using conformers for transformation is pretty common, and I'd prefer to allow these users to use Expound
- \- No middle ground - users can't use Expound at all

### Don't support transformations via conformers out of the box, but provide a multi-method if users want to enable this

I think we could check a multimethod if no other patterns match - the multimethod could take the value and the index

**Tradeoffs**

- \+ Allows users who want to use conformers a path to work around the issue
- \- Adds implementation complexity in Expound
- \- If the value and the index are not unique (and they may not be, especially if multiple libraries are using Expound), then this strategy won't work. We'd also need some unique identifier like the spec name or something

### Just make sure the specific cases I know about don't crash

Hard-code solutions for things like treating strings like regex, etc, and not support a general mechanism

**Tradeoffs**

- \+ Solves the problem for the use-cases I know about
- \+ Allows those users to use Expound without changes
- \- Possible long tail of one-off bugs for an unsupported use case, although I can push back on specific instances
- \- Implmentation complexity

### Throw a more helpful exception, provide an example replacement for 'printer'

`expound/custom-printer` allows users to specify a `:value-str-fn`. I could throw a more useful exception when the default `value-str-fn` fails and provide an example of a custom implementation that works with specific conformers.

**Tradeoffs**

- \+ Gives users a more understandable error out of the box
- \+ Users who want to adapt to their custom use case can, and can share solutions
- \- May require a breaking change to `value-str-fn` signature to include all the relevant information (probably just want to pass the annotated problem)

## Decision

I'm going with the solution "Throw a more helpful exception, provide an example replacement for 'printer'".


## Status

Accepted
