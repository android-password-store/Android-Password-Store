package com.zeapo.pwdstore;

import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;

public class SshKeyGen extends AppCompatActivity {

    // SSH key generation UI
    public static class SshKeyGenFragment extends Fragment {
        public SshKeyGenFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_ssh_keygen, container, false);

            Spinner spinner = (Spinner) v.findViewById(R.id.length);
            Integer[] lengths = new Integer[]{2048, 4096};
            ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(getActivity(),
                    android.R.layout.simple_spinner_dropdown_item, lengths);
            spinner.setAdapter(adapter);

            return v;
        }
    }

    // Displays the generated public key .ssh_key.pub
    public static class ShowSshKeyFragment extends Fragment {
        public ShowSshKeyFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_show_ssh_key, container, false);

            TextView textView = (TextView) v.findViewById(R.id.public_key);
            File file = new File(getActivity().getFilesDir() + "/.ssh_key.pub");
            try {
                textView.setText(FileUtils.readFileToString(file));
                Toast.makeText(getActivity(), "SSH-key generated", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                System.out.println("Exception caught :(");
                e.printStackTrace();
            }

            v.findViewById(R.id.ok_ssh_key).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    getActivity().finish();
                }
            });

            return v;
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setTitle("Generate SSH Key");

        setContentView(R.layout.activity_ssh_keygen);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SshKeyGenFragment()).commit();
        }
    }

    // Invoked when 'Generate' button of SshKeyGenFragment clicked. Generates a
    // private and public key, then replaces the SshKeyGenFragment with a
    // ShowSshKeyFragment which displays the public key.
    public void generate(View view) {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

        Spinner spinner = (Spinner) findViewById(R.id.length);
        int length = (Integer) spinner.getSelectedItem();

        TextView textView = (TextView) findViewById(R.id.passphrase);
        String passphrase = textView.getText().toString();

        textView = (TextView) findViewById(R.id.comment);
        String comment = textView.getText().toString();

        JSch jsch = new JSch();
        try {
            KeyPair kp = KeyPair.genKeyPair(jsch, KeyPair.RSA, length);

            File file = new File(getFilesDir() + "/.ssh_key");
            FileOutputStream out = new FileOutputStream(file, false);
            kp.writePrivateKey(out, passphrase.getBytes());

            file = new File(getFilesDir() + "/.ssh_key.pub");
            out = new FileOutputStream(file, false);
            kp.writePublicKey(out, comment);
        } catch (Exception e) {
            System.out.println("Exception caught :(");
            e.printStackTrace();
            return;
        }
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new ShowSshKeyFragment()).commit();
    }

    // Invoked when 'Copy' button of ShowSshKeyFragment clicked. Copies the
    // displayed public key to the clipboard.
    public void copy (View view) {
        TextView textView = (TextView) findViewById(R.id.public_key);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("public key", textView.getText().toString());
        clipboard.setPrimaryClip(clip);
    }

}
