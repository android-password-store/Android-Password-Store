# Contribution Guidelines

:tada: First off, thanks for taking the time to contribute! :tada:

This document should be treated as suggestions, and not rules, on how to contribute to Android Password Store. All positive contribution is welcome, so please do not hesitate in pitching forth any ideas you have!

## Table of contents

- [Navigating the source code](#navigating-the-source-code)
- [Building the project](#building-the-project)
- [Things to do before you start writing code](#things-to-do-before-you-start-writing-code)

## Navigating the source code

The source code is split across 4 modules.

- `buildSrc` hosts the Gradle build logic for the project
- `autofill-parser` is the aptly named Autofill logic parser
- `openpgp-ktx` contains the glue code that enables APS to interact with OpenKeychain
- `app` is everything else that constitutes APS

In most scenarios, the `app` directory is where you'd be contributing changes to. While most of the code has been rewritten and documented, there are still gnarly "legacy" parts that might be challenging to understand at a glance. Please get in touch via the [Discussions](https://github.com/android-password-store/Android-Password-Store/discussions) page with any questions you have, and we'd love to explain and improve things.

We bundle a [`ignore-revs-file`](https://git-scm.com/docs/git-blame#Documentation/git-blame.txt---ignore-revs-fileltfilegt) to ensure `git blame` is not affected by noisy changes. To make use of this, run `git config blame.ignoreRevsFile .git-blame-ignore-revs` from inside this repository.

## Building the project

### Building with Gradle

This document assumes that you already have an Android development environment ready. If not, refer to Google's documentation on [installing Android Studio](https://developer.android.com/studio/install). APS will always build against the latest stable release of Android Studio, but you can use pre-release versions of the IDE should you desire so.

The app comes in two 'flavors', a FOSS-only **free** variant and a **nonFree** variant that contains proprietary Google dependencies to facilitate some additional features as documented [here](https://android-password-store.github.io/docs/users/build-types). Decide what flavor you want to build, then run the following command to generate a debug APK.

```shell
./gradlew assembleFreeDebug # for 'free' flavor
./gradlew assembleNonFreeDebug # for 'nonFree' flavor
```

You can find the generated APK at `app/build/outputs/apk/<variant>/debug/app-<variant>-debug.apk`.

# Things to do before you start writing code

If you're trying to fix a bug that already has an open issue, it's a good idea to drop a comment mentioning that you're working on a fix. If no open issue exists, ensure that you explain the bug you're fixing in some detail in the pull request body. This helps us as maintainers get a better sense of why you're making specific changes, and we might have insight into better ways of fixing the problem.

If you want to add a new feature, please file an issue first to gauge maintainer and community interest. Sometimes the feature might need adjustments before it is accepted, or it might not be something we want to add to APS at all. Proposing the feature in detail before working on it ensures that you build exactly what is necessary and not waste time and effort on aspects that would get rejected during review.
