package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.zeapo.pwdstore.crypto.PgpHandler;
import com.zeapo.pwdstore.git.GitActivity;
import com.zeapo.pwdstore.git.GitAsyncTask;
import com.zeapo.pwdstore.utils.PasswordItem;
import com.zeapo.pwdstore.utils.PasswordRecyclerAdapter;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;

import java.io.File;

public class PasswordStore extends ActionBarActivity  {
    private static final String TAG = "PwdStrAct";
    private File currentDir;
    private SharedPreferences settings;
    private Activity activity;
    private PasswordFragment plist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pwdstore);
        settings = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        activity = this;
    }

    @Override
    public void onResume(){
        super.onResume();
        // create the repository static variable in PasswordRepository
        PasswordRepository.getRepository(new File(getFilesDir() + this.getResources().getString(R.string.store_git)));

        checkLocalRepository();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

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

    public void getClone(View view){
        Intent intent = new Intent(this, GitActivity.class);
        intent.putExtra("Operation", GitActivity.REQUEST_CLONE);
        startActivityForResult(intent, GitActivity.REQUEST_CLONE);
    }

    private void createRepository() {
//        final String keyId = settings.getString("openpgp_key_ids", "");

        File localDir = new File(getFilesDir() + "/store/");
        localDir.mkdir();
        try {
            PasswordRepository.createRepository(localDir);

            settings.edit().putBoolean("repository_initialized", true).apply();
        } catch (Exception e) {
            e.printStackTrace();
            localDir.delete();
            return;
        }
        checkLocalRepository();
    }

    public void initRepository(View view) {
        final String keyId = settings.getString("openpgp_key_ids", "");

        if (keyId != null && keyId.isEmpty())
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
        checkLocalRepository(PasswordRepository.getWorkTree());
    }

    private void checkLocalRepository(File localDir) {
        Log.d("PASS", "Check, dir: " + localDir.getAbsolutePath());
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        // TODO: Remove the getpaswords() as it is a temporary fix until every user has the repository_initialized set
        if (settings.getBoolean("repository_initialized", false) || PasswordRepository.getPasswords(localDir).size() > 0) {
            // do not push the fragment if we already have it
            if (fragmentManager.findFragmentByTag("PasswordsList") == null) {

                // clean things up
                if (fragmentManager.findFragmentByTag("ToCloneOrNot") != null) {
                    fragmentManager.popBackStack();
                }

                PasswordRepository.setInitialized(true);
                plist = new PasswordFragment();
                Bundle args = new Bundle();
                args.putString("Path", PasswordRepository.getWorkTree().getAbsolutePath());

                plist.setArguments(args);

                fragmentTransaction.addToBackStack("passlist");

                getSupportActionBar().show();

                fragmentTransaction.replace(R.id.main_layout, plist, "PasswordsList");
                fragmentTransaction.commit();
            }
        } else {
            // if we still have the pass list (after deleting the repository for instance) remove it
            if (fragmentManager.findFragmentByTag("PasswordsList") != null) {
                fragmentManager.popBackStack();
            }

            getSupportActionBar().hide();

            ToCloneOrNot cloneFrag = new ToCloneOrNot();
            fragmentTransaction.replace(R.id.main_layout, cloneFrag, "ToCloneOrNot");
            fragmentTransaction.commit();
        }
    }



    @Override
    public void onBackPressed() {
        if  ((null != plist) && plist.isNotEmpty()) {
            plist.popBack();
        } else {
            super.onBackPressed();
        }

        if (null != plist && !plist.isNotEmpty()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
    }

    public void decryptPassword(PasswordItem item) {
        Intent intent = new Intent(this, PgpHandler.class);
        intent.putExtra("NAME", item.toString());
        intent.putExtra("FILE_PATH", item.getFile().getAbsolutePath());
        intent.putExtra("Operation", "DECRYPT");
        startActivityForResult(intent, PgpHandler.REQUEST_CODE_DECRYPT_AND_VERIFY);
    }

    public void createPassword(View v) {
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

        this.currentDir = getCurrentDir();
        Log.i("PWDSTR", "Adding file to : " + this.currentDir.getAbsolutePath());

        Intent intent = new Intent(this, PgpHandler.class);
        intent.putExtra("FILE_PATH", getCurrentDir().getAbsolutePath());
        intent.putExtra("Operation", "ENCRYPT");
        startActivityForResult(intent, PgpHandler.REQUEST_CODE_ENCRYPT);
    }

    public void deletePassword(final PasswordRecyclerAdapter adapter, final int position) {
        final PasswordItem item = adapter.getValues().get(position);
        new AlertDialog.Builder(this).
                setMessage(this.getResources().getString(R.string.delete_dialog_text) +
                        item + "\"")
                .setPositiveButton(this.getResources().getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String path = item.getFile().getAbsolutePath();
                        item.getFile().delete();
                        adapter.remove(position);

                        setResult(RESULT_CANCELED);
                        Git git = new Git(PasswordRepository.getRepository(new File("")));
                        GitAsyncTask tasks = new GitAsyncTask(activity, false, true, CommitCommand.class);
                        System.out.println(tasks);
                        tasks.execute(
                                git.rm().addFilepattern(path.replace(PasswordRepository.getWorkTree() + "/", "")),
                                git.commit().setMessage("[ANDROID PwdStore] Remove " + item + " from store.")
                        );
                    }
                })
                .setNegativeButton(this.getResources().getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .show();
    }

    /**
     * clears adapter's content and updates it with a fresh list of passwords from the root
     */
    public void updateListAdapter() {
        if  ((null != plist)) {
            plist.updateAdapter();
        }
    }

    /**
     * Updates the adapter with the current view of passwords
     */
    public void refreshListAdapter() {
        if  ((null != plist)) {
            plist.refreshAdapter();
        }
    }

    public void filterListAdapter(String filter) {
        if  ((null != plist)) {
            plist.filterAdapter(filter);
        }
    }

    private File getCurrentDir() {
        if  ((null != plist)) {
            return plist.getCurrentDir();
        }
        return PasswordRepository.getWorkTree();
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case GitActivity.REQUEST_CLONE:
                    // if we get here with a RESULT_OK then it's probably OK :)
                    settings.edit().putBoolean("repository_initialized", true).apply();
                    break;
                case PgpHandler.REQUEST_CODE_ENCRYPT :
                    Git git = new Git(PasswordRepository.getRepository(new File("")));
                    GitAsyncTask tasks = new GitAsyncTask(this, false, false, CommitCommand.class);
                    tasks.execute(
                            git.add().addFilepattern("."),
                            git.commit().setMessage(this.getResources().getString(R.string.add_commit_text) + data.getExtras().getString("NAME") + this.getResources().getString(R.string.from_store))
                    );
                    refreshListAdapter();
                    break;
                case GitActivity.REQUEST_INIT:
                    initRepository(getCurrentFocus());
                    break;
                case GitActivity.REQUEST_PULL:
                    updateListAdapter();
                    break;
            }

        }
    }
}
