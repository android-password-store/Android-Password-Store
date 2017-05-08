package com.zeapo.pwdstore.git;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.util.Log;

import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.StatusCommand;


public class GitAsyncTask extends AsyncTask<GitCommand, Integer, String> {
    private Activity activity;
    private boolean finishOnEnd;
    private boolean refreshListOnEnd;
    private ProgressDialog dialog;
    private GitOperation operation;

    public GitAsyncTask(Activity activity, boolean finishOnEnd, boolean refreshListOnEnd, GitOperation operation) {
        this.activity = activity;
        this.finishOnEnd = finishOnEnd;
        this.refreshListOnEnd = refreshListOnEnd;
        this.operation = operation;

        dialog = new ProgressDialog(this.activity);
    }

    protected void onPreExecute() {
        this.dialog.setMessage(activity.getResources().getString(R.string.running_dialog_text));
        this.dialog.setCancelable(false);
        this.dialog.show();
    }

    @Override
    protected String doInBackground(GitCommand... commands) {
        Integer nbChanges = null;
        for (GitCommand command : commands) {
            Log.d("doInBackground", "Executing the command <" + command.toString() + ">");
            try {
                if (command instanceof StatusCommand) {
                    // in case we have changes, we want to keep track of it
                    nbChanges = ((StatusCommand) command).call().getChanged().size();
                } else if (command instanceof CommitCommand) {
                    // the previous status will eventually be used to avoid a commit
                    if (nbChanges == null || nbChanges > 0)
                        command.call();
                } else {
                    command.call();
                }

            } catch (Exception e) {
                e.printStackTrace();
                return e.getMessage() + "\nCaused by:\n" + e.getCause();
            }
        }
        return "";
    }

    protected void onPostExecute(String result) {
        if (this.dialog != null)
            try {
                this.dialog.dismiss();
            } catch (Exception e) {
                // ignore
            }

        if (result == null)
            result = "Unexpected error";

        if (!result.isEmpty()) {
            this.operation.onTaskEnded(result);
        } else {
            if (finishOnEnd) {
                this.activity.setResult(Activity.RESULT_OK);
                this.activity.finish();
            }

            if (refreshListOnEnd) {
                try {
                    ((PasswordStore) this.activity).updateListAdapter();
                } catch (ClassCastException e) {
                    // oups, mistake
                }
            }
        }
    }
}
