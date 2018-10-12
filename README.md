Password Store
========
[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Android--Password--Store-blue.svg?style=flat)](https://android-arsenal.com/details/1/1208)
[![Build Status](https://travis-ci.org/zeapo/Android-Password-Store.svg?branch=travis)](https://travis-ci.org/zeapo/Android-Password-Store)
[![Backers on Open Collective](https://opencollective.com/Android-Password-Store/backers/badge.svg)](#backers) [![Sponsors on Open Collective](https://opencollective.com/Android-Password-Store/sponsors/badge.svg)](#sponsors) 

**Donations**: 
[![Flattr this git repo](http://api.flattr.com/button/flattr-badge-large.png)](https://flattr.com/submit/auto?user_id=zeapo&url=https://github.com/zeapo/Android-Password-Store&title=Android-Password-Store&language=en&tags=github&category=software) or bitcoin at `1H1Z1NPTrR5Cej9bKV3Hu4f5WJZYtkbpox`

This application tries to be 100% compatible with [pass](http://www.passwordstore.org/)

You can install the application from:

- [F-Droid](https://f-droid.org/repository/browse/?fdid=com.zeapo.pwdstore) (the prefered way)
- [Play Store](https://play.google.com/store/apps/details?id=com.zeapo.pwdstore) (always lags behind)

Pull requests are more than welcome (see [TODO](https://github.com/zeapo/Android-Password-Store/projects/1#column-228844)).


Features
========
- Clone an existing pass repository (ssh-key and user/pass support)
- List the passwords
- Handle the directories as categories
- Decrypt the password files (first line is the password, the rest is extra data)
- Add a new password to the current category (or no category if added at the root)
- Pull and Push changes to the remote repository
- Ability to change remote repository info


How-To
======
See the [wiki](https://github.com/zeapo/Android-Password-Store/wiki/First-time-setup) for a newer written version of the following gif walkthrough

FAQ
====

**Q:** What kind of repository can I clone from?

**A:** Make sure to only clone from bare repositories (see [git-clone(1)](http://git-scm.com/docs/git-clone) for how to create a bare repository from an existing one). Otherwise the clone will fail.

**Q:** I get a "Permission Denied" error when trying to import my ssh-key, why?

**A:** ssh-key files are usually created with permissions set to `600`, meaning that only the creator of this key has the right to read from it. The application needs a read access, at least temporarily, make the permissions to `644`, import the key, then set them back to `600`.

**Q** I get the error *No encrypted data with known secret key found in stream*

**A** In OpenKeyChain **(under the left drawer) Apps > Password Store > Accounts > (select the account) > Account key** select the key used to encrypt your passwords.

Community
=========

Ways to get in touch:

- [Github issues](https://github.com/zeapo/Android-Password-Store/issues), use it if you have a bug report, you do not understand how something works or you want to submit a feature request.


Libraries
=========
Libraries that this project uses:

- [OpenKeyChain](https://github.com/open-keychain/open-keychain) for encryption and decryption of passwords.
- [JGit](http://www.eclipse.org/jgit/) git library.
- [Apache's FileUtils](https://commons.apache.org/proper/commons-io/) for file manipulations.

## Contributors

This project exists thanks to all the people who contribute. Want to contribute? See if you can [find an issue](https://github.com/zeapo/Android-Password-Store/issues?q=is%3Aissue+is%3Aopen+sort%3Aupdated-desc) you wanna close, then send a PR!
<a href="https://github.com/zeapo/Android-Password-Store/graphs/contributors"><img src="https://opencollective.com/Android-Password-Store/contributors.svg?width=890&button=false" /></a>


## Backers

Thank you to all our backers! 🙏 [[Become a backer](https://opencollective.com/Android-Password-Store#backer)]

<a href="https://opencollective.com/Android-Password-Store#backers" target="_blank"><img src="https://opencollective.com/Android-Password-Store/backers.svg?width=890"></a>


## Sponsors

Support this project by becoming a sponsor. Your logo will show up here with a link to your website. [[Become a sponsor](https://opencollective.com/Android-Password-Store#sponsor)]

<a href="https://opencollective.com/Android-Password-Store/sponsor/0/website" target="_blank"><img src="https://opencollective.com/Android-Password-Store/sponsor/0/avatar.svg"></a>
<a href="https://opencollective.com/Android-Password-Store/sponsor/1/website" target="_blank"><img src="https://opencollective.com/Android-Password-Store/sponsor/1/avatar.svg"></a>
<a href="https://opencollective.com/Android-Password-Store/sponsor/2/website" target="_blank"><img src="https://opencollective.com/Android-Password-Store/sponsor/2/avatar.svg"></a>
<a href="https://opencollective.com/Android-Password-Store/sponsor/3/website" target="_blank"><img src="https://opencollective.com/Android-Password-Store/sponsor/3/avatar.svg"></a>
<a href="https://opencollective.com/Android-Password-Store/sponsor/4/website" target="_blank"><img src="https://opencollective.com/Android-Password-Store/sponsor/4/avatar.svg"></a>
<a href="https://opencollective.com/Android-Password-Store/sponsor/5/website" target="_blank"><img src="https://opencollective.com/Android-Password-Store/sponsor/5/avatar.svg"></a>
<a href="https://opencollective.com/Android-Password-Store/sponsor/6/website" target="_blank"><img src="https://opencollective.com/Android-Password-Store/sponsor/6/avatar.svg"></a>
<a href="https://opencollective.com/Android-Password-Store/sponsor/7/website" target="_blank"><img src="https://opencollective.com/Android-Password-Store/sponsor/7/avatar.svg"></a>
<a href="https://opencollective.com/Android-Password-Store/sponsor/8/website" target="_blank"><img src="https://opencollective.com/Android-Password-Store/sponsor/8/avatar.svg"></a>
<a href="https://opencollective.com/Android-Password-Store/sponsor/9/website" target="_blank"><img src="https://opencollective.com/Android-Password-Store/sponsor/9/avatar.svg"></a>


