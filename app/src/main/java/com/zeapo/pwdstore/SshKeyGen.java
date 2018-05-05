package com.zeapo.pwdstore;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SshKeyGen extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle("Generate SSH Key");

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SshKeyGenFragment()).commit();
        }
    }

    // Invoked when 'Generate' button of SshKeyGenFragment clicked. Generates a
    // private and public key, then replaces the SshKeyGenFragment with a
    // ShowSshKeyFragment which displays the public key.
    public void generate(View view) {
        String length = Integer.toString((Integer) ((Spinner) findViewById(R.id.length)).getSelectedItem());
        String passphrase = ((EditText) findViewById(R.id.passphrase)).getText().toString();
        String comment = ((EditText) findViewById(R.id.comment)).getText().toString();
        new generateTask().execute(length, passphrase, comment);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // SSH key generation UI
    public static class SshKeyGenFragment extends Fragment {
        @BindView(R.id.length)
        Spinner spinner;
        @BindView(R.id.show_passphrase)
        CheckBox checkbox;
        @BindView(R.id.passphrase)
        EditText editText;

        public SshKeyGenFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            final View v = inflater.inflate(R.layout.fragment_ssh_keygen, container, false);
            ButterKnife.bind(this, v);
            Typeface monoTypeface = Typeface.createFromAsset(getActivity().getAssets(), "fonts/sourcecodepro.ttf");

            Integer[] lengths = new Integer[]{2048, 4096};
            ArrayAdapter<Integer> adapter = new ArrayAdapter<>(getActivity(),
                    android.R.layout.simple_spinner_dropdown_item, lengths);

            spinner.setAdapter(adapter);
            editText.setTypeface(monoTypeface);
            checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    int selection = editText.getSelectionEnd();
                    if (isChecked) {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    } else {
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    }
                    editText.setSelection(selection);
                }
            });

            return v;
        }
    }

    // Displays the generated public key .ssh_key.pub
    public static class ShowSshKeyFragment extends DialogFragment {
        @BindView(R.id.public_key)
        TextView textView;

        public ShowSshKeyFragment() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = getActivity().getLayoutInflater();
            @SuppressLint("InflateParams") final View v = inflater.inflate(R.layout.fragment_show_ssh_key, null);
            builder.setView(v);

            File file = new File(getActivity().getFilesDir() + "/.ssh_key.pub");
            try {
                textView.setText(FileUtils.readFileToString(file));
            } catch (Exception e) {
                System.out.println("Exception caught :(");
                e.printStackTrace();
            }

            builder.setPositiveButton(getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (getActivity() instanceof SshKeyGen)
                        getActivity().finish();
                }
            });

            builder.setNegativeButton(getResources().getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });

            builder.setNeutralButton(getResources().getString(R.string.ssh_keygen_copy), null);

            final AlertDialog ad = builder.setTitle("Your public key").create();
            ad.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    Button b = ad.getButton(AlertDialog.BUTTON_NEUTRAL);
                    b.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            TextView textView = (TextView) getDialog().findViewById(R.id.public_key);
                            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("public key", textView.getText().toString());
                            clipboard.setPrimaryClip(clip);
                        }
                    });
                }
            });
            return ad;
        }
    }

    private class generateTask extends AsyncTask<String, Void, Exception> {
        private ProgressDialog pd;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pd = ProgressDialog.show(SshKeyGen.this, "", "Generating keys");
        }

        protected Exception doInBackground(String... strings) {
            int length = Integer.parseInt(strings[0]);
            String passphrase = strings[1];
            String comment = strings[2];

            JSch jsch = new JSch();
            try {
                KeyPair kp = KeyPair.genKeyPair(jsch, KeyPair.RSA, length);

                File file = new File(getFilesDir() + "/.ssh_key");
                FileOutputStream out = new FileOutputStream(file, false);
                if (passphrase.length() > 0) {
                    kp.writePrivateKey(out, passphrase.getBytes());
                } else {
                    kp.writePrivateKey(out);
                }

                file = new File(getFilesDir() + "/.ssh_key.pub");
                out = new FileOutputStream(file, false);
                kp.writePublicKey(out, comment);
                return null;
            } catch (Exception e) {
                System.out.println("Exception caught :(");
                e.printStackTrace();
                return e;
            }
        }

        @Override
        protected void onPostExecute(Exception e) {
            super.onPostExecute(e);
            pd.dismiss();
            if (e == null) {
                Toast.makeText(SshKeyGen.this, "SSH-key generated", Toast.LENGTH_LONG).show();
                DialogFragment df = new ShowSshKeyFragment();
                df.show(getFragmentManager(), "public_key");
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("use_generated_key", true);
                editor.apply();
            } else {
                new AlertDialog.Builder(SshKeyGen.this)
                        .setTitle("Error while trying to generate the ssh-key")
                        .setMessage(getResources().getString(R.string.ssh_key_error_dialog_text) + e.getMessage())
                        .setPositiveButton(getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // pass
                            }
                        }).show();
            }

        }
    }
}
