package com.zeapo.pwdstore;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;

/**
 * Created by zeapo on 7/27/14.
 */
public class GitRepo {

    private static Repository repository;

    protected GitRepo(){    }

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
}
