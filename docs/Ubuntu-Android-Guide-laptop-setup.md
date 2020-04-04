# Setup your pass on your laptop

## 1. Generate a gpg key
```
gpg2 --full-gen-key
```
Explanation: the pass application uses gpg keys to encrypt your passwords

## 2.  Initialize pass
```
pass init my-email@gmail.com
```
Explanation: the `init` command creates the folder where your passwords will be encrypted and stored - `~/.password-store`

