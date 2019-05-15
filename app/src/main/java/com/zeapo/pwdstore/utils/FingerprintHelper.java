package com.zeapo.pwdstore.utils;


import androidx.core.hardware.fingerprint.FingerprintManagerCompat;
import androidx.core.os.CancellationSignal;

import com.mattprecious.swirl.SwirlView;


public class FingerprintHelper extends FingerprintManagerCompat.AuthenticationCallback {

    private boolean mListening;
    private final FingerprintManagerCompat mFingerprintManagerCompat;
    private final SwirlView mSwirlView;
    private final Callback mCallback;
    private CancellationSignal mCancellationSignal;

    private boolean mSelfCancelled;

    public static class FingerprintUiHelperBuilder {
        private final FingerprintManagerCompat mFingerprintManagerCompat;

        public FingerprintUiHelperBuilder(FingerprintManagerCompat fingerprintManagerCompat) {
            mFingerprintManagerCompat = fingerprintManagerCompat;
        }

        public FingerprintHelper build(SwirlView swirlView, Callback callback) {
            return new FingerprintHelper(mFingerprintManagerCompat, swirlView, callback);
        }
    }

    private FingerprintHelper(FingerprintManagerCompat fingerprintManagerCompat, SwirlView swirlView,
                              Callback callback) {
        mFingerprintManagerCompat = fingerprintManagerCompat;
        mSwirlView = swirlView;
        mCallback = callback;
    }

    public void startListening(FingerprintManagerCompat.CryptoObject cryptoObject) {
        if (!mListening) {
            mListening = true;
            mCancellationSignal = new CancellationSignal();
            mSelfCancelled = false;
            mFingerprintManagerCompat
                    .authenticate(cryptoObject, 0, mCancellationSignal, this, null);
            mSwirlView.setState(SwirlView.State.ON);
        }
    }

    public void stopListening() {
        if (mCancellationSignal != null) {
            mSelfCancelled = true;
            mCancellationSignal.cancel();
            mCancellationSignal = null;
            mListening = false;
        }
    }

    @Override
    public void onAuthenticationError(int errMsgId, CharSequence errString) {
        if (!mSelfCancelled) {
            showError();
            mCallback.onError();
        }
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        showError();
    }

    @Override
    public void onAuthenticationFailed() {
        showError();
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
        mSwirlView.setState(SwirlView.State.OFF);
        mSwirlView.postDelayed(mCallback::onAuthenticated, 100);
    }

    private void showError() {
        mSwirlView.setState(SwirlView.State.ERROR);
    }

    public interface Callback {
        void onAuthenticated();

        void onError();
    }
}