# Changelog
All notable changes to this project will be documented in this file.

## Unreleased

### Added
- Copy implicit username (password filename) by long pressing
- Create xkpasswd style passwords
- Swipe on password list to synchronize repository

### Fixed
- Can't delete folders containing a password

## [1.5.0] - 2020-02-21

### Added
- Fast scroller with alphabetic hints
- UI button to create new folders
- Option to directly start searching when opening the app
- Option to always search from root folder regardless of the currently open folder

### Changed
- Logging is now enabled in release builds
- Searching now shows folders as well as the passwords inside them

### Fixed
- OpenKeychain errors cause app crash

## [1.4.0] - 2020-01-24

### Added
- Add save-and-copy button
- Dark theme
- Setting to save OpenKeychain auth id
- Add number of passwords to folders

### Changed
- Updated UI design and iconograph
- Biometric authentication
- Use new OpenKeychain integration library

### Fixed
- Snackbars showing behind keyboards

## [1.3.2] - 2018-12-23

### Changed
- Improve French translation.

### Fixed
- Extra field is multi-line.

## [1.3.1] - 2018-10-18

### Fixed
- Fix default sort order bug.

## [1.3.0] - 2018-10-16

### Added
- Allow app to be installed on external media (SD card).
- Change password sort order.
- Display HOTP code if present.
- Open search view on keyboard press.

### Changed
- Use adaptive icon.
- Password entry is more secure.
- Clean paths on password list view.
- Improve Chinese translation.
- Don't show hidden files and directories.

### Fixed
- Fix clipboard clearing.
- Wrap long passwords.

## 1.2.0.75 - 2018-05-31

### Added
- Add Arabic translation.
- Warn user that remembering SSH passphrase is currently insecure.

### Changed
- Update Japanese assets.

### Fixed
- Fix elements overlapping.


[Unreleased]: https://github.com/zeapo/android-password-store/compare/v1.5.0...HEAD
[1.5.0]: https://github.com/zeapo/android-password-store/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/zeapo/android-password-store/compare/v1.3.0...v1.4.0
[1.3.2]: https://github.com/zeapo/android-password-store/compare/v1.3.1...v1.3.2
[1.3.1]: https://github.com/zeapo/android-password-store/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/zeapo/android-password-store/compare/v1.2.0.75...v1.3.0
