package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.zeapo.pwdstore.R;

import org.eclipse.jgit.api.Git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.diff.Edit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class GitClone extends Activity implements AdapterView.OnItemSelectedListener {


    /* The clone process has to be on a different thread than the main one */
    private class CloneTask extends AsyncTask<File, Integer, Long> {
        private ProgressDialog dialog;
        private Activity activity;
        private Context context;

        public CloneTask(Activity activity) {
            this.activity = activity;
            context = activity;
            dialog = new ProgressDialog(context);
        }

        protected void onPreExecute() {
            this.dialog.setMessage("Cloning...");
            this.dialog.setCancelable(false);
            this.dialog.show();
        }

        protected void onPostExecute(Long result) {
            this.dialog.dismiss();
        }


        protected Long doInBackground(File... remote) {
            int count = remote.length;
            long totalSize = 0;
            for (int i = 0; i < count; i++) {
                try {
                    Git.cloneRepository().
                            setCloneAllBranches(true).
                            setDirectory(remote[i]).
                            setURI(((TextView) findViewById(R.id.clone_uri)).getText().toString())
                            .call();
                    totalSize++;
                } catch (Exception e) {
                    e.printStackTrace();
                    totalSize++;
                }
            }
            return totalSize;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_git_clone);

        // init the spinner
        Spinner connection_mode_spinner = (Spinner) findViewById(R.id.connection_mode);
        ArrayAdapter<CharSequence> connection_mode_adapter = ArrayAdapter.createFromResource(this,
                R.array.connection_modes, android.R.layout.simple_spinner_item);
        connection_mode_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        connection_mode_spinner.setAdapter(connection_mode_adapter);
        connection_mode_spinner.setOnItemSelectedListener(this);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.git_clone, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void cloneRepository(View view) {

        final File localDir = new File(getApplicationContext().getCacheDir().getAbsoluteFile() + "/store");

        if (localDir.exists()) {
            AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
            builder1.setMessage(R.string.dialog_delete_msg);
            builder1.setCancelable(true);
            builder1.setPositiveButton(R.string.dialog_delete,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            try {
                                FileUtils.deleteDirectory(localDir);
                            } catch (IOException e) {
                                //TODO Handle the exception correctly
                                e.printStackTrace();
                            }

                            dialog.cancel();
                        }
                    }
            );
            builder1.setNegativeButton(R.string.dialog_do_not_delete,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    }
            );

            AlertDialog alert11 = builder1.create();
            alert11.show();
        }


        String connectionMode = ((Spinner) findViewById(R.id.connection_mode)).getSelectedItem().toString();
        if (connectionMode.equalsIgnoreCase("ssh-key")) {

        } else {
            // Set an EditText view to get user input
            final LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText username = new EditText(this);
            username.setHint("Username");
            username.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);

            final EditText password = new EditText(this);
            password.setHint("Password");
            password.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
            password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            layout.addView(username);
            layout.addView(password);


            new AlertDialog.Builder(this)
                    .setTitle("Authenticate")
                    .setMessage("Please provide your usename and password for this repository")
                    .setView(layout)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            //TODO use Jsch to set the authentication method

                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Do nothing.
                        }
                    }).show();
        }

        new CloneTask(this).execute(localDir);
    }


    public void selectConnectionMode(View view) {

    }

    /* when the connection mode is selected */
    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        String selection = ((Spinner) findViewById(R.id.connection_mode)).getSelectedItem().toString();

        if (selection.equalsIgnoreCase("ssh-key")) {
            new AlertDialog.Builder(this)
                    .setMessage("Authentication method not implemented yet")
                    .setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.cancel();
                                }
                            }
                    ).show();
            ((Button) findViewById(R.id.clone_button)).setEnabled(false);
        } else {
            ((Button) findViewById(R.id.clone_button)).setEnabled(true);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }
}
