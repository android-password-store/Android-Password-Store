# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

### Changed

- Changed the support level for Chrome Beta/Canary/Dev/Stable and Ungoogled Chromium to `PasswordFillAndSaveIfNoAccessibility`.

- Updated `androidx.annotation` to 1.1.0 and `androidx.autofill` to `1.2.0-alpha01`.

- The library now requires Kotlin 1.5.0 configured with `kotlinOptions.languageVersion = "1.5"`.

- Removed Bromite from supported Autofill browsers, since they [disable Android autofill](https://github.com/bromite/bromite/blob/master/FAQ.md#does-bromite-support-the-android-autofill-framework).

- Added [Styx](https://github.com/jamal2362/Styx) to supported Autofill browsers.

### Fixed

- Fix build warning from undeclared unsigned type use.

### Added

- Added the `PasswordFillAndSaveIfNoAccessibility` browser support level for Chromium-based browsers beyond v89.

- Added the german term for _username_ to the heuristic keywords.

- Non-native Autofill browsers are now deemed unsupported before Android 9.

## [1.0.0] - 2020-12-04

- Initial release

[1.0.0]: https://github.com/android-password-store/Android-Password-Store/commits/autofill-parser-v1.0.0/autofill-parser
