package com.zeapo.pwdstore;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zeapo.pwdstore.pwgen.PasswordGenerator;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;


/**
 * A placeholder fragment containing a simple view.
 */
public class PasswordGeneratorDialogFragment extends DialogFragment {

    public PasswordGeneratorDialogFragment() {
    }

    @NotNull
    @SuppressLint("SetTextI18n")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        final Activity callingActivity = requireActivity();
        LayoutInflater inflater = callingActivity.getLayoutInflater();
        @SuppressLint("InflateParams") final View view = inflater.inflate(R.layout.fragment_pwgen, null);
        Typeface monoTypeface = Typeface.createFromAsset(callingActivity.getAssets(), "fonts/sourcecodepro.ttf");

        builder.setView(view);

        SharedPreferences prefs
                = requireActivity().getApplicationContext().getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE);

        CheckBox checkBox = view.findViewById(R.id.numerals);
        checkBox.setChecked(!prefs.getBoolean("0", false));

        checkBox = view.findViewById(R.id.symbols);
        checkBox.setChecked(prefs.getBoolean("y", false));

        checkBox = view.findViewById(R.id.uppercase);
        checkBox.setChecked(!prefs.getBoolean("A", false));

        checkBox = view.findViewById(R.id.lowercase);
        checkBox.setChecked(!prefs.getBoolean("L", false));

        checkBox = view.findViewById(R.id.ambiguous);
        checkBox.setChecked(!prefs.getBoolean("B", false));

        checkBox = view.findViewById(R.id.pronounceable);
        checkBox.setChecked(!prefs.getBoolean("s", true));

        AppCompatEditText textView = view.findViewById(R.id.lengthNumber);
        textView.setText(Integer.toString(prefs.getInt("length", 20)));

        AppCompatTextView passwordText = view.findViewById(R.id.passwordText);
        passwordText.setTypeface(monoTypeface);

        builder.setPositiveButton(getResources().getString(R.string.dialog_ok), (dialog, which) -> {
            EditText edit = callingActivity.findViewById(R.id.crypto_password_edit);
            edit.setText(passwordText.getText());
        });

        builder.setNegativeButton(getResources().getString(R.string.dialog_cancel), (dialog, which) -> {

        });

        builder.setNeutralButton(getResources().getString(R.string.pwgen_generate), null);

        final AlertDialog ad = builder.setTitle(this.getResources().getString(R.string.pwgen_title)).create();
        ad.setOnShowListener(dialog -> {
            setPreferences();
            try {
                passwordText.setText(PasswordGenerator.generate(requireActivity().getApplicationContext()).get(0));
            } catch (PasswordGenerator.PasswordGeneratorExeption e) {
                Toast.makeText(requireActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                passwordText.setText("");
            }

            Button b = ad.getButton(AlertDialog.BUTTON_NEUTRAL);
            b.setOnClickListener(v -> {
                setPreferences();
                try {
                    passwordText.setText(PasswordGenerator.generate(callingActivity.getApplicationContext()).get(0));
                } catch (PasswordGenerator.PasswordGeneratorExeption e) {
                    Toast.makeText(requireActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    passwordText.setText("");
                }
            });
        });
        return ad;
    }

    private void setPreferences() {
        ArrayList<String> preferences = new ArrayList<>();
        if (!((CheckBox) getDialog().findViewById(R.id.numerals)).isChecked()) {
            preferences.add("0");
        }
        if (((CheckBox) getDialog().findViewById(R.id.symbols)).isChecked()) {
            preferences.add("y");
        }
        if (!((CheckBox) getDialog().findViewById(R.id.uppercase)).isChecked()) {
            preferences.add("A");
        }
        if (!((CheckBox) getDialog().findViewById(R.id.ambiguous)).isChecked()) {
            preferences.add("B");
        }
        if (!((CheckBox) getDialog().findViewById(R.id.pronounceable)).isChecked()) {
            preferences.add("s");
        }
        if (!((CheckBox) getDialog().findViewById(R.id.lowercase)).isChecked()) {
            preferences.add("L");
        }

        EditText editText = getDialog().findViewById(R.id.lengthNumber);
        try {
            int length = Integer.valueOf(editText.getText().toString());
            PasswordGenerator.setPrefs(requireActivity().getApplicationContext(), preferences, length);
        } catch (NumberFormatException e) {
            PasswordGenerator.setPrefs(requireActivity().getApplicationContext(), preferences);
        }
    }
}

