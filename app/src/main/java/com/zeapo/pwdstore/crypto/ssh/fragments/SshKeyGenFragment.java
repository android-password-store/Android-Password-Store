package com.zeapo.pwdstore.crypto.ssh.fragments;

import android.app.Fragment;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;

import com.zeapo.pwdstore.R;

import butterknife.BindView;
import butterknife.ButterKnife;

// SSH key generation UI
public class SshKeyGenFragment extends Fragment {
    @BindView(R.id.length)
    Spinner spinner;
    @BindView(R.id.show_passphrase)
    CheckBox checkbox;
    @BindView(R.id.passphrase)
    EditText editText;

    public SshKeyGenFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.fragment_ssh_keygen, container, false);
        ButterKnife.bind(this, v);
        Typeface monoTypeface = Typeface.createFromAsset(getActivity().getAssets(), "fonts/sourcecodepro.ttf");

        Integer[] lengths = new Integer[]{2048, 4096};
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_spinner_dropdown_item, lengths);

        spinner.setAdapter(adapter);
        editText.setTypeface(monoTypeface);
        checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                int selection = editText.getSelectionEnd();
                if (isChecked) {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                } else {
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                editText.setSelection(selection);
            }
        });

        return v;
    }
}
