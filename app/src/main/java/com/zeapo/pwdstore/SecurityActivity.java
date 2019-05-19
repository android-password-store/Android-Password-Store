package com.zeapo.pwdstore;

import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.hardware.fingerprint.FingerprintManagerCompat;

import com.mattprecious.swirl.SwirlView;
import com.zeapo.pwdstore.utils.FingerprintHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;


public class SecurityActivity extends AppCompatActivity {

    public static final String PASSWORD_INTENT = "password";
    private static final String KEY_NAME = "fp_key";
    private static final String SECRET_MESSAGE = "secret_message";

    private View mPasswordWrong;
    private FingerprintManagerCompat mFingerprintManagerCompat;
    private Cipher mCipher;
    private FingerprintHelper mFingerprintUiHelper;
    private FingerprintManagerCompat.CryptoObject mCryptoObject;

    public static String decodeString(String text) {
        return new String(Base64.decode(text, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    public static String encodeString(String text) {
        return Base64.encodeToString(text.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security);

        final String password = decodeString(getIntent().getStringExtra(PASSWORD_INTENT));
        AppCompatEditText editText = findViewById(R.id.edittext);
        mPasswordWrong = findViewById(R.id.password_wrong);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.toString().equals(password)) {
                    mPasswordWrong.setVisibility(View.GONE);
                    setResult(1);
                    finish();
                } else {
                    mPasswordWrong.setVisibility(editable.toString().isEmpty() ? View.GONE : View.VISIBLE);
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext()).getBoolean("fingerprint_authentication", false)) {
            mFingerprintManagerCompat = FingerprintManagerCompat.from(this);
            if (mFingerprintManagerCompat.isHardwareDetected()
                    && mFingerprintManagerCompat.hasEnrolledFingerprints()
                    && getSystemService(KeyguardManager.class).isDeviceSecure()) {
                loadFingerprint();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void loadFingerprint() {
        try {
            // Obtain a reference to the Keystore
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");

            //Generate the key
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            //Obtain a cipher instance and configure it with the properties required for fingerprint authentication
            mCipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_CBC + "/"
                    + KeyProperties.ENCRYPTION_PADDING_PKCS7);

            //Initialize an empty KeyStore
            keyStore.load(null);

            //Specify the operation(s) this key can be used for
            keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    //Configure this key so that the user has to confirm their identity with a fingerprint each time they want to use it
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());

            //Generate the key
            keyGenerator.generateKey();

            SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME, null);
            mCipher.init(Cipher.ENCRYPT_MODE, key);
        } catch (KeyStoreException | NoSuchProviderException | NoSuchAlgorithmException
                | NoSuchPaddingException | UnrecoverableKeyException | InvalidKeyException
                | CertificateException | InvalidAlgorithmParameterException | IOException e) {
            return;
        }

        mCryptoObject = new FingerprintManagerCompat.CryptoObject(mCipher);
        FrameLayout fingerprintParent = findViewById(R.id.fingerprint_parent);
        final SwirlView swirlView = new SwirlView(new ContextThemeWrapper(this, R.style.Swirl));
        swirlView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        fingerprintParent.addView(swirlView);
        fingerprintParent.setVisibility(View.VISIBLE);

        mFingerprintUiHelper = new FingerprintHelper.FingerprintUiHelperBuilder(
                mFingerprintManagerCompat).build(swirlView,
                new FingerprintHelper.Callback() {
                    @Override
                    public void onAuthenticated() {
                        try {
                            mCipher.doFinal(SECRET_MESSAGE.getBytes());
                            mPasswordWrong.setVisibility(View.GONE);
                            setResult(1);
                            finish();
                        } catch (IllegalBlockSizeException | BadPaddingException e) {
                            e.printStackTrace();
                            swirlView.setState(SwirlView.State.ERROR);
                        }
                    }

                    @Override
                    public void onError() {
                    }
                });
        mFingerprintUiHelper.startListening(mCryptoObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mFingerprintUiHelper != null) {
            mFingerprintUiHelper.startListening(mCryptoObject);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAffinity();
        finish();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mFingerprintUiHelper != null) {
            mFingerprintUiHelper.stopListening();
        }
    }
}