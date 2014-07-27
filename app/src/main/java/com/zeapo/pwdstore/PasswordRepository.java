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
import java.util.List;

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

    public static ArrayList<String> getFilesList(){
        return getFilesList(repository.getWorkTree());
    }

    public static ArrayList<String> getFilesList(File path){
        List<File> files = (List<File>) FileUtils.listFiles(path, new String[] {"gpg"}, true);
        ArrayList<String> filePaths = new ArrayList<String>();
        for (File file : files) {
            filePaths.add(file.getAbsolutePath().replace(repository.getWorkTree().getAbsolutePath(), ""));
        }
        return filePaths;
    }
}
