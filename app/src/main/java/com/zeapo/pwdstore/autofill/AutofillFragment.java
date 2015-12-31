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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;

public class AutofillFragment extends DialogFragment {
    private static final int MATCH_WITH = 777;
    private ArrayAdapter<String> adapter;
    private boolean isWeb;

    public AutofillFragment() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // this fragment is only created from the settings page (AutofillPreferenceActivity)
        // need to interact with the recyclerAdapter which is a member of activity
        AutofillPreferenceActivity callingActivity = (AutofillPreferenceActivity) getActivity();
        LayoutInflater inflater = callingActivity.getLayoutInflater();

        final View view = inflater.inflate(R.layout.fragment_autofill, null);

        builder.setView(view);

        final String packageName = getArguments().getString("packageName");
        final String appName = getArguments().getString("appName");
        isWeb = getArguments().getBoolean("isWeb");

        // set the dialog icon and title or webName editText
        String iconPackageName;
        if (!isWeb) {
            iconPackageName = packageName;
            builder.setTitle(appName);
            view.findViewById(R.id.webURL).setVisibility(View.GONE);
        } else {
            iconPackageName = "com.android.browser";
            builder.setTitle("Website");
            ((EditText) view.findViewById(R.id.webURL)).setText(packageName);
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
        if (!isWeb) {
            prefs = getActivity().getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
        } else {
            prefs = getActivity().getApplicationContext().getSharedPreferences("autofill_web", Context.MODE_PRIVATE);
        }
        String preference = prefs.getString(packageName, "");
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
        builder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);
        final SharedPreferences.Editor editor = prefs.edit();
        if (isWeb) {
            builder.setNeutralButton(R.string.autofill_apps_delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (((AutofillPreferenceActivity) getActivity()).recyclerAdapter != null
                            && packageName != null && !packageName.equals("")) {
                        editor.remove(packageName);
                        ((AutofillPreferenceActivity) getActivity()).recyclerAdapter.removeWebsite(packageName);
                        editor.apply();
                    }
                }
            });
        }
        return builder.create();
    }

    // need to the onClick here for buttons to dismiss dialog only when wanted
    @Override
    public void onStart() {
        super.onStart();
        AlertDialog ad = (AlertDialog) getDialog();
        if(ad != null) {
            Button positiveButton = ad.getButton(Dialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AutofillPreferenceActivity callingActivity = (AutofillPreferenceActivity) getActivity();
                    Dialog dialog = getDialog();

                    SharedPreferences prefs;
                    if (!isWeb) {
                        prefs = getActivity().getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
                    } else {
                        prefs = getActivity().getApplicationContext().getSharedPreferences("autofill_web", Context.MODE_PRIVATE);
                    }
                    SharedPreferences.Editor editor = prefs.edit();

                    String packageName = getArguments().getString("packageName", "");
                    if (isWeb) {
                        packageName = ((EditText) dialog.findViewById(R.id.webURL)).getText().toString();

                        // handle some errors
                        EditText webURL = (EditText) dialog.findViewById(R.id.webURL);
                        if (packageName.equals("")) {
                            webURL.setError("URL cannot be blank");
                            return;
                        }
                        String oldPackageName = getArguments().getString("packageName", "");
                        int position = callingActivity.recyclerAdapter.getPosition(packageName);
                        if (!oldPackageName.equals(packageName) && position != -1) {
                            webURL.setError("URL already exists");
                            return;
                        }
                    }
                    RadioGroup radioGroup = (RadioGroup) dialog.findViewById(R.id.autofill_radiogroup);
                    switch (radioGroup.getCheckedRadioButtonId()) {
                        case R.id.use_default:
                            if (!isWeb) {
                                editor.remove(packageName);
                            } else {
                                editor.putString(packageName, "");
                            }
                            break;
                        case R.id.first:
                            editor.putString(packageName, "/first");
                            break;
                        case R.id.never:
                            editor.putString(packageName, "/never");
                            break;
                        default:
                            StringBuilder paths = new StringBuilder();
                            for (int i = 0; i < adapter.getCount(); i++) {
                                paths.append(adapter.getItem(i));
                                if (i != adapter.getCount()) {
                                    paths.append("\n");
                                }
                            }
                            editor.putString(packageName, paths.toString());
                    }
                    editor.apply();

                    // if recyclerAdapter has not loaded yet, there is no need to notify
                    if (callingActivity.recyclerAdapter != null) {
                        int position;
                        if (!isWeb) {
                            String appName = getArguments().getString("appName", "");
                            position = callingActivity.recyclerAdapter.getPosition(appName);
                            callingActivity.recyclerAdapter.notifyItemChanged(position);
                        } else {
                            position = callingActivity.recyclerAdapter.getPosition(packageName);
                            String oldPackageName = getArguments().getString("packageName", "");
                            if (oldPackageName.equals(packageName)) {
                                callingActivity.recyclerAdapter.notifyItemChanged(position);
                            } else if (oldPackageName.equals("")){
                                callingActivity.recyclerAdapter.addWebsite(packageName);
                            } else {
                                editor.remove(oldPackageName);
                                callingActivity.recyclerAdapter.updateWebsite(oldPackageName, packageName);
                            }
                        }
                    }

                    dismiss();
                }
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            adapter.add(data.getStringExtra("path"));
        }
    }
}
