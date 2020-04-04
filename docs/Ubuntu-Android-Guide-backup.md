# How to backup everything

## Backup
Backup the pass data folder
```
cp -r ~/.password-store password-store
```

Backup the gpg keys
```
gpg2 --export-secret-keys > secret.gpg
```

## Restore
Restore the pass data folder
```
cp -r ~/password-store ~/.password-store
```

Restore the gpg keys
```
gpg2 --import secret.gpg
gpg --edit-key <KEY_ID>
gpg>trust
enter '5'
```

