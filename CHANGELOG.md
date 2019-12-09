# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

### Fixed

- [Bug with printing failures for `multi-spec`s](https://github.com/bhb/expound/issues/122). Unfortunately, I had to remove some [output that was useful but not reliable in all cases](https://github.com/bhb/expound/blob/946f9268c8ed8db72a521b8077aa0926febf7916/src/expound/alpha.cljc#L240-L261)
- [Bug with registering message for set-based specs](https://github.com/bhb/expound/issues/101)
- [Bug with duplicate custom messages in `alt` or `or` specs](https://github.com/bhb/expound/issues/135)

## [0.8.1] - 2019-11-30

### Fixed

- [Bug with unnecessary dependency](https://github.com/bhb/expound/issues/171)

### Added

- New documentation comparing `clojure.spec` messages to Expound messages

## [0.8.0] - 2019-11-24

### Fixed
- [Bug printing specs with several `or` branches](https://github.com/bhb/expound/issues/93)
- [Bug with over-expanding spec forms inside a table of keys](https://github.com/bhb/expound/issues/155)
- [Bug with incorrectly grouping independent problems in a `keys` spec](https://github.com/bhb/expound/issues/165). Thanks [`@kelvinqian00`](https://github.com/kelvinqian00) for the fix!

### Added
- `expound` and `expound-str` now (optionally) accept the same options as `custom-printer` (e.g. `(expound int? "" {:theme :figwheel-theme})`)

### Changed
- New format for table of missing keys

## [0.7.2] - 2018-12-18

### Fixed
- Bugs with exceptions on specs that conform e.g. [`s/keys*`](https://github.com/bhb/expound/issues/112) and [`s/and`](https://github.com/bhb/expound/issues/102). Expound also now [print messages for specs that use `conform` for coercion](https://github.com/bhb/expound/issues/78).
- [ClassCastException when checking spec against sorted map](https://github.com/bhb/expound/issues/136)
- Incompatibilities with the new `explain-data` format introduced in ClojureScript 1.10.439 and `clojure.spec.alpha` 0.2.176


### Changed
- Replaced Codox docs with [cljdoc](https://cljdoc.xyz/) for [API docs](https://cljdoc.xyz/d/expound/expound)
- `clojure.spec` dependency updated to 0.2.168
- Dropped support for ClojureScript 1.9.562 and 1.9.946
- Deprecated `def` macro (use `defmsg` function instead)

## [0.7.1] - 2018-06-25

### Fixed
- [Bug with printing alternatives in 'or' or 'alt' specs](https://github.com/bhb/expound/issues/73)
- [Bug with missing elements in `cat` specs](https://github.com/bhb/expound/issues/79)

### Changed
- If a problem has a value for `:expound.spec.problem/type` key, `expound.alpha/problem-group-str` must be implemented for that value or Expound will throw an error.

## [0.7.0] - 2018-05-28

### Fixed
- [Use existing value of `ansi/*enable-color*`](https://github.com/bhb/expound/pull/98)

### Added
- Specs and docstrings for public API
- Codox site for documentation
- [3rd-party libraries can extend Expound by setting `:expound.spec.problem/type` on each `clojure.spec` problem and declaring a `defmethod` to implement custom printing](https://github.com/bhb/expound/pull/97/)

## [0.6.0] - 2018-04-26

### Fixed
- Bug with extra whitespace when "Revelant specs" are not printed

### Added
- [Optional colorized output i.e. "themes"](https://github.com/bhb/expound/issues/44)
- [`explain-results` and `explain-results-str` functions print human-optimized output for `clojure.spec.test.alpha/check` results](https://github.com/bhb/expound/issues/72)

## [0.5.0] - 2018-02-06

### Fixed
- [Bug with displaying errors for `s/or` specs](https://github.com/bhb/expound/issues/64)
- Bug where "should be one of..." values didn't display correctly

### Added
- Optional error messages for predicates

## [0.4.0] - 2017-12-16

### Fixed
- [Bug with including development HTML page in JAR](https://github.com/bhb/expound/issues/60)

### Added
- Table of keywords and specs for missing keys

### Changed
- Better error message for compound key clauses like `:req-un [(or ::foo ::bar)]`
- Better error message for failures in `cat` specs

## [0.3.4] - 2017-11-19

### Fixed

- [Bug with composing multi-specs in other specs](https://github.com/bhb/expound/issues/24)
- [Bug with explaining failures with NaN values](https://github.com/bhb/expound/issues/48)
- [Bug with printing specs which are anonymous functions](https://github.com/bhb/expound/issues/50)
- [Bug with set predicates on different branches of 'alt' or 'or' specs](https://github.com/bhb/expound/issues/36)

## [0.3.3] - 2017-11-02

### Fixed
- [Bug with non-function values that can be treated as functions](https://github.com/bhb/expound/issues/41)
- Multiple bugs when reporting assertion failures

## [0.3.2] - 2017-10-29

### Fixed
- Bug where duplicate predicates were printed twice
- [Bug with `fspec` specs](https://github.com/bhb/expound/issues/25)

## [0.3.1] - 2017-09-26

### Fixed
- [Bug with nested `map-of` or `key` specs](https://github.com/bhb/expound/issues/27)
- [Bug with `(coll-of)` specs with set values](https://github.com/bhb/expound/issues/31)

## [0.3.0] - 2017-09-05

### Added
- Configurable printers

### Fixed
- [Bug with using predicates as specs](https://github.com/bhb/expound/issues/20)

## [0.2.1] - 2017-08-16

### Fixed
- [Bug with including extraneous compiled Javascript in JAR file](https://github.com/bhb/expound/issues/16)

## [0.2.0] - 2017-08-14

### Added
- Support for [Orchestra](https://github.com/jeaye/orchestra) instrumentation

### Changed
- Pretty-print predicates
- Omit `clojure.core` and `cljs.core` prefix when printing predicates

### Fixed
- Append [newline to expound output](https://github.com/bhb/expound/issues/8)

## [0.1.2] - 2017-07-22

### Added
- [Support for instrumentation](https://github.com/bhb/expound/issues/4)
- [Support for Spec asserts](https://github.com/bhb/expound/issues/5)

## [0.1.1] - 2017-07-17

### Fixed
- [Bug with loading goog.string/format](https://github.com/bhb/expound/issues/3)

## 0.1.0 - 2017-07-12

### Added
- `expound` and `expound-str` functions.

[Unreleased]: https://github.com/bhb/expound/compare/v0.8.1...HEAD
[0.8.1]: https://github.com/bhb/expound/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/bhb/expound/compare/v0.7.2...v0.8.0
[0.7.2]: https://github.com/bhb/expound/compare/v0.7.0...v0.7.2
[0.7.1]: https://github.com/bhb/expound/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/bhb/expound/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/bhb/expound/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/bhb/expound/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/bhb/expound/compare/v0.3.4...v0.4.0
[0.3.4]: https://github.com/bhb/expound/compare/v0.3.3...v0.3.4
[0.3.3]: https://github.com/bhb/expound/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/bhb/expound/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/bhb/expound/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/bhb/expound/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/bhb/expound/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/bhb/expound/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/bhb/expound/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/bhb/expound/compare/v0.1.0...v0.1.1
