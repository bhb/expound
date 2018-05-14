# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

### Added
- Specs and docstrings for public API
- Codox site for documentation

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

[Unreleased]: https://github.com/bhb/expound/compare/v0.6.0...HEAD
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
