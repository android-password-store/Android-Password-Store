package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.zeapo.pwdstore.crypto.PgpHandler;
import com.zeapo.pwdstore.utils.PasswordItem;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.util.Stack;


public class PasswordStore extends Activity  implements ToCloneOrNot.OnFragmentInteractionListener, PasswordFragment.OnFragmentInteractionListener {
    private Stack<Integer> scrollPositions;
    /** if we leave the activity to do something, do not add any other fragment */
    public boolean leftActivity = false;
    private File currentDir;
    private SharedPreferences settings;
    private Activity activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pwdstore);
        scrollPositions = new Stack<Integer>();
        settings = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        activity = this;
    }

    @Override
    public void onResume(){
        super.onResume();

        // create the repository static variable in PasswordRepository
        PasswordRepository.getRepository(new File(getFilesDir() + "/store/.git"));

        // re-check that there was no change with the repository state
        checkLocalRepository();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.pwdstore, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        Intent intent;

        switch (id) {
            case R.id.user_pref:
                try {
                    intent = new Intent(this, UserPreference.class);
                    startActivity(intent);
                } catch (Exception e) {
                    System.out.println("Exception caught :(");
                    e.printStackTrace();
                }
                this.leftActivity = true;
                return true;

            case R.id.menu_add_password:
                createPassword(getCurrentFocus());
                break;

//            case R.id.menu_add_category:
//                break;

            case R.id.git_push:
                intent = new Intent(this, GitHandler.class);
                intent.putExtra("Operation", GitHandler.REQUEST_PUSH);
                startActivityForResult(intent, GitHandler.REQUEST_PUSH);
                this.leftActivity = true;
                return true;

            case R.id.git_pull:
                intent = new Intent(this, GitHandler.class);
                intent.putExtra("Operation", GitHandler.REQUEST_PULL);
                startActivityForResult(intent, GitHandler.REQUEST_PULL);
                this.leftActivity = true;
                return true;

            case R.id.referesh:
                refreshListAdapter();
                return true;

            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    public void getClone(View view){
        Intent intent = new Intent(this, GitHandler.class);
        intent.putExtra("Operation", GitHandler.REQUEST_CLONE);
        startActivity(intent);
    }

    private void createRepository() {
        final String keyId = settings.getString("openpgp_key_ids", "");

        File localDir = new File(getFilesDir() + "/store/");
        localDir.mkdir();
        try {
            // we take only the first key-id, we have to think about how to handle multiple keys, and why should we do that...
            // also, for compatibility use short-version of the key-id
            FileUtils.writeStringToFile(new File(localDir.getAbsolutePath() + "/.gpg-id"),
                    keyId.substring(keyId.length() - 8));
        } catch (Exception e) {
            localDir.delete();
            return;
        }
        PasswordRepository.createRepository(localDir);
        checkLocalRepository();
    }

    public void initRepository(View view) {
        final String keyId = settings.getString("openpgp_key_ids", "");

        if (keyId.isEmpty())
            new AlertDialog.Builder(this)
                    .setMessage("You have to select your \"PGP-Key ID\" before initializing the repository")
                    .setPositiveButton("On my way!", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(activity, UserPreference.class);
                            startActivityForResult(intent, GitHandler.REQUEST_INIT);
                        }
                    })
                    .setNegativeButton("Nah... later", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            // do nothing :(
                        }
                    })
                    .show();

        else {
            new AlertDialog.Builder(this)
                    .setMessage("Which connection method do you prefer?")
                    .setPositiveButton("ssh-key", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            settings.edit().putString("git_remote_auth", "ssh-key").apply();
                            createRepository();
                        }
                    })
                    .setNegativeButton("username/password", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            settings.edit().putString("git_remote_auth", "username/password").apply();
                            createRepository();
                        }
                    })
                    .setCancelable(false)


                    .show();

        }
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    private void checkLocalRepository() {
        checkLocalRepository(PasswordRepository.getWorkTree());
    }

    private void checkLocalRepository(File localDir) {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        // if we are coming back from gpg do not anything
        if (this.leftActivity) {
            this.leftActivity = false;
            return;
        }

        int status = 0;

        if (localDir.exists()) {
            File[] folders = localDir.listFiles();
            status = folders.length;

            // this means that the repository has been correctly cloned
            if ((new File(localDir.getAbsolutePath() + "/.gpg-id")).exists())
                status++;
        }

        // either the repo is empty or it was not correctly cloned
        switch (status) {
            case 0:
                if(!localDir.equals(PasswordRepository.getWorkTree()))
                    break;

                ToCloneOrNot cloneFrag = new ToCloneOrNot();
                fragmentTransaction.replace(R.id.main_layout, cloneFrag, "ToCloneOrNot");
                fragmentTransaction.commit();
                break;
            default:
                PasswordFragment passFrag = new PasswordFragment();
                Bundle args = new Bundle();
                args.putString("Path", localDir.getAbsolutePath());

                if (!scrollPositions.isEmpty())
                    args.putInt("Position", scrollPositions.pop());
                else
                    args.putInt("Position", 0);

                passFrag.setArguments(args);

                if (fragmentManager.findFragmentByTag("PasswordsList") != null)
                    fragmentTransaction.addToBackStack("passlist");

                fragmentTransaction.replace(R.id.main_layout, passFrag, "PasswordsList");
                fragmentTransaction.commit();
        }

        this.leftActivity = false;
    }

    /** Stack the positions the different fragments were at */
    @Override
    public void savePosition(Integer position) {
        this.scrollPositions.push(position);
    }

    /* If an item is clicked in the list of passwords, this will be triggered */
    @Override
    public void onFragmentInteraction(PasswordItem item) {
        if (item.getType() == PasswordItem.TYPE_CATEGORY) {
            checkLocalRepository(item.getFile());
        }
    }
    public void decryptPassword(PasswordItem item) {
        try {
            this.leftActivity = true;

            Intent intent = new Intent(this, PgpHandler.class);
            intent.putExtra("PGP-ID", FileUtils.readFileToString(PasswordRepository.getFile("/.gpg-id")));
            intent.putExtra("NAME", item.toString());
            intent.putExtra("FILE_PATH", item.getFile().getAbsolutePath());
            intent.putExtra("Operation", "DECRYPT");
            startActivityForResult(intent, PgpHandler.REQUEST_CODE_DECRYPT_AND_VERIFY);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createPassword(View v) {
        this.currentDir = getCurrentDir();
        Log.i("PWDSTR", "Adding file to : " + this.currentDir.getAbsolutePath());
        this.leftActivity = true;

        try {
            Intent intent = new Intent(this, PgpHandler.class);
            intent.putExtra("PGP-ID", FileUtils.readFileToString(PasswordRepository.getFile("/.gpg-id")));
            intent.putExtra("FILE_PATH", this.currentDir.getAbsolutePath());
            intent.putExtra("Operation", "ENCRYPT");
            startActivityForResult(intent, PgpHandler.REQUEST_CODE_ENCRYPT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deletePassword(final PasswordItem item) {
        new AlertDialog.Builder(this).
                setMessage("Are you sure you want to delete the password \"" +
                        item + "\"")
                .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String path = item.getFile().getAbsolutePath();
                        item.getFile().delete();

                        setResult(RESULT_CANCELED);
                        Git git = new Git(PasswordRepository.getRepository(new File("")));
                        GitAsyncTask tasks = new GitAsyncTask(activity, false, true);
                        System.out.println(tasks);
                        tasks.execute(
                                git.rm().addFilepattern(path.replace(PasswordRepository.getWorkTree() + "/", "")),
                                git.commit().setMessage("[ANDROID PwdStore] Remove " + item + " from store.")
                        );
                    }
                })
                .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .show();
    }

    public void refreshListAdapter() {
        PasswordFragment plist;
        if  (null !=
                (plist = (PasswordFragment) getFragmentManager().findFragmentByTag("PasswordsList"))) {
            plist.updateAdapter();
        }
    }

    private File getCurrentDir() {
        return new File(((PasswordFragment) getFragmentManager().findFragmentByTag("PasswordsList")).getArguments().getString("Path"));
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_CANCELED)
            refreshListAdapter();

        if (resultCode == RESULT_OK) {
            refreshListAdapter();

            switch (requestCode) {
                case PgpHandler.REQUEST_CODE_ENCRYPT :
                    Git git = new Git(PasswordRepository.getRepository(new File("")));
                    GitAsyncTask tasks = new GitAsyncTask(this, false, false);
                    tasks.execute(
                            git.add().addFilepattern("."),
                            git.commit().setMessage("[ANDROID PwdStore] Add " + data.getExtras().getString("NAME") + " from store.")
                    );
                    break;
                case GitHandler.REQUEST_INIT:
                    initRepository(getCurrentFocus());
                    break;
            }

        }
    }
}
