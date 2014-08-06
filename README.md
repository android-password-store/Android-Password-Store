PwdStore
========

This application tries to be 100% compatible with [pass](http://www.zx2c4.com/projects/password-store/)

Feautres
========
- Clone an existing pass repository
- List the passwords
- Handle the directories as categories
- Decrypt the password files (first line is the password, the rest is extra data)
- Add a new password to the current category (or no category if added at the root)

Libraries
=========
This project uses three libraries:
- [OpenKeyChain](https://github.com/open-keychain/open-keychain) for encryption and decryption of passwords
- [JGit]() a pretty good git lib 
- [Apache's FileUtils]() for files manipulations

TODOs
=====
- Initialize a new pass repository
- Pull from/Push to a pass repository
- Create a new cateogry
- Multi-select (for password deletion)
- Multiple password stores (multiple git repositories). 
- More UI enhancements

Needed
======
- Icons: the current ones are CC, but would be great to have our own icons
- UI enhancements: any UI changes or suggestions are welcome





