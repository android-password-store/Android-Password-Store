package com.zeapo.pwdstore;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class PasswordRepository {

    private static Repository repository;
    private static LinkedHashMap<String, ArrayList<String>> mainPasswordMap;
    private static ArrayList<String> mainListOfFiles;

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

    public static ArrayList<String> getFilesList(){
        // Avoid multiple queries
        if (null == mainListOfFiles) {
            mainListOfFiles = getFilesList(repository.getWorkTree());
        }
        return mainListOfFiles;
    }

    public static LinkedHashMap<String, ArrayList<String>> getPasswords() {
        // avoid multiple queries
        if (null == mainPasswordMap) {
            mainPasswordMap = getPasswords(repository.getWorkTree());
        }
        return mainPasswordMap;
    }

    public static ArrayList<String> getFilesList(File path){
        List<File> files = (List<File>) FileUtils.listFiles(path, new String[] {"gpg"}, true);
        ArrayList<String> filePaths = new ArrayList<String>();
        for (File file : files) {
            filePaths.add(file.getAbsolutePath().replace(repository.getWorkTree().getAbsolutePath() + "/", ""));
        }
        return filePaths;
    }

    public static LinkedHashMap<String, ArrayList<String>> getPasswords(File path) {
        //We need to recover the passwords then parse the files
        ArrayList<String> passList = getFilesList(path);
        LinkedHashMap<String, ArrayList<String>> passMap = new LinkedHashMap<String, ArrayList<String>>();
        passMap.put("Without Category", new ArrayList<String>());

        for (String file : passList) {
            String[] parts = file.split("/");
            if (parts.length == 1) {
                passMap.get("Without Category").add(parts[0]);
            } else {
                if (passMap.containsKey(parts[0])) {
                    passMap.get(parts[0]).add(parts[1]);
                } else {
                    ArrayList<String> tempList = new ArrayList<String>();
                    tempList.add(parts[1]);
                    passMap.put(parts[0], tempList);
                }
            }
        }
        return passMap;
    }
}
