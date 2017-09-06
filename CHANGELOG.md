# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

### Added
- Added configurable printers

### Fixed
- Fixed [bug with using predicates as specs](https://github.com/bhb/expound/issues/20)

## [0.2.1] - 2017-08-16

### Fixed
- Fixed [bug with including extraneous compiled Javascript in JAR file](https://github.com/bhb/expound/issues/16)

## [0.2.0] - 2017-08-14

### Changed
- Append [newline to expound output](https://github.com/bhb/expound/issues/8)
- Pretty print predicates
- Omit `clojure.core` and `cljs.core` prefix when printing predicates
- Added support for [Orchestra](https://github.com/jeaye/orchestra) instrumentation

## [0.1.2] - 2017-07-22

### Added
- Added [support for instrumentation](https://github.com/bhb/expound/issues/4)
- Added [support for Spec asserts](https://github.com/bhb/expound/issues/5)

## [0.1.1] - 2017-07-17

### Fixed
- Fixed [bug with loading goog.string/format](https://github.com/bhb/expound/issues/3)

## 0.1.0 - 2017-07-12

### Added
- Added `expound` and `expound-str` functions.

[Unreleased]: https://github.com/bhb/expound/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/bhb/expound/compare/v0.1.0...v0.1.1
[0.1.2]: https://github.com/bhb/expound/compare/v0.1.1...v0.1.2
[0.2.0]: https://github.com/bhb/expound/compare/v0.1.2...v0.2.0
[0.2.1]: https://github.com/bhb/expound/compare/v0.2.0...v0.2.1

