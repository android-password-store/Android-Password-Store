package com.zeapo.pwdstore.utils;

import android.support.annotation.NonNull;

import java.io.File;

public class PasswordItem implements Comparable{

    public final static char TYPE_CATEGORY = 'c';
    public final static char TYPE_PASSWORD = 'p';

    private char type;
    private String name;
    private PasswordItem parent;
    private File file;
    private String fullPathToParent;

    /** Create a password item
     *
     * Make it protected so that we use a builder
     */
    private PasswordItem(String name, PasswordItem parent, char type, File file, File rootDir) {
        this.name = name;
        this.parent = parent;
        this.type = type;
        this.file = file;
        this.fullPathToParent = file.getAbsolutePath().replace(rootDir.getAbsolutePath(), "").replace(file.getName(), "");
    }

    /** Create a new Category item
     */
    public static PasswordItem newCategory(String name, File file, PasswordItem parent, File rootDir) {
        return new PasswordItem(name, parent, TYPE_CATEGORY, file, rootDir);
    }

    /** Create a new parentless category item
     */
    public static PasswordItem newCategory(String name, File file, File rootDir) {
        return new PasswordItem(name, null, TYPE_CATEGORY, file, rootDir);
    }

    /** Create a new password item
     */
    public static PasswordItem newPassword(String name, File file,  PasswordItem parent, File rootDir) {
        return new PasswordItem(name, parent, TYPE_PASSWORD, file, rootDir);
    }

    /** Create a new parentless password item
     */
    public static PasswordItem newPassword(String name, File file, File rootDir) {
        return new PasswordItem(name, null, TYPE_PASSWORD, file, rootDir);
    }

    public char getType(){
        return this.type;
    }

    private String getName(){
        return this.name;
    }

    public PasswordItem getParent() {
        return this.parent;
    }

    public File getFile() {
        return this.file;
    }

    public String getFullPathToParent() {
        return this.fullPathToParent;
    }

    @Override
    public String toString(){
        return this.getName().replace(".gpg", "");
    }

    @Override
    public boolean equals(Object o) {
        // Makes it possible to have a category and a password with the same name
        return o != null
                && o.getClass() == PasswordItem.class
                && ((PasswordItem) o).getFile().equals(this.getFile());
    }

    @Override
    public int compareTo(@NonNull Object o) {
        PasswordItem other = (PasswordItem) o;
        // Appending the type will make the sort type dependent
        return (this.getType() + this.getName())
                .compareToIgnoreCase(other.getType() + other.getName());
    }
}
