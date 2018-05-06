package com.zeapo.pwdstore.crypto.password.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.crypto.password.RandomPasswordGenerator;

import java.util.ArrayList;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import dagger.android.DaggerDialogFragment;


/**
 * A placeholder fragment containing a simple view.
 */
public class PwgenDialogFragment extends DaggerDialogFragment {

    @BindView(R.id.numerals)
    CheckBox checkBoxNumerals;
    @BindView(R.id.symbols)
    CheckBox checkBoxSymbols;
    @BindView(R.id.uppercase)
    CheckBox checkBoxUppercase;
    @BindView(R.id.ambiguous)
    CheckBox checkBoxAmbiguous;
    @BindView(R.id.pronounceable)
    CheckBox checkBoxPronounceable;
    @BindView(R.id.lengthNumber)
    TextView textViewLength;
    @BindView(R.id.passwordText)
    TextView passwordText;

    @Inject
    RandomPasswordGenerator randomPasswordGenerator;

    @SuppressLint("SetTextI18n")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final Activity callingActivity = getActivity();
        LayoutInflater inflater = callingActivity.getLayoutInflater();
        @SuppressLint("InflateParams") final View view = inflater.inflate(R.layout.fragment_pwgen, null);
        ButterKnife.bind(this, view);
        Typeface monoTypeface = Typeface.createFromAsset(callingActivity.getAssets(), "fonts/sourcecodepro.ttf");

        builder.setView(view);

        SharedPreferences prefs
                = getActivity().getApplicationContext().getSharedPreferences("pwgen", Context.MODE_PRIVATE);

        checkBoxNumerals.setChecked(!prefs.getBoolean("0", false));
        checkBoxSymbols.setChecked(prefs.getBoolean("y", false));
        checkBoxUppercase.setChecked(!prefs.getBoolean("A", false));
        checkBoxAmbiguous.setChecked(!prefs.getBoolean("B", false));
        checkBoxPronounceable.setChecked(!prefs.getBoolean("s", true));
        textViewLength.setText(Integer.toString(prefs.getInt("length", 20)));
        passwordText.setTypeface(monoTypeface);

        builder.setPositiveButton(getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                EditText edit = (EditText) callingActivity.findViewById(R.id.crypto_password_edit);
                edit.setText(textViewLength.getText());
            }
        });

        builder.setNegativeButton(getResources().getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        builder.setNeutralButton(getResources().getString(R.string.pwgen_generate), null);

        final AlertDialog ad = builder.setTitle("Generate Password").create();
        ad.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                setPreferences();
                textViewLength.setText(randomPasswordGenerator.generate(getActivity().getApplicationContext()).get(0));

                Button b = ad.getButton(AlertDialog.BUTTON_NEUTRAL);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setPreferences();
                        textViewLength.setText(randomPasswordGenerator.generate(callingActivity.getApplicationContext()).get(0));
                    }
                });
            }
        });
        return ad;
    }

    private void setPreferences() {
        ArrayList<String> preferences = new ArrayList<>();
        if (!checkBoxNumerals.isChecked()) {
            preferences.add("0");
        }
        if (checkBoxSymbols.isChecked()) {
            preferences.add("y");
        }
        if (!checkBoxUppercase.isChecked()) {
            preferences.add("A");
        }
        if (!checkBoxAmbiguous.isChecked()) {
            preferences.add("B");
        }
        if (!checkBoxPronounceable.isChecked()) {
            preferences.add("s");
        }
        try {
            int length = Integer.valueOf(textViewLength.getText().toString());
            randomPasswordGenerator.setPrefs(getActivity().getApplicationContext(), preferences, length);
        } catch (NumberFormatException e) {
            randomPasswordGenerator.setPrefs(getActivity().getApplicationContext(), preferences);
        }
    }
}

