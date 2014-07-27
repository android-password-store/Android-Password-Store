package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;


public class PasswordStore extends Activity  implements ToCloneOrNot.OnFragmentInteractionListener, PasswordFragment.OnFragmentInteractionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pwdstore);
    }

    @Override
    public void onResume(){
        super.onResume();
        // re-check that there was no change with the repository state
        checkLocalRepository();
        Repository repository = PasswordRepository.getRepository(new File(getFilesDir() + "/store/.git"));
        PasswordRepository.getFilesList();
        PasswordRepository.getPasswords();
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
//        int id = item.getItemId();
//        if (id == R.id.clone_setting) {
//            getClone();
//            return true;
//        }
        return super.onOptionsItemSelected(item);
    }

    public void getClone(View view){
        Intent intent = new Intent(this, GitClone.class);
        startActivity(intent);
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

    private void checkLocalRepository() {
        int status = 0;
        final File localDir = new File(getFilesDir() + "/store/.git");

        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        if (localDir.exists()) {
            File[] folders = localDir.listFiles((FileFilter) FileFilterUtils.directoryFileFilter());
            status = folders.length;
        }

        // either the repo is empty or it was not correctly cloned
        switch (status) {
            case 0:
                ToCloneOrNot cloneFrag = new ToCloneOrNot();
                fragmentTransaction.replace(R.id.main_layout, cloneFrag);
                fragmentTransaction.commit();
                break;
            case 1:
                // empty
                break;
            default:
                PasswordFragment passFrag = new PasswordFragment();
                fragmentTransaction.replace(R.id.main_layout, passFrag);
                fragmentTransaction.commit();
        }
    }

    /* If an item is clicked in the list of passwords, this will be triggered */
    @Override
    public void onFragmentInteraction(String id) {

        try {
            for (String l : (List<String>) FileUtils.readLines(PasswordRepository.getFile(id), null)) {
                System.out.println(l);
            }
        } catch (IOException e) {
            //TODO handle problems
            e.printStackTrace();
        }
    }

}
