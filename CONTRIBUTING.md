# Contribution Guidelines

:tada: First off, thanks for taking the time to contribute! :tada:

This document should be treated as suggestions, and not rules, on how to contribute to Android Password Store. All positive contribution is welcome, so please do not hesitate in pitching forth any ideas you have!

## Table of contents

- [Navigating the source code](#navigating-the-source-code)
- [Building the project](#building-the-project)
- [Things to do before you start writing code](#things-to-do-before-you-start-writing-code)

## Navigating the source code

The source code is split across 12 modules and 1 subproject.

- `build-logic` and its modules host the Gradle build logic for the project.
- `autofill-parser` is the aptly named parser for Android's Autofill structures that also deals with trust and feature detection for browsers. 
- `openpgp-ktx` contains the glue code that enables APS to interact with OpenKeychain.
- `coroutine-utils` and `coroutine-utils-testing` are helper libraries that allow for effective usage and testing of [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html).
- `crypto-common` is the foundation of our new, extensible cryptography APIs that adds the ability to introduce new cryptographic backends to APS with minimal effort.
- `crypto-pgpainless` is the first of our new backends that utilises `crypto-common` to offer PGP cryptography through the [PGPainless](https://gh.pgpainless.org/) library.
- `format-common` handles parsing the `pass` file format.
- `passgen/random` contains the default password generator.
- `passgen/diceware` is our new password generator that implements the [Diceware](https://theworld.com/~reinhold/diceware.html) algorithm.
- `sentry-stub` contains no-op variants of [Sentry](https://sentry.io/) APIs that we use to ensure the FOSS-only, telemetry-free variant of APS continues to compile in absence of Sentry dependencies.
- `ui-compose` has the theming code for building UI components in [Jetpack Compose](https://developer.android.com/jetpack/compose).
- `app` is everything else that constitutes APS.

In most scenarios, the `app` directory is where you'd be contributing changes to. While most of the code has been rewritten and documented, there are still gnarly "legacy" parts that might be challenging to understand at a glance. Please get in touch via the [Discussions](https://github.com/android-password-store/Android-Password-Store/discussions) page with any questions you have, and we'd love to explain and improve things.

We bundle a [`ignore-revs-file`](https://git-scm.com/docs/git-blame#Documentation/git-blame.txt---ignore-revs-fileltfilegt) to ensure `git blame` is not affected by noisy changes. To make use of this, run `git config blame.ignoreRevsFile .git-blame-ignore-revs` from inside this repository. GitHub will automatically use this file for the blames it renders on the website.

## Source code conventions

- Unless you're absolutely sure what you're doing, always prefer the `unsafeLazy` method over Kotlin's inbuilt `lazy` for lazily evaluated properties.
- For inflating a Fragment or Activity view, always use the [`viewBinding`](https://github.com/android-password-store/Android-Password-Store/blob/3a16d77db24b6d79d9d27ce3207d72a7a879f38a/app/src/main/java/dev/msfjarvis/aps/util/extensions/FragmentViewBindingDelegate.kt) extension.

## Building the project

### Building with Gradle

This document assumes that you already have an Android development environment ready. If not, refer to Google's documentation on [installing Android Studio](https://developer.android.com/studio/install). APS will always build against the latest stable release of Android Studio, but you can use pre-release versions of the IDE should you desire so.

The app comes in two 'flavors', a FOSS-only **free** variant and a **nonFree** variant that contains proprietary Google dependencies to facilitate some additional features as documented [here](https://android-password-store.github.io/docs/users/build-types). Decide what flavor you want to build, then run the following command to generate a debug APK.

```shell
./gradlew collectFreeDebugApks # for 'free' flavor
./gradlew collectNonFreeDebugApks # for 'nonFree' flavor
```

You can find the generated APK at `app/outputs`.

## Pre-push checks

The project enforces codestyle conventions and library API stability by virtue of a carefully curated Gradle build. To setup a Git pre-push hook to run them automatically, run `./gradlew installGitHooks`.

# Things to do before you start writing code

If you're trying to fix a bug that already has an open issue, it's a good idea to drop a comment mentioning that you're working on a fix. If no open issue exists, please file one with the full details of what the bug is and how you intend to resolve it, and only start implementation once a maintainer has triaged the issue and has signed off on your approach. This allows maintainers to be able to review your pull request faster when it arrives, and saves you redundant effort of having to make potentially large changes in response to code review.

If you want to add a new feature, please file an issue first to gauge maintainer and community interest. Sometimes the feature might need adjustments before it is accepted, or it might not be something we want to add to APS at all. Proposing the feature in detail before working on it ensures that you build exactly what is necessary and not waste time and effort on aspects that would get rejected during review.
