# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

### Changed

- Changed the support level for Chrome Beta/Canary/Dev/Stable, Bromite and Ungoogled Chromium to `PasswordFillAndSaveIfNoAccessibility`.

- Updated `androidx.annotation` to 1.1.0 and `androidx.autofill` to `1.2.0-alpha01`.

- The library now requires Kotlin 1.5.0 configured with `kotlinOptions.languageVersion = "1.5"`.

### Fixed

- Fix build warning from undeclared unsigned type use.

### Added

- Added the `PasswordFillAndSaveIfNoAccessibility` browser support level for Chromium-based browsers beyond v89.

- Added the german term for _username_ to the heuristic keywords.

- Non-native Autofill browsers are now deemed unsupported before Android 9.

## [1.0.0] - 2020-12-04

- Initial release

[1.0.0]: https://github.com/android-password-store/Android-Password-Store/commits/autofill-parser-v1.0.0/autofill-parser
