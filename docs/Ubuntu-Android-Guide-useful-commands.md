# Useful Commands

List secret keys
```
gpg -k
```

Delete a private key
```
gpg --delete-secret-key "User Name"
```

Delete a public key
```
gpg --delete-key "User Name"
```

Backup the gpg keys
```
gpg2 --export-secret-keys > secret.gpg
```

Restore the gpg keys
```
gpg2 --import secret.gpg
gpg --edit-key <KEY_ID>
gpg>trust
enter '5'
```
