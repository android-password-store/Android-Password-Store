# How to use pass on your laptop

## Genegate a password
Create a password for your gmail: (the 10 is the number of characters you want your password to have)
```
pass generate gmail 10
```

## Genegate a password inside a folder
Create a password inside a folder called banks
```
pass generate banks/wells-fargo 12
```

## View a password
View the gmail password
```
pass gmail
```

You will be asked for your password. type it and you should see your gmail's password in the terminal:
```
t61\A3'L2;
```

## View names of all passwords
View all entries:
```
pass
```

You should see something like this:
```
Password Store
└── gmail
```


## Delete a password
Delete the gmail password
```
pass rm gmail
```

