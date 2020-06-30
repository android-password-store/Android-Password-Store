# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Fixed

-   Folder names that were very long did not look right
-   Error message for wrong SSH/HTTPS password now looks cleaner

### Added

-   TOTP support is reintroduced by popular demand. HOTP continues to be unsupported and heavily discouraged.
-   Initial support for detecting and filling OTP fields with Autofill
-   Importing TOTP secrets using URI codes

## [1.9.1] - 2020-06-28

### Fixed

-   Remember passphrase option did not work with old-style keys (generated either before 2019 or by passing `-m PEM` to new versions of OpenSSH)

### Added

-   Add GNU IceCatMobile to the list of supported browsers for Autofill

## [1.9.0] - 2020-06-21

### Fixed

-   'Draw over other apps' permission dialog opens when attempting to use Oreo Autofill
-   Old app shortcuts are now removed when the local repository is deleted

### Added

-   Completely revamped decypted password view
-   Add support for better, more secure Keyex's and MACs with a brand new SSH backend
-   Allow manually marking domains for subdomain-level association. This will allow you to keep separate passwords for `site1.example.com` and `site2.example.com` and have them show as such in Autofill.
-   Provide better messages for OpenKeychain errors
-   Rename passwords and categories

### Changed

-   **BREAKING**: Remove support for HOTP/TOTP secrets - Please use FIDO keys or a dedicated app like [Aegis](https://github.com/beemdevelopment/Aegis) or [andOTP](https://github.com/andOTP/andOTP)
-   Reduce Autofill false positives on username fields by removing "name" from list of heuristic terms
-   Reduced app size
-   Improve IME experience with server config screen
-   Removed edit password option from long-press menu.
-   Batch deletion now does not require manually confirming for each password
-   Better commit messages on password deletion

## [1.8.1] - 2020-05-24

### Fixed

-   Don't strip leading slash from repository paths

## [1.8.0] - 2020-05-23

### Added

-   Allow user to abort password move when it is replacing an existing file
-   Allow setting a default username for Autofill
-   Add no authentication mode for working with public repositories

### Changed

-   More UI related tweaks, changes and improvements
-   Improved error messages and internal logic for server configuration

### Fixed

-   Add the following fields to encrypted username detection: user, account, email, name, handle, id, identity.
-   Improved detection of broken or incomplete git repositories
-   Better UX flow for storage permissions

## [1.7.2] - 2020-04-29

### Added

-   Settings option to enable debug logging

### Changed

-   SSH Keygen UI was improved
-   Default key length for SSH Keygen is now 4096 bits
-   Settings items were rearranged and cleaned up
-   Autofill icons in dark mode are now more legible

### Fixed

-   Failure to detect if repository was not cloned which broke Git operations
-   Search results were inaccurate if root directory's name started with a dot (.)
-   Saving git username and email did not provide user-facing confirmation

## [1.7.1] - 2020-04-23

### Fixed

-   Autofill message does not show OK button when many browsers are installed
-   Autofill message does not get marked as shown when dismissed
-   App crashes when using type-independent sort
-   Storage permission not requested when using existing external repository

## [1.7.0] - 2020-04-21

### Added

-   Oreo Autofill support
-   Securely remember HTTPS password/SSH key passphrase

### Fixed

-   Text input box theming
-   Password repository held in non-hidden storage no longer fails
-   Remove ambiguous and confusing URL field in server config menu
    and heavily improve UI for ease of use.

## [1.6.0] - 2020-03-20

### Added

-   Copy implicit username (password filename) by long pressing
-   Create xkpasswd style passwords
-   Swipe on password list to synchronize repository

### Fixed

-   Resolve memory leaks on password decryption
-   Can't delete folders containing a password

## [1.5.0] - 2020-02-21

### Added

-   Fast scroller with alphabetic hints
-   UI button to create new folders
-   Option to directly start searching when opening the app
-   Option to always search from root folder regardless of the currently open folder

### Changed

-   Logging is now enabled in release builds
-   Searching now shows folders as well as the passwords inside them

### Fixed

-   OpenKeychain errors cause app crash

## [1.4.0] - 2020-01-24

### Added

-   Add save-and-copy button
-   Dark theme
-   Setting to save OpenKeychain auth id
-   Add number of passwords to folders

### Changed

-   Updated UI design and iconograph
-   Biometric authentication
-   Use new OpenKeychain integration library

### Fixed

-   Snackbars showing behind keyboards

## [1.3.2] - 2018-12-23

### Changed

-   Improve French translation.

### Fixed

-   Extra field is multi-line.

## [1.3.1] - 2018-10-18

### Fixed

-   Fix default sort order bug.

## [1.3.0] - 2018-10-16

### Added

-   Allow app to be installed on external media (SD card).
-   Change password sort order.
-   Display HOTP code if present.
-   Open search view on keyboard press.

### Changed

-   Use adaptive icon.
-   Password entry is more secure.
-   Clean paths on password list view.
-   Improve Chinese translation.
-   Don't show hidden files and directories.

### Fixed

-   Fix clipboard clearing.
-   Wrap long passwords.

## 1.2.0.75 - 2018-05-31

### Added

-   Add Arabic translation.
-   Warn user that remembering SSH passphrase is currently insecure.

### Changed

-   Update Japanese assets.

### Fixed

-   Fix elements overlapping.

[Unreleased]: https://github.com/android-password-store/Android-Password-Store/compare/1.9.1...HEAD

[1.9.1]: https://github.com/android-password-store/Android-Password-Store/compare/1.9.0...1.9.1

[1.9.0]: https://github.com/android-password-store/Android-Password-Store/compare/1.8.1...1.9.0

[1.8.1]: https://github.com/android-password-store/Android-Password-Store/compare/v1.8.0..v1.8.1

[1.8.0]: https://github.com/android-password-store/Android-Password-Store/compare/v1.7.2..v1.8.0

[1.7.2]: https://github.com/android-password-store/Android-Password-Store/compare/v1.7.1..v1.7.2

[1.7.1]: https://github.com/android-password-store/Android-Password-Store/compare/v1.7.0..v1.7.1

[1.7.0]: https://github.com/android-password-store/Android-Password-Store/compare/v1.6.0..v1.7.0

[1.6.0]: https://github.com/android-password-store/Android-Password-Store/compare/v1.5.0..v1.6.0

[1.5.0]: https://github.com/android-password-store/Android-Password-Store/compare/v1.4.0...v1.5.0

[1.4.0]: https://github.com/android-password-store/Android-Password-Store/compare/v1.3.0...v1.4.0

[1.3.2]: https://github.com/android-password-store/Android-Password-Store/compare/v1.3.1...v1.3.2

[1.3.1]: https://github.com/android-password-store/Android-Password-Store/compare/v1.3.0...v1.3.1

[1.3.0]: https://github.com/android-password-store/Android-Password-Store/compare/v1.2.0.75...v1.3.0
