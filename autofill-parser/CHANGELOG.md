# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

### Added

- [Mull](https://divestos.org/pages/our_apps#mull) is now supported as an Autofill-capable browser when installed from the divestos.org [repo](https://divestos.org/fdroid/official/).

## [1.1.2]

### Added

- [Vivaldi](https://play.google.com/store/apps/details?id=com.vivaldi.browser) is now supported as an Autofill-capable browser.
- [Vanadium](https://github.com/GrapheneOS/Vanadium) is now supported as an Autofill-capable browser.
- `mail` is now included in the heuristics for username fields

### Changed

- Dependency updates
    - `androidx.core:core-ktx:1.10.1`
    - `androidx.autofill:autofill:1.2.0-beta01`

## [1.1.1]

### Added

- [Mull](https://f-droid.org/packages/us.spotco.fennec_dos/) is now supported as an Autofill-capable browser.
- Raise target SDK to 31 

## [1.1.0]

### Changed

- Changed the support level for Chrome Beta/Canary/Dev/Stable, Bromite and Ungoogled Chromium to `PasswordFillAndSaveIfNoAccessibility`.

- Updated `androidx.annotation` to 1.3.0 and `androidx.autofill` to `1.2.0-beta01`.

- The library now uses Kotlin 1.6.10 and Coroutines 1.6.0.

- Added [Styx](https://github.com/jamal2362/Styx) to supported Autofill browsers.

- The dependency on [timberkt](https://github.com/ajalt/timberkt) has been replaced with [logcat](https://github.com/square/logcat).

- Updated `publicsuffixes` list to the latest version as of Dec 18 2021.

### Fixed

- Fix build warning from undeclared unsigned type use.

### Added

- Added the `PasswordFillAndSaveIfNoAccessibility` browser support level for Chromium-based browsers beyond v89.

- Added the german term for _username_ to the heuristic keywords.

- Non-native Autofill browsers are now deemed unsupported before Android 9.

## [1.0.0] - 2020-12-04

- Initial release

[1.0.0]: https://github.com/android-password-store/Android-Password-Store/commits/autofill-parser-v1.0.0/autofill-parser

[1.1.0]: https://github.com/android-password-store/Android-Password-Store/commits/autofill-parser-v1.1.0/autofill-parser

[1.1.1]: https://github.com/android-password-store/Android-Password-Store/commits/autofill-parser-v1.1.1/autofill-parser

[1.1.2]: https://github.com/android-password-store/Android-Password-Store/commits/autofill-parser-v1.1.2/autofill-parser
