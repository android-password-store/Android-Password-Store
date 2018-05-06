package com.zeapo.pwdstore.crypto.ssh;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.crypto.ssh.fragments.ShowSshKeyFragment;
import com.zeapo.pwdstore.crypto.ssh.fragments.SshKeyGenFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasFragmentInjector;
import dagger.android.support.DaggerAppCompatActivity;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class SshKeyGen extends AppCompatActivity implements HasFragmentInjector {

    @BindView(R.id.length)
    Spinner lengthStrSpinner;
    @BindView(R.id.passphrase)
    EditText passphraseEditText;
    @BindView(R.id.comment)
    EditText commentEditText;
    ShowSshKeyFragment showSshKeyFragment;
    SshKeyGenFragment sshKeyGenFragment;
    private ProgressDialog progressDialog;
    @Inject
    DispatchingAndroidInjector<Fragment> fragmentDispatchingAndroidInjector;
    @Inject
    JSch jSch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showSshKeyFragment = new ShowSshKeyFragment();
        sshKeyGenFragment = new SshKeyGenFragment();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setTitle("Generate SSH Key");

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, sshKeyGenFragment).commit();
        }
    }

    // Invoked when 'Generate' button of SshKeyGenFragment clicked. Generates a
    // private and public key, then replaces the SshKeyGenFragment with a
    // ShowSshKeyFragment which displays the public key.
    public void generate(View view) {
        ButterKnife.bind(this);

        String lengthStr = Integer.toString((Integer) lengthStrSpinner.getSelectedItem());
        String passphrase = passphraseEditText.toString();
        String comment = commentEditText.getText().toString();

        progressDialog = ProgressDialog.show(this, "", "Generating keys");

        Disposable d = Observable.fromCallable(generateSshKeyCallable(lengthStr, passphrase, comment, getFilesDir()))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::generateSshKeySuccess, this::generateSshKeyError);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void generateSshKeyError(Throwable throwable) {
        new AlertDialog.Builder(SshKeyGen.this)
                .setTitle("Error while trying to generate the ssh-key")
                .setMessage(SshKeyGen.this.getResources().getString(R.string.ssh_key_error_dialog_text) + throwable.getMessage())
                .setPositiveButton(SshKeyGen.this.getResources().getString(R.string.dialog_ok), (dialogInterface, i) -> {
                    // pass
                }).show();
    }

    private void generateSshKeySuccess(Boolean success) {
        progressDialog.dismiss();
        Toast.makeText(getApplicationContext(), "SSH-key generated", Toast.LENGTH_LONG).show();
        showSshKeyFragment.show(getFragmentManager(), "public_key");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SshKeyGen.this.getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("use_generated_key", true);
        editor.apply();
    }

    @NonNull
    private Callable<Boolean> generateSshKeyCallable(String lengthStr, String passphrase, String comment, File filesDir) {
        return () -> {
            int length = Integer.parseInt(lengthStr);

            KeyPair kp = KeyPair.genKeyPair(jSch, KeyPair.RSA, length);

            File file = new File(filesDir + "/.ssh_key");
            FileOutputStream out = new FileOutputStream(file, false);
            if (passphrase.length() > 0) {
                kp.writePrivateKey(out, passphrase.getBytes());
            } else {
                kp.writePrivateKey(out);
            }

            file = new File(filesDir + "/.ssh_key.pub");
            out = new FileOutputStream(file, false);
            kp.writePublicKey(out, comment);
            return true;
        };
    }

    @Override
    public AndroidInjector<Fragment> fragmentInjector() {
        return fragmentDispatchingAndroidInjector;
    }
}
