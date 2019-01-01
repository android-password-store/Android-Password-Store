package com.zeapo.pwdstore;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import com.zeapo.pwdstore.crypto.PgpActivity;
import com.zeapo.pwdstore.git.GitActivity;
import com.zeapo.pwdstore.git.GitAsyncTask;
import com.zeapo.pwdstore.git.GitOperation;
import com.zeapo.pwdstore.pwgen.PRNGFixes;
import com.zeapo.pwdstore.utils.PasswordItem;
import com.zeapo.pwdstore.utils.PasswordRecyclerAdapter;
import com.zeapo.pwdstore.utils.PasswordRepository;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.util.*;

public class PasswordStore extends AppCompatActivity {
    private static final String TAG = "PwdStrAct";
    private SharedPreferences settings;
    private Activity activity;
    private PasswordFragment plist;
    private ShortcutManager shortcutManager;
    private MenuItem searchItem = null;
    private SearchView searchView;

    private final static int CLONE_REPO_BUTTON = 401;
    private final static int NEW_REPO_BUTTON = 402;
    private final static int HOME = 403;
    public static final int REQUEST_CODE_SIGN = 9910;
    public static final int REQUEST_CODE_ENCRYPT = 9911;
    public static final int REQUEST_CODE_SIGN_AND_ENCRYPT = 9912;
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;
    public static final int REQUEST_CODE_GET_KEY = 9914;
    public static final int REQUEST_CODE_GET_KEY_IDS = 9915;
    public static final int REQUEST_CODE_EDIT = 9916;
    public static final int REQUEST_CODE_SELECT_FOLDER = 9917;

