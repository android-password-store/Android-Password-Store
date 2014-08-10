PwdStore
========

This application tries to be 100% compatible with [pass](http://www.zx2c4.com/projects/password-store/)

Alpha version available at [PlayStore]

Features
========
- Clone an existing pass repository
- List the passwords
- Handle the directories as categories
- Decrypt the password files (first line is the password, the rest is extra data)
- Add a new password to the current category (or no category if added at the root)
- Pull and Push changes to the remote repository
- Ability to change remote repository info

Libraries
=========
This project uses three libraries:

- [OpenKeyChain](https://github.com/open-keychain/open-keychain) for encryption and decryption of passwords
- [JGit](http://www.eclipse.org/jgit/) a pretty good git lib 
- [Apache's FileUtils](https://commons.apache.org/proper/commons-io/) for files manipulations

TODOs
=====
- Initialize a new pass repository
- Create a new category
- Multi-select (for password deletion)
- Multiple password stores (multiple git repositories). 
- More UI enhancements
- Clean-up the hard-coded strings

Needed
======
- Icons: the current ones are CC, but would be great to have our own icons
- UI enhancements: any UI changes or suggestions are welcome





