package com.zeapo.pwdstore;

import android.app.Activity;
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
    public GitAsyncTask(Activity activity, boolean finishOnEnd) {
        this.activity = activity;
        this.finishOnEnd = finishOnEnd;
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
            }
            totalSize++;
        }
        return totalSize;
    }

    protected void onPostExecute(Integer result) {
        Log.i("GIT_ASYNC", result + "");
        if (finishOnEnd) {
            this.activity.finish();
        }
    }
}
