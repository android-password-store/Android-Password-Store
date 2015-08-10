package com.zeapo.pwdstore.autofill;

import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;

public class AutofillFragment extends DialogFragment {

    public AutofillFragment() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // this fragment is only created from the settings page (AutofillActivity)
        // need to interact with the recyclerAdapter which is a member of activity
        final AutofillActivity callingActivity = (AutofillActivity) getActivity();
        LayoutInflater inflater = callingActivity.getLayoutInflater();
        final View view = inflater.inflate(R.layout.fragment_autofill, null);

        builder.setView(view);

        final String packageName = getArguments().getString("packageName");
        String appName = getArguments().getString("appName");

        builder.setTitle(appName);
        SharedPreferences prefs
                = getActivity().getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
        String preference = prefs.getString(packageName, "first");
        switch (preference) {
            case "first":
                ((RadioButton) view.findViewById(R.id.first)).toggle();
                break;
            case "never":
                ((RadioButton) view.findViewById(R.id.never)).toggle();
                break;
            default:
                ((RadioButton) view.findViewById(R.id.match)).toggle();
                ((EditText) view.findViewById(R.id.matched)).setText(preference);
        }

        View.OnClickListener matchPassword = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO figure out UI for this
                Intent intent = new Intent(getActivity(), PasswordStore.class);
                startActivity(intent);
            }
        };
        view.findViewById(R.id.match).setOnClickListener(matchPassword);
        view.findViewById(R.id.matched).setOnClickListener(matchPassword);

        final SharedPreferences.Editor editor = prefs.edit();
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                RadioGroup radioGroup = (RadioGroup) view.findViewById(R.id.autofill_radiogroup);
                switch (radioGroup.getCheckedRadioButtonId()) {
                    case R.id.first:
                        editor.putString(packageName, "first");
                        break;
                    case R.id.never:
                        editor.putString(packageName, "never");
                        break;
                    default:
                        EditText matched = (EditText) view.findViewById(R.id.matched);
                        String path = matched.getText().toString();
                        editor.putString(packageName, path);
                }
                editor.apply();
                int position = getArguments().getInt("position");
                if (position == -1) {
                    callingActivity.recyclerAdapter.add(packageName);
                } else {
                    callingActivity.recyclerAdapter.notifyItemChanged(position);
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        return builder.create();
    }
}
