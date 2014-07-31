package com.zeapo.pwdstore.utils;

import java.io.File;

public class PasswordItem implements Comparable{

    public final static char TYPE_CATEGORY = 'c';
    public final static char TYPE_PASSWORD = 'p';

    private char type;
    private String name;
    private PasswordItem parent;
    private File file;

    /** Create a password item
     *
     * Make it protected so that we use a builder
     * @param name
     * @param parent
     * @param type
     */
    protected PasswordItem(String name, PasswordItem parent, char type, File file) {
        this.name = name;
        this.parent = parent;
        this.type = type;
        this.file = file;
    }

    /** Create a new Category item
     *
     * @param name
     * @param parent
     * @return
     */
    public static PasswordItem newCategory(String name, File file, PasswordItem parent) {
        return new PasswordItem(name, parent, TYPE_CATEGORY, file);
    }

    /** Create a new parentless category item
     *
     * @param name
     * @return
     */
    public static PasswordItem newCategory(String name, File file) {
        return new PasswordItem(name, null, TYPE_CATEGORY, file);
    }

    /** Create a new password item
     *
     * @param name
     * @param parent
     * @return
     */
    public static PasswordItem newPassword(String name, File file,  PasswordItem parent) {
        return new PasswordItem(name, parent, TYPE_PASSWORD, file);
    }

    /** Create a new parentless password item
     *
     * @param name
     * @return
     */
    public static PasswordItem newPassword(String name, File file) {
        return new PasswordItem(name, null, TYPE_PASSWORD, file);
    }

    public char getType(){
        return this.type;
    }

    public String getName(){
        return this.name;
    }

    public PasswordItem getParent() {
        return this.parent;
    }

    public File getFile() {
        return this.file;
    }

    @Override
    public String toString(){
        return this.getName().replace(".gpg", "");
    }

    @Override
    public boolean equals(Object o){
        PasswordItem other = (PasswordItem) o;
        // Makes it possible to have a category and a password with the same name
        return (other.getType() + other.getName())
                .equals(this.getType() + this.getName());
    }

    @Override
    public int compareTo(Object o) {
        PasswordItem other = (PasswordItem) o;
        // Appending the type will make the sort type dependent
        return (this.getType() + this.getName())
                .compareTo(other.getType() + other.getName());
    }
}
