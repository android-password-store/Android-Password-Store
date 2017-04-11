package com.zeapo.pwdstore.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static java.util.Collections.sort;

public class PasswordRepository {

    private static Repository repository;
    private static boolean initialized = false;

    protected PasswordRepository(){    }

    /**
     * Returns the git repository
     * @param localDir needed only on the creation
     * @return the git repository
     */
    public static Repository getRepository(File localDir) {
        if (repository == null && localDir != null) {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            try {
                repository = builder.setGitDir(localDir)
                        .readEnvironment()
                        .build();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return repository;
    }

    public static boolean isInitialized() {
        return repository != null;
    }

    public static void createRepository(File localDir) throws Exception{
        localDir.delete();

        Git.init().setDirectory(localDir).call();
        getRepository(localDir);
    }

    // TODO add multiple remotes support for pull/push
    public static void addRemote(String name, String url, Boolean replace) {
        StoredConfig storedConfig = repository.getConfig();
        Set<String> remotes = storedConfig.getSubsections("remote");

        if (!remotes.contains(name)) {
            try {
                URIish uri = new URIish(url);
                RefSpec refSpec = new RefSpec("+refs/head/*:refs/remotes/" + name + "/*");

                RemoteConfig remoteConfig = new RemoteConfig(storedConfig, name);
                remoteConfig.addFetchRefSpec(refSpec);
                remoteConfig.addPushRefSpec(refSpec);
                remoteConfig.addURI(uri);
                remoteConfig.addPushURI(uri);

                remoteConfig.update(storedConfig);

                storedConfig.save();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (replace) {
            try {
                URIish uri = new URIish(url);

                RemoteConfig remoteConfig = new RemoteConfig(storedConfig, name);
                // remove the first and eventually the only uri
                if (remoteConfig.getURIs().size() > 0) {
                    remoteConfig.removeURI(remoteConfig.getURIs().get(0));
                }
                if (remoteConfig.getPushURIs().size() > 0) {
                    remoteConfig.removePushURI(remoteConfig.getPushURIs().get(0));
                }

                remoteConfig.addURI(uri);
                remoteConfig.addPushURI(uri);

                remoteConfig.update(storedConfig);

                storedConfig.save();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void closeRepository() {
        if (repository != null) repository.close();
        repository = null;
    }

    public static File getRepositoryDirectory(Context context) {
        File dir = null;
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());

        if (settings.getBoolean("git_external", false)) {
            String external_repo  = settings.getString("git_external_repo", null);
            if (external_repo != null) {
                dir = new File(external_repo);
            }
        } else {
            dir = new File(context.getFilesDir() + "/store");
        }

        return dir;
    }

    public static Repository initialize(Context context) {
        File dir = getRepositoryDirectory(context);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());

        if (dir == null) {
            return null;
        }

        // uninitialize the repo if the dir does not exist or is absolutely empty
        if (!dir.exists() || !dir.isDirectory() || FileUtils.listFiles(dir, null, false).isEmpty()) {
            settings.edit().putBoolean("repository_initialized", false).apply();
        }

        if (!PasswordRepository.getPasswords(dir).isEmpty()) {
            settings.edit().putBoolean("repository_initialized", true).apply();
        }

        // create the repository static variable in PasswordRepository
        return PasswordRepository.getRepository(new File(dir.getAbsolutePath() + "/.git"));
    }

    /**
     * Gets the password items in the root directory
     * @return a list of passwords in the root direcotyr
     */
    public static ArrayList<PasswordItem> getPasswords(File rootDir) {
        return getPasswords(rootDir, rootDir);
    }

    /**
     * Gets the .gpg files in a directory
     * @param path the directory path
     * @return the list of gpg files in that directory
     */
    public static ArrayList<File> getFilesList(File path){
        if (!path.exists()) return new ArrayList<File>();

        Log.d("REPO", "current path: " + path.getPath());
        ArrayList<File> files = new ArrayList<File>(Arrays.asList(path.listFiles((FileFilter) FileFilterUtils.directoryFileFilter())));
        files.addAll( new ArrayList<File>((List<File>)FileUtils.listFiles(path, new String[] {"gpg"}, false)));

        return new ArrayList<File>(files);
    }

    /**
     * Gets the passwords (PasswordItem) in a directory
     * @param path the directory path
     * @return a list of password items
     */
    public static ArrayList<PasswordItem> getPasswords(File path, File rootDir) {
        //We need to recover the passwords then parse the files
        ArrayList<File> passList = getFilesList(path);

        if (passList.size() == 0) return new ArrayList<PasswordItem>();

        ArrayList<PasswordItem> passwordList = new ArrayList<PasswordItem>();

        for (File file : passList) {
            if (file.isFile()) {
                passwordList.add(PasswordItem.newPassword(file.getName(), file, rootDir));
            } else {
                // ignore .git directory
                if (file.getName().equals(".git"))
                    continue;
                passwordList.add(PasswordItem.newCategory(file.getName(), file, rootDir));
            }
        }
        sort(passwordList);
        return passwordList;
    }

    /**
     * Sets the git user name
     * @param String username username
     */
    public static void setUserName(String username) {
        setStringConfig("user", null, "name", username);
    }

    /**
     * Sets the git user email
     * @param String email email
     */
    public static void setUserEmail(String email) {
        setStringConfig("user", null, "email", email);
    }

    /**
     * Sets a git config value
     * @param String section config section name
     * @param String subsection config subsection name
     * @param String name config name
     * @param String value the value to be set
     */
    private static void setStringConfig(String section, String subsection, String name, String value) {
        if (isInitialized()) {
            StoredConfig config = repository.getConfig();
            config.setString(section, subsection, name, value);
            try {
                config.save();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}
