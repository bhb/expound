# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

#### Fixed
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

[Unreleased]: https://github.com/bhb/expound/compare/v0.3.1...HEAD
[0.3.1]: https://github.com/bhb/expound/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/bhb/expound/compare/v0.2.1...v0.3.0
[0.2.1]: https://github.com/bhb/expound/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/bhb/expound/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/bhb/expound/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/bhb/expound/compare/v0.1.0...v0.1.1
