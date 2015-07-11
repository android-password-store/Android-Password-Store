package com.zeapo.pwdstore;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.zeapo.pwdstore.pwgen.pwgen;

import java.util.ArrayList;


/**
 * A placeholder fragment containing a simple view.
 */
public class pwgenDialogFragment extends DialogFragment {

    public pwgenDialogFragment() {
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.fragment_pwgen, null);
        builder.setView(view);

        SharedPreferences prefs
                = getActivity().getApplicationContext().getSharedPreferences("pwgen", Context.MODE_PRIVATE);

        CheckBox checkBox = (CheckBox) view.findViewById(R.id.numerals);
        checkBox.setChecked(!prefs.getBoolean("0", false));

        checkBox = (CheckBox) view.findViewById(R.id.symbols);
        checkBox.setChecked(prefs.getBoolean("y", false));

        checkBox = (CheckBox) view.findViewById(R.id.uppercase);
        checkBox.setChecked(!prefs.getBoolean("A", false));

        checkBox = (CheckBox) view.findViewById(R.id.ambiguous);
        checkBox.setChecked(!prefs.getBoolean("B", false));

        checkBox = (CheckBox) view.findViewById(R.id.pronounceable);
        checkBox.setChecked(!prefs.getBoolean("s", true));

        TextView textView = (TextView) view.findViewById(R.id.lengthNumber);
        textView.setText(Integer.toString(prefs.getInt("length", 20)));

        textView = (TextView) view.findViewById(R.id.passwordText);
        textView.setText(pwgen.generate(getActivity().getApplicationContext()).get(0));

        builder.setPositiveButton(getResources().getString(R.string.dialog_ok), (dialog, which) -> {
            setPreferences();
            TextView edit = (TextView) getActivity().findViewById(R.id.crypto_password_edit);
            TextView generate = (TextView) getDialog().findViewById(R.id.passwordText);
            edit.append(generate.getText());
        });

        builder.setNegativeButton(getResources().getString(R.string.dialog_cancel), (dialog, which) -> {

        });

        builder.setNeutralButton(getResources().getString(R.string.pwgen_generate), null);

        final AlertDialog ad = builder.setTitle("Generate Password").create();
        ad.setOnShowListener(dialog -> {
            Button b = ad.getButton(AlertDialog.BUTTON_NEUTRAL);
            b.setOnClickListener(v -> {
                setPreferences();
                TextView textView1 = (TextView) getDialog().findViewById(R.id.passwordText);
                textView1.setText(pwgen.generate(getActivity().getApplicationContext()).get(0));
            });
        });
        return ad;
    }

    private boolean setPreferences () {
        ArrayList<String> preferences = new ArrayList<>();
        if (!((CheckBox)getDialog().findViewById(R.id.numerals)).isChecked()) {
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
        TextView textView = (TextView) getDialog().findViewById(R.id.lengthNumber);
        try {
            int length = Integer.valueOf(textView.getText().toString());
            return pwgen.setPrefs(getActivity().getApplicationContext(), preferences, length);
        } catch(NumberFormatException e) {
            return pwgen.setPrefs(getActivity().getApplicationContext(), preferences);
        }
    }
}

