package com.zeapo.pwdstore.utils;

import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Collections.sort;

public class PasswordRepository {

    private static Repository repository;
    private static boolean initialized = false;

    protected PasswordRepository(){    }

    public static Repository getRepository(File localDir) {
        if (repository == null) {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            try {
                repository = builder.setGitDir(localDir)
                        .readEnvironment()
                        .findGitDir()
                        .build();
                initialized = true;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return repository;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void createRepository(File localDir) {
        localDir.delete();

        try {

            // create the directory
            Repository repository = FileRepositoryBuilder.create(new File(localDir, ".git"));
            repository.create();

            Git.init()
                    .setDirectory(localDir)
                    .call();

            getRepository(localDir);

            new Git(repository)
                    .branchCreate()
                    .setName("master")
                    .call();

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    // TODO add remote edition later-on
    // TODO add multiple remotes support for pull/push
    public static void addRemote(String name, String url) {
        StoredConfig storedConfig = repository.getConfig();
        Set<String> remotes = storedConfig.getSubsections("remote");

        if (!remotes.contains(name)) {
            try {
                URIish uri = new URIish(url);
                RefSpec refSpec = new RefSpec("+refs/head/*:refs/remotes/"+ name + "/*");

                RemoteConfig remoteConfig = new RemoteConfig(storedConfig, name);
                remoteConfig.addFetchRefSpec(refSpec);
                remoteConfig.addPushRefSpec(refSpec);
                remoteConfig.addURI(uri);
                remoteConfig.addPushURI(uri);

                remoteConfig.update(storedConfig);

                storedConfig.save();
            } catch (Exception e) {

            }
        }
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

        Log.d("REPO", path.getAbsolutePath());
        ArrayList<File> files = new ArrayList<File>(Arrays.asList(path.listFiles((FileFilter) FileFilterUtils.directoryFileFilter())));
        files.addAll( new ArrayList<File>((List<File>)FileUtils.listFiles(path, new String[] {"gpg"}, false)));

        return new ArrayList<File>(files);
    }

    public static ArrayList<PasswordItem> getPasswords(File path) {
        //We need to recover the passwords then parse the files
        ArrayList<File> passList = getFilesList(path);

        if (passList.size() == 0) return new ArrayList<PasswordItem>();

        // TODO replace with a set
        ArrayList<PasswordItem> passwordList = new ArrayList<PasswordItem>();

        for (File file : passList) {
            if (file.isFile()) {
                passwordList.add(PasswordItem.newPassword(file.getName(), file));
            } else {
                // ignore .git directory
                if (file.getName().equals(".git"))
                    continue;
                passwordList.add(PasswordItem.newCategory(file.getName(), file));
            }
        }
        sort(passwordList);
        return passwordList;
    }
}
