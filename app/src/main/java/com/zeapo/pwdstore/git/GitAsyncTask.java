package com.zeapo.pwdstore.git;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;
import java.lang.ref.WeakReference;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;

public class GitAsyncTask extends AsyncTask<GitCommand, Integer, String> {
    private WeakReference<Activity> activityWeakReference;
    private boolean finishOnEnd;
    private boolean refreshListOnEnd;
    private ProgressDialog dialog;
    private GitOperation operation;

    public GitAsyncTask(
            Activity activity,
            boolean finishOnEnd,
            boolean refreshListOnEnd,
            GitOperation operation) {
        this.activityWeakReference = new WeakReference<>(activity);
        this.finishOnEnd = finishOnEnd;
        this.refreshListOnEnd = refreshListOnEnd;
        this.operation = operation;

        dialog = new ProgressDialog(getActivity());
    }

    private Activity getActivity() {
        return activityWeakReference.get();
    }

    protected void onPreExecute() {
        this.dialog.setMessage(
                getActivity().getResources().getString(R.string.running_dialog_text));
        this.dialog.setCancelable(false);
        this.dialog.show();
    }

    @Override
    protected String doInBackground(GitCommand... commands) {
        Integer nbChanges = null;
        final Activity activity = getActivity();
        for (GitCommand command : commands) {
            try {
                if (command instanceof StatusCommand) {
                    // in case we have changes, we want to keep track of it
                    org.eclipse.jgit.api.Status status = ((StatusCommand) command).call();
                    nbChanges = status.getChanged().size() + status.getMissing().size();
                } else if (command instanceof CommitCommand) {
                    // the previous status will eventually be used to avoid a commit
                    if (nbChanges == null || nbChanges > 0) command.call();
                } else if (command instanceof PullCommand) {
                    final PullResult result = ((PullCommand) command).call();
                    final RebaseResult rr = result.getRebaseResult();

                    if (rr.getStatus() == RebaseResult.Status.STOPPED) {
                        return activity.getString(R.string.git_pull_fail_error);
                    }

                } else if (command instanceof PushCommand) {
                    for (final PushResult result : ((PushCommand) command).call()) {
                        // Code imported (modified) from Gerrit PushOp, license Apache v2
                        for (final RemoteRefUpdate rru : result.getRemoteUpdates()) {
                            switch (rru.getStatus()) {
                                case REJECTED_NONFASTFORWARD:
                                    return activity.getString(R.string.git_push_nff_error);
                                case REJECTED_NODELETE:
                                case REJECTED_REMOTE_CHANGED:
                                case NON_EXISTING:
                                case NOT_ATTEMPTED:
                                    return activity.getString(R.string.git_push_generic_error)
                                            + rru.getStatus().name();
                                case REJECTED_OTHER_REASON:
                                    if ("non-fast-forward".equals(rru.getMessage())) {
                                        return activity.getString(R.string.git_push_other_error);
                                    } else {
                                        return activity.getString(R.string.git_push_generic_error)
                                                + rru.getMessage();
                                    }
                                default:
                                    break;
                            }
                        }
                    }
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

        if (result == null) result = "Unexpected error";

        if (!result.isEmpty()) {
            this.operation.onError(result);
        } else {
            this.operation.onSuccess();

            if (finishOnEnd) {
                this.getActivity().setResult(Activity.RESULT_OK);
                this.getActivity().finish();
            }

            if (refreshListOnEnd) {
                try {
                    ((PasswordStore) this.getActivity()).updateListAdapter();
                } catch (ClassCastException e) {
                    // oups, mistake
                }
            }
        }
    }
}
