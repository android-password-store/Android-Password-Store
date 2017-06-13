package com.zeapo.pwdstore.git;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.support.design.widget.Snackbar;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.widget.Toast;

import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.StatusCommand;


public class GitAsyncTask extends AsyncTask<GitCommand, String, String> {
    private Activity activity;
    private boolean lockAndFinishActivity;
    private boolean refreshListOnEnd;
    private ProgressDialog dialog;
    private GitOperation operation;
    private Snackbar snack;

    public GitAsyncTask(Activity activity, boolean lockAndFinishActivity, boolean refreshListOnEnd, GitOperation operation) {
        this.activity = activity;
        this.lockAndFinishActivity = lockAndFinishActivity;
        this.refreshListOnEnd = refreshListOnEnd;
        this.operation = operation;

        this.dialog = new ProgressDialog(this.activity);
    }

    protected void onPreExecute() {
        final Spanned message = Html.fromHtml(String.format("<font color=\"#ffffff\">Running %s</font>", operation.getClass().getSimpleName()));

        if (lockAndFinishActivity) {
            this.dialog.setMessage(message);
            this.dialog.setCancelable(false);
            this.dialog.show();
        } else {
            Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    protected void onProgressUpdate(String... progress) {
        final Spanned message = Html.fromHtml(String.format("<font color=\"#ffffff\">Running jgit command: <strong>%s</strong></font>", progress[0]));
        if (lockAndFinishActivity) {
            this.dialog.setMessage(message);
        } else {
            Toast.makeText(activity.getApplicationContext(), message, Toast.LENGTH_LONG).show();
        }
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
                String opName = command.getClass().getSimpleName();
                publishProgress(opName);
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
            if (lockAndFinishActivity) {
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
