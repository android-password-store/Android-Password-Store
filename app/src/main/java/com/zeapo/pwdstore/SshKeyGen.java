package com.zeapo.pwdstore;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

public class SshKeyGen extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle("Generate SSH Key");

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
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
        new KeyGenerateTask(this).execute(length, passphrase, comment);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // SSH key generation UI
    public static class SshKeyGenFragment extends Fragment {
        public SshKeyGenFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            final View v = inflater.inflate(R.layout.fragment_ssh_keygen, container, false);
            Typeface monoTypeface = Typeface.createFromAsset(requireContext().getAssets(), "fonts/sourcecodepro.ttf");

            Spinner spinner = v.findViewById(R.id.length);
            Integer[] lengths = new Integer[]{2048, 4096};
            ArrayAdapter<Integer> adapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_dropdown_item, lengths);
            spinner.setAdapter(adapter);

            ((EditText) v.findViewById(R.id.passphrase)).setTypeface(monoTypeface);

            CheckBox checkbox = v.findViewById(R.id.show_passphrase);
            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                EditText editText = v.findViewById(R.id.passphrase);
                int selection = editText.getSelectionEnd();
                if (isChecked) {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                } else {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                editText.setSelection(selection);
            });

            return v;
        }
    }

    // Displays the generated public key .ssh_key.pub
    public static class ShowSshKeyFragment extends DialogFragment {
        public ShowSshKeyFragment() { }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final FragmentActivity activity = requireActivity();
            final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            LayoutInflater inflater = activity.getLayoutInflater();
            @SuppressLint("InflateParams") final View v = inflater.inflate(R.layout.fragment_show_ssh_key, null);
            builder.setView(v);

            TextView textView = v.findViewById(R.id.public_key);
            File file = new File(activity.getFilesDir() + "/.ssh_key.pub");
            try {
                textView.setText(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.out.println("Exception caught :(");
                e.printStackTrace();
            }

            builder.setPositiveButton(getResources().getString(R.string.dialog_ok), (dialog, which) -> {
                if (activity instanceof SshKeyGen)
                    activity.finish();
            });

            builder.setNegativeButton(getResources().getString(R.string.dialog_cancel), (dialog, which) -> {

            });

            builder.setNeutralButton(getResources().getString(R.string.ssh_keygen_copy), null);

            final AlertDialog ad = builder.setTitle("Your public key").create();
            ad.setOnShowListener(dialog -> {
                Button b = ad.getButton(AlertDialog.BUTTON_NEUTRAL);
                b.setOnClickListener(v1 -> {
                    TextView textView1 = getDialog().findViewById(R.id.public_key);
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("public key", textView1.getText().toString());
                    clipboard.setPrimaryClip(clip);
                });
            });
            return ad;
        }
    }

    private static class KeyGenerateTask extends AsyncTask<String, Void, Exception> {
        private ProgressDialog pd;
        private WeakReference<SshKeyGen> weakReference;

        private KeyGenerateTask(final SshKeyGen activity) {
            weakReference = new WeakReference<>(activity);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            pd = ProgressDialog.show(weakReference.get(), "", "Generating keys");
        }

        protected Exception doInBackground(String... strings) {
            int length = Integer.parseInt(strings[0]);
            String passphrase = strings[1];
            String comment = strings[2];

            JSch jsch = new JSch();
            try {
                KeyPair kp = KeyPair.genKeyPair(jsch, KeyPair.RSA, length);

                File file = new File(weakReference.get().getFilesDir() + "/.ssh_key");
                FileOutputStream out = new FileOutputStream(file, false);
                if (passphrase.length() > 0) {
                    kp.writePrivateKey(out, passphrase.getBytes());
                } else {
                    kp.writePrivateKey(out);
                }

                file = new File(weakReference.get().getFilesDir() + "/.ssh_key.pub");
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
                Toast.makeText(weakReference.get(), "SSH-key generated", Toast.LENGTH_LONG).show();
                DialogFragment df = new ShowSshKeyFragment();
                df.show(weakReference.get().getSupportFragmentManager(), "public_key");
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(weakReference.get());
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("use_generated_key", true);
                editor.apply();
            } else {
                new MaterialAlertDialogBuilder(weakReference.get(), R.style.AppTheme_Dialog)
                        .setTitle("Error while trying to generate the ssh-key")
                        .setMessage(weakReference.get().getResources().getString(R.string.ssh_key_error_dialog_text) + e.getMessage())
                        .setPositiveButton(weakReference.get().getResources().getString(R.string.dialog_ok), (dialogInterface, i) -> {
                            // pass
                        }).show();
            }
        }
    }
}
