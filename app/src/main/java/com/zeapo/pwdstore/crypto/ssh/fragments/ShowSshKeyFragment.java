package com.zeapo.pwdstore.crypto.ssh.fragments;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.crypto.ssh.SshKeyGen;

import org.apache.commons.io.FileUtils;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;

// Displays the generated public key .ssh_key.pub
public class ShowSshKeyFragment extends DialogFragment {
    @BindView(R.id.public_key)
    TextView textView;

    public ShowSshKeyFragment() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        @SuppressLint("InflateParams") final View v = inflater.inflate(R.layout.fragment_show_ssh_key, null);
        ButterKnife.bind(this, v);
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
