package com.zeapo.pwdstore;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
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
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final Activity callingActivity = getActivity();
        LayoutInflater inflater = callingActivity.getLayoutInflater();
        @SuppressLint("InflateParams") final View view = inflater.inflate(R.layout.fragment_pwgen, null);
        Typeface monoTypeface = Typeface.createFromAsset(callingActivity.getAssets(), "fonts/sourcecodepro.ttf");

        builder.setView(view);

        SharedPreferences prefs
                = getActivity().getApplicationContext().getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE);

        CheckBox checkBox = view.findViewById(R.id.numerals);
        checkBox.setChecked(!prefs.getBoolean("0", false));

        checkBox = view.findViewById(R.id.symbols);
        checkBox.setChecked(prefs.getBoolean("y", false));

        checkBox = view.findViewById(R.id.uppercase);
        checkBox.setChecked(!prefs.getBoolean("A", false));

        checkBox = view.findViewById(R.id.ambiguous);
        checkBox.setChecked(!prefs.getBoolean("B", false));

        checkBox = view.findViewById(R.id.pronounceable);
        checkBox.setChecked(!prefs.getBoolean("s", true));

        TextView textView = view.findViewById(R.id.lengthNumber);
        textView.setText(Integer.toString(prefs.getInt("length", 20)));

        ((TextView) view.findViewById(R.id.passwordText)).setTypeface(monoTypeface);

        builder.setPositiveButton(getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                EditText edit = callingActivity.findViewById(R.id.crypto_password_edit);
                TextView generate = view.findViewById(R.id.passwordText);
                edit.setText(generate.getText());
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
                TextView textView = view.findViewById(R.id.passwordText);
                textView.setText(PasswordGenerator.INSTANCE.generate(getActivity().getApplicationContext()).get(0));

                Button b = ad.getButton(AlertDialog.BUTTON_NEUTRAL);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setPreferences();
                        TextView textView = view.findViewById(R.id.passwordText);
                        textView.setText(PasswordGenerator.INSTANCE.generate(callingActivity.getApplicationContext()).get(0));
                    }
                });
            }
        });
        return ad;
    }

    private void setPreferences () {
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
        EditText editText = getDialog().findViewById(R.id.lengthNumber);
        try {
            int length = Integer.valueOf(editText.getText().toString());
            PasswordGenerator.INSTANCE.setPrefs(getActivity().getApplicationContext(), preferences, length);
        } catch(NumberFormatException e) {
            PasswordGenerator.INSTANCE.setPrefs(getActivity().getApplicationContext(), preferences);
        }
    }
}

