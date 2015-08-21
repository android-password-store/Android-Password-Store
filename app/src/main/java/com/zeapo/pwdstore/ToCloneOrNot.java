package com.zeapo.pwdstore;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;


public class ToCloneOrNot extends Fragment {

    public ToCloneOrNot() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_to_clone_or_not, container, false);

        Switch use_git = (Switch) view.findViewById(R.id.enable_git_switch);
        final Switch use_remote = (Switch) view.findViewById(R.id.clone_remote_switch);
        final boolean use_remote_initial = use_remote.isChecked();

        use_git.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // use_git is incompatible with use_remote
                use_remote.setEnabled(isChecked);
                // if disabled set it to false, otherwise set it back to its initial value
                use_remote.setChecked(isChecked && use_remote_initial);
            }
        });
        return view;
    }

}
