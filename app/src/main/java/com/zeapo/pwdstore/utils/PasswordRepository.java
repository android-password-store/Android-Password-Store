package com.zeapo.pwdstore.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static java.util.Collections.sort;

public class PasswordRepository {

    private static Repository repository;

    protected PasswordRepository() {
    }

    /**
     * Returns the git repository
     *
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

    public static void createRepository(File localDir) throws Exception {
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
            String external_repo = settings.getString("git_external_repo", null);
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
        if (!dir.exists() || !dir.isDirectory() || dir.listFiles().length == 0) {
            settings.edit().putBoolean("repository_initialized", false).apply();
        } else {
            settings.edit().putBoolean("repository_initialized", true).apply();
        }

        // create the repository static variable in PasswordRepository
        return PasswordRepository.getRepository(new File(dir.getAbsolutePath() + "/.git"));
    }

    /**
     * Gets the password items in the root directory
     *
     * @return a list of passwords in the root direcotyr
     */
    public static ArrayList<PasswordItem> getPasswords(File rootDir, PasswordSortOrder sortOrder) {
        return getPasswords(rootDir, rootDir, sortOrder);
    }

    /**
     * Gets the .gpg files in a directory
     *
     * @param path the directory path
     * @return the list of gpg files in that directory
     */
    public static ArrayList<File> getFilesList(File path) {
        if (path == null || !path.exists()) return new ArrayList<>();

        Log.d("REPO", "current path: " + path.getPath());
        List<File> directories = Arrays.asList(path.listFiles((FileFilter) FileFilterUtils.directoryFileFilter()));
        List<File> files = Arrays.asList(path.listFiles((FileFilter) FileFilterUtils.suffixFileFilter(".gpg")));

        ArrayList<File> items = new ArrayList<>();
        items.addAll(directories);
        items.addAll(files);

        return items;
    }

    /**
     * Gets the passwords (PasswordItem) in a directory
     *
     * @param path the directory path
     * @return a list of password items
     */
    public static ArrayList<PasswordItem> getPasswords(File path, File rootDir, PasswordSortOrder sortOrder) {
        //We need to recover the passwords then parse the files
        ArrayList<File> passList = getFilesList(path);

        if (passList.size() == 0) return new ArrayList<>();

        ArrayList<PasswordItem> passwordList = new ArrayList<>();

        for (File file : passList) {
            if (file.isFile()) {
                if (!file.isHidden()) {
                    passwordList.add(PasswordItem.newPassword(file.getName(), file, rootDir));
                }
            } else {
                if (!file.isHidden()) {
                    passwordList.add(PasswordItem.newCategory(file.getName(), file, rootDir));
                }
            }
        }
        sort(passwordList, sortOrder.comparator);
        return passwordList;
    }

    /**
     * Sets the git user name
     *
     * @param username username
     */
    public static void setUserName(String username) {
        setStringConfig("user", null, "name", username);
    }

    /**
     * Sets the git user email
     *
     * @param email email
     */
    public static void setUserEmail(String email) {
        setStringConfig("user", null, "email", email);
    }

    /**
     * Sets a git config value
     *
     * @param section    config section name
     * @param subsection config subsection name
     * @param name       config name
     * @param value      the value to be set
     */
    private static void setStringConfig(String section, String subsection, String name, String value) {
        if (isInitialized()) {
            StoredConfig config = repository.getConfig();
            config.setString(section, subsection, name, value);
            try {
                config.save();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public enum PasswordSortOrder {

        FOLDER_FIRST(new Comparator<PasswordItem>() {
            @Override
            public int compare(PasswordItem p1, PasswordItem p2) {
                return (p1.getType() + p1.getName())
                        .compareToIgnoreCase(p2.getType() + p2.getName());
            }
        }),

        INDEPENDENT(new Comparator<PasswordItem>() {
            @Override
            public int compare(PasswordItem p1, PasswordItem p2) {
                return p1.getName().compareToIgnoreCase(p2.getName());
            }
        }),

        FILE_FIRST(new Comparator<PasswordItem>() {
            @Override
            public int compare(PasswordItem p1, PasswordItem p2) {
                return (p2.getType() + p1.getName())
                        .compareToIgnoreCase(p1.getType() + p2.getName());
            }
        })

        ;

        private Comparator<PasswordItem> comparator;

        PasswordSortOrder(Comparator<PasswordItem> comparator) {
            this.comparator = comparator;
        }

        public static PasswordSortOrder getSortOrder(SharedPreferences settings) {
            return valueOf(settings.getString("sort_order", FOLDER_FIRST.name()));
        }
    }
}
