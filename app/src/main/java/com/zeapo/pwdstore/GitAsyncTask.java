package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Log;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.TransportException;


public class GitAsyncTask extends AsyncTask<GitCommand, Integer, Integer> {
    private Activity activity;
    private boolean finishOnEnd;
    private boolean refreshListOnEnd;
    private ProgressDialog dialog;

    public GitAsyncTask(Activity activity, boolean finishOnEnd, boolean refreshListOnEnd) {
        this.activity = activity;
        this.finishOnEnd = finishOnEnd;
        this.refreshListOnEnd = refreshListOnEnd;

        dialog = new ProgressDialog(this.activity);
    }

    protected void onPreExecute() {
        this.dialog.setMessage("Running command...");
        this.dialog.setCancelable(false);
        this.dialog.show();
    }

    @Override
    protected Integer doInBackground(GitCommand... cmd) {
        int count = cmd.length;
        Integer totalSize = 0;
        for (int i = 0; i < count; i++) {
            try {
                cmd[i].call();
            } catch (JGitInternalException e) {
                e.printStackTrace();
                return -99;
            } catch (InvalidRemoteException e) {
                e.printStackTrace();
                return -1;
            } catch (TransportException e) {
                e.printStackTrace();
                return -2;
            } catch (Exception e) {
                e.printStackTrace();
                return -98;
            }
            totalSize++;
        }
        return totalSize;
    }

    protected void onPostExecute(Integer result) {
        Log.i("GIT_ASYNC", result + "");
        this.dialog.dismiss();
        if (finishOnEnd) {
            this.activity.setResult(Activity.RESULT_OK);
            this.activity.finish();
        }

        if (refreshListOnEnd) {
            try {
                ((PasswordStore) this.activity).refreshListAdapter();
            } catch (ClassCastException e){
                // oups, mistake
            }
        }
    }
}
