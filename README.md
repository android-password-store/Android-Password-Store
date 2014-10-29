PwdStore
========

This application tries to be 100% compatible with [pass](http://www.zx2c4.com/projects/password-store/)

You can install the application either from the [Play Store](https://play.google.com/store/apps/details?id=com.zeapo.pwdstore),
from [F-Droid](https://f-droid.org/repository/browse/?fdid=com.zeapo.pwdstore), or
using the apk file found at `app/app-release.apk` or build everything from source.

How-To
======
*Note:* This section is work in progress

Clone using SSH-key, then decrypt a password
--------------------------------------------

<img src="tutorial_clone_ssh_then_decrypt.gif" alt="Clone And Decrypt" style="width:720px">

Features
========
- Clone an existing pass repository (ssh-key and user/pass support)
- List the passwords
- Handle the directories as categories
- Decrypt the password files (first line is the password, the rest is extra data)
- Add a new password to the current category (or no category if added at the root)
- Pull and Push changes to the remote repository
- Ability to change remote repository info

Libraries
=========
This project uses three libraries:

- [OpenKeyChain](https://github.com/open-keychain/open-keychain) for encryption and decryption of passwords.
    To download the library, run the following commands at the root of the project

        git submodule init
        git submodule update


- [JGit](http://www.eclipse.org/jgit/) a pretty good git lib
- [Apache's FileUtils](https://commons.apache.org/proper/commons-io/) for files manipulations

TODOs
=====
- Create a new category
- Multi-select (for password deletion)
- Multiple password stores (multiple git repositories).
- More UI enhancements
- Clean-up the hard-coded strings

Needed
======
- Icons: the current ones are CC, but would be great to have our own icons
- UI enhancements: any UI changes or suggestions are welcome







[![Bitdeli Badge](https://d2weczhvl823v0.cloudfront.net/zeapo/android-password-store/trend.png)](https://bitdeli.com/free "Bitdeli Badge")

