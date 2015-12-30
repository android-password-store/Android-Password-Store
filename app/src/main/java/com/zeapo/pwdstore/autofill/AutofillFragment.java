package com.zeapo.pwdstore.autofill;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;

public class AutofillFragment extends DialogFragment {
    private static final int MATCH_WITH = 777;
    ArrayAdapter<String> adapter;

    public AutofillFragment() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // this fragment is only created from the settings page (AutofillPreferenceActivity)
        // need to interact with the recyclerAdapter which is a member of activity
        final AutofillPreferenceActivity callingActivity = (AutofillPreferenceActivity) getActivity();
        LayoutInflater inflater = callingActivity.getLayoutInflater();

        final View view = inflater.inflate(R.layout.fragment_autofill, null);

        builder.setView(view);

        String packageName = getArguments().getString("packageName", "");
        String appName = getArguments().getString("appName", "");

        final boolean isWebsite = appName.equals(packageName);

        // set the dialog icon and title or webName editText
        String iconPackageName;
        if (!isWebsite) {
            iconPackageName = packageName;
            builder.setTitle(appName);
            view.findViewById(R.id.webName).setVisibility(View.GONE);
        } else {
            iconPackageName = "com.android.browser";
            builder.setTitle("Website");
            ((EditText) view.findViewById(R.id.webName)).setText(packageName);
        }
        try {
            builder.setIcon(callingActivity.getPackageManager().getApplicationIcon(iconPackageName));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // set up the listview now for items added by button/from preferences
        adapter = new ArrayAdapter<String>(getActivity().getApplicationContext()
                , android.R.layout.simple_list_item_1, android.R.id.text1) {
            // set text color to black because default is white...
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getView(position, convertView, parent);
                textView.setTextColor(ContextCompat.getColor(getContext(), R.color.grey_black_1000));
                return textView;
            }
        };
        ((ListView) view.findViewById(R.id.matched)).setAdapter(adapter);
        // delete items by clicking them
        ((ListView) view.findViewById(R.id.matched)).setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        adapter.remove(adapter.getItem(position));
                    }
                });

        // set the existing preference, if any
        SharedPreferences prefs;
        String preference;
        if (!isWebsite) {
            prefs = getActivity().getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
        } else {
            prefs = getActivity().getApplicationContext().getSharedPreferences("autofill_web", Context.MODE_PRIVATE);
        }
        preference = prefs.getString(packageName, "");
        switch (preference) {
            case "":
                ((RadioButton) view.findViewById(R.id.use_default)).toggle();
                break;
            case "/first":
                ((RadioButton) view.findViewById(R.id.first)).toggle();
                break;
            case "/never":
                ((RadioButton) view.findViewById(R.id.never)).toggle();
                break;
            default:
                ((RadioButton) view.findViewById(R.id.match)).toggle();
                // trim to remove the last blank element
                adapter.addAll(preference.trim().split("\n"));
        }

        // add items with the + button
        View.OnClickListener matchPassword = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((RadioButton) view.findViewById(R.id.match)).toggle();
                Intent intent = new Intent(getActivity(), PasswordStore.class);
                intent.putExtra("matchWith", true);
                startActivityForResult(intent, MATCH_WITH);
            }
        };
        view.findViewById(R.id.matchButton).setOnClickListener(matchPassword);

        // write to preferences when OK clicked
        final SharedPreferences.Editor editor = prefs.edit();
        builder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String key;
                String packageName = getArguments().getString("packageName", "");

                if (!isWebsite) {
                    key = packageName;
                } else {
                    key = ((EditText) view.findViewById(R.id.webName)).getText().toString();
                    // if key.equals("") show error

                    // if new packageName/appName/website name/website title/key
                    // is different than old, remove the old one. Basically,
                    // "edit" the old one.
                    if (!key.equals(packageName) && !packageName.equals("")) {
                        editor.remove(packageName);
                        if (callingActivity.recyclerAdapter != null) {
                            if (callingActivity.recyclerAdapter.getPosition(packageName) != -1) {
                                callingActivity.recyclerAdapter.removeWebsite(packageName);
                            }
                        }
                    }
                }

                RadioGroup radioGroup = (RadioGroup) view.findViewById(R.id.autofill_radiogroup);
                switch (radioGroup.getCheckedRadioButtonId()) {
                    case R.id.use_default:
                        editor.remove(key);
                        break;
                    case R.id.first:
                        editor.putString(key, "/first");
                        break;
                    case R.id.never:
                        editor.putString(key, "/never");
                        break;
                    default:
                        StringBuilder paths = new StringBuilder();
                        for (int i = 0; i < adapter.getCount(); i++) {
                            paths.append(adapter.getItem(i));
                            if (i != adapter.getCount()) {
                                paths.append("\n");
                            }
                        }
                        editor.putString(key, paths.toString());
                }
                editor.apply();

                // if recyclerAdapter has not loaded yet, there is no need to notify
                if (callingActivity.recyclerAdapter != null) {
                    int position;
                    if (!isWebsite) {
                        String appName = getArguments().getString("appName", "");
                        position = callingActivity.recyclerAdapter.getPosition(appName);
                        callingActivity.recyclerAdapter.notifyItemChanged(position);
                    } else {
                        String appName = ((EditText) view.findViewById(R.id.webName)).getText().toString();
                        position = callingActivity.recyclerAdapter.getPosition(appName);
                        switch (radioGroup.getCheckedRadioButtonId()) {
                            // remove if existed, else do nothing
                            case R.id.use_default:
                                if (position != -1) {
                                    callingActivity.recyclerAdapter.removeWebsite(appName);
                                }
                                break;
                            // change if existed, else add
                            default:
                                if (position != -1) {
                                    callingActivity.recyclerAdapter.notifyItemChanged(position);
                                } else {
                                    callingActivity.recyclerAdapter.addWebsite(appName);
                                }
                        }
                    }
                }
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        return builder.create();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            adapter.add(data.getStringExtra("path"));
        }
    }
}
