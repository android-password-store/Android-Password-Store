package com.zeapo.pwdstore.utils;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.sort;

public class PasswordRepository {

    private static Repository repository;

    protected PasswordRepository(){    }

    public static Repository getRepository(File localDir) {
        if (repository == null) {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            try {
                repository = builder.setGitDir(localDir)
                        .readEnvironment()
                        .findGitDir()
                        .build();

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return repository;
    }

    public static void closeRepository() {
        repository.close();
    }

    public static ArrayList<File> getFilesList(){
        return getFilesList(repository.getWorkTree());
    }

    public static ArrayList<PasswordItem> getPasswords() {
        return getPasswords(repository.getWorkTree());
    }

    public static File getWorkTree() {
        return repository.getWorkTree();
    }

    public static File getFile(String name) {
        return new File(repository.getWorkTree() + "/" + name);
    }

    public static ArrayList<File> getFilesList(File path){
        if (!path.exists()) return new ArrayList<File>();

        List<File> files = (List<File>) FileUtils.listFiles(path, new String[] {"gpg"}, true);
        return new ArrayList<File>(files);
    }

    public static ArrayList<PasswordItem> getPasswords(File path) {
        //We need to recover the passwords then parse the files
        ArrayList<File> passList = getFilesList(path);

        if (passList.size() == 0) return new ArrayList<PasswordItem>();

        // TODO replace with a set
        ArrayList<PasswordItem> passwordList = new ArrayList<PasswordItem>();

        for (File file : passList) {
            String fileName = file.getAbsolutePath().replace(path.getAbsolutePath() + "/", "");

            String[] parts = fileName.split("/");
            if (parts.length == 1) {
                passwordList.add(PasswordItem.newPassword(parts[0], file.getParentFile()));
            } else {
                if (!passwordList.contains(PasswordItem.newCategory(parts[0], file.getParentFile()))) {
                    passwordList.add(PasswordItem.newCategory(parts[0], file.getParentFile()));
                }
            }
        }
        sort(passwordList);
        return passwordList;
    }
}