    private static boolean isPrintable(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return (!Character.isISOControl(c)) &&
                block != null &&
                block != Character.UnicodeBlock.SPECIALS;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // open search view on search key, or Ctr+F
        if ((keyCode == KeyEvent.KEYCODE_SEARCH ||
                keyCode == KeyEvent.KEYCODE_F && event.isCtrlPressed())
                && !searchItem.isActionViewExpanded()) {
            searchItem.expandActionView();
            return true;
        }

        // open search view on any printable character and query for it
        char c = (char) event.getUnicodeChar();
        boolean printable = isPrintable(c);
        if (printable && !searchItem.isActionViewExpanded()) {
            searchItem.expandActionView();
            searchView.setQuery(Character.toString(c), true);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private final static int REQUEST_EXTERNAL_STORAGE = 50;

    @Override
    @SuppressLint("NewApi")
    protected void onCreate(Bundle savedInstanceState) {
        settings = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            shortcutManager = getSystemService(ShortcutManager.class);
        }
        activity = this;
        PRNGFixes.apply();

        // If user opens app with permission granted then revokes and returns,
        // prevent attempt to create password list fragment
        if (savedInstanceState != null && (!settings.getBoolean("git_external", false)
                || ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            savedInstanceState = null;
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pwdstore);
    }

    @Override
    public void onResume() {
        super.onResume();
        // do not attempt to checkLocalRepository() if no storage permission: immediate crash
        if (settings.getBoolean("git_external", false)) {
            if (ContextCompat.checkSelfPermission(activity,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                        Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Snackbar snack = Snackbar.make(findViewById(R.id.main_layout), "The store is on the sdcard but the app does not have permission to access it. Please give permission.",
                            Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.dialog_ok, new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    ActivityCompat.requestPermissions(activity,
                                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                            REQUEST_EXTERNAL_STORAGE);
                                }
                            });
                    snack.show();
                    View view = snack.getView();
                    TextView tv = (TextView) view.findViewById(com.google.android.material.R.id.snackbar_text);
                    tv.setTextColor(Color.WHITE);
                    tv.setMaxLines(10);
                } else {
                    // No explanation needed, we can request the permission.
                    ActivityCompat.requestPermissions(activity,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            REQUEST_EXTERNAL_STORAGE);
                }
            } else {
                checkLocalRepository();
            }

        } else {
            checkLocalRepository();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkLocalRepository();
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        searchItem = menu.findItem(R.id.action_search);
        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                filterListAdapter(s);
                return true;
            }
        });

        // When using the support library, the setOnActionExpandListener() method is
        // static and accepts the MenuItem object as an argument
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                refreshListAdapter();
                return true;
            }

            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        Intent intent;
        Log.d("PASS", "Menu item " + id + " pressed");

        AlertDialog.Builder initBefore = new AlertDialog.Builder(this)
                .setMessage(this.getResources().getString(R.string.creation_dialog_text))
                .setPositiveButton(this.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                });

        switch (id) {
            case R.id.user_pref:
                try {
                    intent = new Intent(this, UserPreference.class);
                    startActivity(intent);
                } catch (Exception e) {
                    System.out.println("Exception caught :(");
                    e.printStackTrace();
                }
                return true;
            case R.id.git_push:
                if (!PasswordRepository.isInitialized()) {
                    initBefore.show();
                    break;
                }

                intent = new Intent(this, GitActivity.class);
                intent.putExtra("Operation", GitActivity.REQUEST_PUSH);
                startActivityForResult(intent, GitActivity.REQUEST_PUSH);
                return true;

            case R.id.git_pull:
                if (!PasswordRepository.isInitialized()) {
                    initBefore.show();
                    break;
                }

                intent = new Intent(this, GitActivity.class);
                intent.putExtra("Operation", GitActivity.REQUEST_PULL);
                startActivityForResult(intent, GitActivity.REQUEST_PULL);
                return true;

            case R.id.git_sync:
                if (!PasswordRepository.isInitialized()) {
                    initBefore.show();
                    break;
                }

                intent = new Intent(this, GitActivity.class);
                intent.putExtra("Operation", GitActivity.REQUEST_SYNC);
                startActivityForResult(intent, GitActivity.REQUEST_SYNC);
                return true;

            case R.id.refresh:
                updateListAdapter();
                return true;

            case android.R.id.home:
                Log.d("PASS", "Home pressed");
                this.onBackPressed();
                break;

            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    public void openSettings(View view) {
        Intent intent;

        try {
            intent = new Intent(this, UserPreference.class);
            startActivity(intent);
        } catch (Exception e) {
            System.out.println("Exception caught :(");
            e.printStackTrace();
        }
    }

    public void cloneExistingRepository(View view) {
        initRepository(CLONE_REPO_BUTTON);
    }

    public void createNewRepository(View view) {
        initRepository(NEW_REPO_BUTTON);
    }

    private void createRepository() {
        if (!PasswordRepository.isInitialized()) {
            PasswordRepository.initialize(this);
        }

        File localDir = PasswordRepository.getRepositoryDirectory(getApplicationContext());

        localDir.mkdir();
        try {
            PasswordRepository.createRepository(localDir);
            new File(localDir.getAbsolutePath() + "/.gpg-id").createNewFile();
            settings.edit().putBoolean("repository_initialized", true).apply();
        } catch (Exception e) {
            e.printStackTrace();
            localDir.delete();
            return;
        }
        checkLocalRepository();
    }

    private void initializeRepositoryInfo() {
        final String externalRepoPath = settings.getString("git_external_repo", null);
        if (settings.getBoolean("git_external", false) && externalRepoPath != null) {
            File dir = new File(externalRepoPath);

            PasswordRepository.PasswordSortOrder sortOrder = PasswordRepository.PasswordSortOrder.valueOf(settings.getString("sort_order", null));

            if (dir.exists() && dir.isDirectory() && !PasswordRepository.getPasswords(dir, PasswordRepository.getRepositoryDirectory(this), getSortOrder()).isEmpty()) {
                PasswordRepository.closeRepository();
                checkLocalRepository();
                return; // if not empty, just show me the passwords!
            }
        }

        final Set<String> keyIds = settings.getStringSet("openpgp_key_ids_set", new HashSet<String>());

        if (keyIds.isEmpty())
            new AlertDialog.Builder(this)
                    .setMessage(this.getResources().getString(R.string.key_dialog_text))
                    .setPositiveButton(this.getResources().getString(R.string.dialog_positive), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(activity, UserPreference.class);
                            startActivityForResult(intent, GitActivity.REQUEST_INIT);
                        }
                    })
                    .setNegativeButton(this.getResources().getString(R.string.dialog_negative), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            // do nothing :(
                        }
                    })
                    .show();

        createRepository();
    }

    private void checkLocalRepository() {
        Repository repo = PasswordRepository.initialize(this);
        if (repo == null) {
            Intent intent = new Intent(activity, UserPreference.class);
            intent.putExtra("operation", "git_external");
            startActivityForResult(intent, HOME);
        } else {
            checkLocalRepository(PasswordRepository.getRepositoryDirectory(getApplicationContext()));
        }
    }

    private void checkLocalRepository(File localDir) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        if (localDir != null && settings.getBoolean("repository_initialized", false)) {
            Log.d("PASS", "Check, dir: " + localDir.getAbsolutePath());
            // do not push the fragment if we already have it
            if (fragmentManager.findFragmentByTag("PasswordsList") == null || settings.getBoolean("repo_changed", false)) {
                settings.edit().putBoolean("repo_changed", false).apply();

                plist = new PasswordFragment();
                Bundle args = new Bundle();
                args.putString("Path", PasswordRepository.getRepositoryDirectory(getApplicationContext()).getAbsolutePath());

                // if the activity was started from the autofill settings, the
                // intent is to match a clicked pwd with app. pass this to fragment
                if (getIntent().getBooleanExtra("matchWith", false)) {
                    args.putBoolean("matchWith", true);
                }

                plist.setArguments(args);

                getSupportActionBar().show();
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);

                fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

                fragmentTransaction.replace(R.id.main_layout, plist, "PasswordsList");
                fragmentTransaction.commit();
            }
        } else {
            getSupportActionBar().hide();

            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

            ToCloneOrNot cloneFrag = new ToCloneOrNot();
            fragmentTransaction.replace(R.id.main_layout, cloneFrag, "ToCloneOrNot");
            fragmentTransaction.commit();
        }
    }


    @Override
    public void onBackPressed() {
        if ((null != plist) && plist.isNotEmpty()) {
            plist.popBack();
        } else {
            super.onBackPressed();
        }

        if (null != plist && !plist.isNotEmpty()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }

    private String getRelativePath(String fullPath, String repositoryPath) {
        return fullPath.replace(repositoryPath, "").replaceAll("/+", "/");
    }

    public int getLastChangedTimestamp(String fullPath) {
        File repoPath = PasswordRepository.getRepositoryDirectory(this);
        Repository repository = PasswordRepository.getRepository(repoPath);

        if (repository == null) {
            Log.e(TAG, "getLastChangedTimestamp: No git repository");
            return -1;
        }

        Git git = new Git(repository);
        String relativePath = getRelativePath(fullPath, repoPath.getAbsolutePath())
                .substring(1); // Removes leading '/'

        Iterator<RevCommit> iterator;
        try {
            iterator = git
                    .log()
                    .addPath(relativePath)
                    .call()
                    .iterator();
        } catch (GitAPIException e) {
            Log.e(TAG, "getLastChangedTimestamp: GITAPIException", e);
            return -1;
        }

        if (!iterator.hasNext()) {
            Log.w(TAG, "getLastChangedTimestamp: No commits for file: " + relativePath);
            return -1;
        }

        return iterator.next().getCommitTime();
    }

    public void decryptPassword(PasswordItem item) {
        Intent intent = new Intent(this, PgpActivity.class);
        intent.putExtra("NAME", item.toString());
        intent.putExtra("FILE_PATH", item.getFile().getAbsolutePath());
        intent.putExtra("REPO_PATH", PasswordRepository.getRepositoryDirectory(getApplicationContext()).getAbsolutePath());
        intent.putExtra("LAST_CHANGED_TIMESTAMP", getLastChangedTimestamp(item.getFile().getAbsolutePath()));
        intent.putExtra("OPERATION", "DECRYPT");

        // Adds shortcut
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutInfo shortcut = new ShortcutInfo.Builder(this, item.getFullPathToParent())
                    .setShortLabel(item.toString())
                    .setLongLabel(item.getFullPathToParent() + item.toString())
                    .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                    .setIntent(intent.setAction("DECRYPT_PASS")) // Needs action
                    .build();
            List<ShortcutInfo> shortcuts = shortcutManager.getDynamicShortcuts();

            if (shortcuts.size() >= shortcutManager.getMaxShortcutCountPerActivity() &&
                    shortcuts.size() > 0) {
                shortcuts.remove(shortcuts.size() - 1);
                shortcuts.add(0, shortcut);
                shortcutManager.setDynamicShortcuts(shortcuts);
            } else {
                shortcutManager.addDynamicShortcuts(Collections.singletonList(shortcut));
            }
        }
        startActivityForResult(intent, REQUEST_CODE_DECRYPT_AND_VERIFY);
    }

    public void editPassword(PasswordItem item) {
        Intent intent = new Intent(this, PgpActivity.class);
        intent.putExtra("NAME", item.toString());
        intent.putExtra("FILE_PATH", item.getFile().getAbsolutePath());
        intent.putExtra("PARENT_PATH", getCurrentDir().getAbsolutePath());
        intent.putExtra("REPO_PATH", PasswordRepository.getRepositoryDirectory(getApplicationContext()).getAbsolutePath());
        intent.putExtra("OPERATION", "EDIT");
        startActivityForResult(intent, REQUEST_CODE_EDIT);
    }

    public void createPassword() {
        if (!PasswordRepository.isInitialized()) {
            new AlertDialog.Builder(this)
                    .setMessage(this.getResources().getString(R.string.creation_dialog_text))
                    .setPositiveButton(this.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    }).show();
            return;
        }

        if (settings.getStringSet("openpgp_key_ids_set", new HashSet<String>()).isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(this.getResources().getString(R.string.no_key_selected_dialog_title))
                    .setMessage(this.getResources().getString(R.string.no_key_selected_dialog_text))
                    .setPositiveButton(this.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(activity, UserPreference.class);
                            startActivity(intent);
                        }
                    }).show();
            return;
        }

        File currentDir = getCurrentDir();
        Log.i("PWDSTR", "Adding file to : " + currentDir.getAbsolutePath());

        Intent intent = new Intent(this, PgpActivity.class);
        intent.putExtra("FILE_PATH", getCurrentDir().getAbsolutePath());
        intent.putExtra("REPO_PATH", PasswordRepository.getRepositoryDirectory(getApplicationContext()).getAbsolutePath());
        intent.putExtra("OPERATION", "ENCRYPT");
        startActivityForResult(intent, REQUEST_CODE_ENCRYPT);
    }

    // deletes passwords in order from top to bottom
    public void deletePasswords(final PasswordRecyclerAdapter adapter, final Set<Integer> selectedItems) {
        final Iterator it = selectedItems.iterator();
        if (!it.hasNext()) {
            return;
        }
        final int position = (int) it.next();
        final PasswordItem item = adapter.getValues().get(position);
        new AlertDialog.Builder(this).
                setMessage(this.getResources().getString(R.string.delete_dialog_text) +
                        item + "\"")
                .setPositiveButton(this.getResources().getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        item.getFile().delete();
                        adapter.remove(position);
                        it.remove();
                        adapter.updateSelectedItems(position, selectedItems);

                        commitChange(getResources().getString(R.string.git_commit_remove_text,
                                item.getLongName()));
                        deletePasswords(adapter, selectedItems);
                    }
                })
                .setNegativeButton(this.getResources().getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        it.remove();
                        deletePasswords(adapter, selectedItems);
                    }
                })
                .show();
    }

    public void movePasswords(ArrayList<PasswordItem> values) {
        Intent intent = new Intent(this, SelectFolderActivity.class);
        ArrayList<String> fileLocations = new ArrayList<>();
        for (PasswordItem passwordItem : values) {
            fileLocations.add(passwordItem.getFile().getAbsolutePath());
        }
        intent.putExtra("Files", fileLocations);
        intent.putExtra("Operation", "SELECTFOLDER");
        startActivityForResult(intent, REQUEST_CODE_SELECT_FOLDER);
    }

    /**
     * clears adapter's content and updates it with a fresh list of passwords from the root
     */
    public void updateListAdapter() {
        if ((null != plist)) {
            plist.updateAdapter();
        }
    }

    /**
     * Updates the adapter with the current view of passwords
     */
    private void refreshListAdapter() {
        if ((null != plist)) {
            plist.refreshAdapter();
        }
    }

    private void filterListAdapter(String filter) {
        if ((null != plist)) {
            plist.filterAdapter(filter);
        }
    }

    private File getCurrentDir() {
        if ((null != plist)) {
            return plist.getCurrentDir();
        }
        return PasswordRepository.getRepositoryDirectory(getApplicationContext());
    }

    private void commitChange(final String message) {
        new GitOperation(PasswordRepository.getRepositoryDirectory(activity), activity) {
            @Override
            public void execute() {
                Log.d(TAG, "Commiting with message " + message);
                Git git = new Git(this.repository);
                GitAsyncTask tasks = new GitAsyncTask(activity, false, true, this);
                tasks.execute(
                        git.add().addFilepattern("."),
                        git.commit().setAll(true).setMessage(message)
                );
            }
        }.execute();
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case GitActivity.REQUEST_CLONE:
                    // if we get here with a RESULT_OK then it's probably OK :)
                    settings.edit().putBoolean("repository_initialized", true).apply();
                    break;
                case REQUEST_CODE_DECRYPT_AND_VERIFY:
                    // if went from decrypt->edit and user saved changes or HOTP counter was incremented, we need to commitChange
                    if (data != null && data.getBooleanExtra("needCommit", false)) {
                        if (data.getStringExtra("OPERATION").equals("EDIT")) {
                            commitChange(this.getResources().getString(R.string.git_commit_edit_text,
                                    data.getExtras().getString("LONG_NAME")));
                        } else {
                            commitChange(this.getResources().getString(R.string.git_commit_increment_text,
                                    data.getExtras().getString("LONG_NAME")));
                        }
                    }
                    refreshListAdapter();
                    break;
                case REQUEST_CODE_ENCRYPT:
                    commitChange(this.getResources().getString(R.string.git_commit_add_text,
                            data.getExtras().getString("LONG_NAME")));
                    refreshListAdapter();
                    break;
                case REQUEST_CODE_EDIT:
                    commitChange(this.getResources().getString(R.string.git_commit_edit_text,
                            data.getExtras().getString("LONG_NAME")));
                    refreshListAdapter();
                    break;
                case GitActivity.REQUEST_INIT:
                    initializeRepositoryInfo();
                    break;
                case GitActivity.REQUEST_SYNC:
                case GitActivity.REQUEST_PULL:
                    updateListAdapter();
                    break;
                case HOME:
                    checkLocalRepository();
                    break;
                case NEW_REPO_BUTTON:
                    initializeRepositoryInfo();
                    break;
                case CLONE_REPO_BUTTON:
                    // duplicate code
                    if (settings.getBoolean("git_external", false) && settings.getString("git_external_repo", null) != null) {
                        String externalRepoPath = settings.getString("git_external_repo", null);
                        File dir = externalRepoPath != null ? new File(externalRepoPath) : null;

                        if (dir != null &&
                                dir.exists() &&
                                dir.isDirectory() &&
                                !FileUtils.listFiles(dir, null, true).isEmpty() &&
                                !PasswordRepository.getPasswords(dir, PasswordRepository.getRepositoryDirectory(this), getSortOrder()).isEmpty()) {
                            PasswordRepository.closeRepository();
                            checkLocalRepository();
                            return; // if not empty, just show me the passwords!
                        }
                    }
                    Intent intent = new Intent(activity, GitActivity.class);
                    intent.putExtra("Operation", GitActivity.REQUEST_CLONE);
                    startActivityForResult(intent, GitActivity.REQUEST_CLONE);
                    break;
                case REQUEST_CODE_SELECT_FOLDER:
                    Log.d("Moving", "Moving passwords to " + data.getStringExtra("SELECTED_FOLDER_PATH"));
                    Log.d("Moving", TextUtils.join(", ", data.getStringArrayListExtra("Files")));
                    File target = new File(data.getStringExtra("SELECTED_FOLDER_PATH"));
                    if (!target.isDirectory()) {
                        Log.e("Moving", "Tried moving passwords to a non-existing folder.");
                        break;
                    }

                    String repositoryPath = PasswordRepository
                            .getRepositoryDirectory(getApplicationContext())
                            .getAbsolutePath();

                    // TODO move this to an async task
                    for (String fileString : data.getStringArrayListExtra("Files")) {
                        File source = new File(fileString);
                        if (!source.exists()) {
                            Log.e("Moving", "Tried moving something that appears non-existent.");
                            continue;
                        }

                        File destinationFile = new File(target.getAbsolutePath() + "/" + source.getName());

                        String basename = FilenameUtils.getBaseName(source.getAbsolutePath());

                        String sourceLongName = PgpActivity.getLongName(source.getParent(),
                                repositoryPath, basename);

                        String destinationLongName = PgpActivity.getLongName(target.getAbsolutePath(),
                                repositoryPath, basename);

                        if (destinationFile.exists()) {
                            Log.e("Moving", "Trying to move a file that already exists.");
                            // TODO: Add option to cancel overwrite. Will be easier once this is an async task.
                            // TODO: Replace with resource strings
                            new AlertDialog.Builder(this)
                                    .setTitle("Password already exists!")
                                    .setMessage(String.format("This will overwrite %1$s with %2$s.",
                                            destinationLongName, sourceLongName))
                                    .setPositiveButton("Okay", null)
                                    .show();
                        }

                        if (!source.renameTo(destinationFile)) {
                            // TODO this should show a warning to the user
                            Log.e("Moving", "Something went wrong while moving.");
                        } else {
                            commitChange(this.getResources()
                                    .getString(R.string.git_commit_move_text,
                                            sourceLongName,
                                            destinationLongName));
                        }
                    }
                    updateListAdapter();
                    if (plist != null) {
                        plist.dismissActionMode();
                    }
                    break;
            }
        }
    }

    private void initRepository(final int operation) {
        PasswordRepository.closeRepository();

        new AlertDialog.Builder(this)
                .setTitle(this.getResources().getString(R.string.location_dialog_title))
                .setMessage(this.getResources().getString(R.string.location_dialog_text))
                .setPositiveButton(this.getResources().getString(R.string.location_hidden), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        settings.edit().putBoolean("git_external", false).apply();

                        switch (operation) {
                            case NEW_REPO_BUTTON:
                                initializeRepositoryInfo();
                                break;
                            case CLONE_REPO_BUTTON:
                                PasswordRepository.initialize(PasswordStore.this);

                                Intent intent = new Intent(activity, GitActivity.class);
                                intent.putExtra("Operation", GitActivity.REQUEST_CLONE);
                                startActivityForResult(intent, GitActivity.REQUEST_CLONE);
                                break;
                        }
                    }
                })
                .setNegativeButton(this.getResources().getString(R.string.location_sdcard), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        settings.edit().putBoolean("git_external", true).apply();

                        if (settings.getString("git_external_repo", null) == null) {
                            Intent intent = new Intent(activity, UserPreference.class);
                            intent.putExtra("operation", "git_external");
                            startActivityForResult(intent, operation);
                        } else {
                            new AlertDialog.Builder(activity).
                                    setTitle("Directory already selected").
                                    setMessage("Do you want to use \"" + settings.getString("git_external_repo", null) + "\"?").
                                    setPositiveButton("Use", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            switch (operation) {
                                                case NEW_REPO_BUTTON:
                                                    initializeRepositoryInfo();
                                                    break;
                                                case CLONE_REPO_BUTTON:
                                                    PasswordRepository.initialize(PasswordStore.this);

                                                    Intent intent = new Intent(activity, GitActivity.class);
                                                    intent.putExtra("Operation", GitActivity.REQUEST_CLONE);
                                                    startActivityForResult(intent, GitActivity.REQUEST_CLONE);
                                                    break;
                                            }
                                        }
                                    }).
                                    setNegativeButton("Change", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent(activity, UserPreference.class);
                                            intent.putExtra("operation", "git_external");
                                            startActivityForResult(intent, operation);
                                        }
                                    }).show();
                        }
                    }
                })
                .show();
    }

    public void matchPasswordWithApp(PasswordItem item) {
        String path = item.getFile().getAbsolutePath();
        path = path.replace(PasswordRepository.getRepositoryDirectory(getApplicationContext()) + "/", "").replace(".gpg", "");
        Intent data = new Intent();
        data.putExtra("path", path);
        setResult(RESULT_OK, data);
        finish();
    }

    private PasswordRepository.PasswordSortOrder getSortOrder() {
        return PasswordRepository.PasswordSortOrder.getSortOrder(settings);
    }
}
