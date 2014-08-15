package com.zeapo.pwdstore;

import android.app.Activity;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.zeapo.pwdstore.utils.PasswordAdapter;
import com.zeapo.pwdstore.utils.PasswordItem;
import com.zeapo.pwdstore.utils.PasswordRepository;

import java.io.File;
import java.util.List;

/**
 * A fragment representing a list of Items.
 * <p />
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p />
 */
public class PasswordFragment extends Fragment implements ExpandableListView.OnGroupClickListener {

    private OnFragmentInteractionListener mListener;

    /**
     * The fragment's ListView/GridView.
     */
    private ExpandableListView mListView;

    /**
     * The Adapter which will be used to populate the ListView/GridView with
     * Views.
     */
    private PasswordAdapter mAdapter;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public PasswordFragment() {   }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getArguments().getString("Path");
        mAdapter = new PasswordAdapter(getActivity(), PasswordRepository.getPasswords(new File(path)));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_password, container, false);

        // Set the adapter
        mListView = (ExpandableListView) view.findViewById(R.id.pass_list);
        mListView.setAdapter((android.widget.ExpandableListAdapter) mAdapter);

        // Set OnItemClickListener so we can be notified on item clicks
        mListView.setOnGroupClickListener(this);
        mListView.setSelectionFromTop(getArguments().getInt("Position"), 0);

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onPause() {
        super.onPause();
        mListener.savePosition(mListView.getFirstVisiblePosition());

    }

    @Override
    public boolean onGroupClick(ExpandableListView expandableListView, View view, int i, long l) {
        if( ((PasswordItem) mAdapter.getGroup(i)).getType() == PasswordItem.TYPE_CATEGORY ){
            if (null != mListener) {
    //            Notify the active callbacks interface (the activity, if the
    //            fragment is attached to one) that an item has been selected.
                mListener.onFragmentInteraction(mAdapter.getItem(i));
            }
        } else {
            if (expandableListView.isGroupExpanded(i)) {
                expandableListView.collapseGroup(i);
            } else {
                expandableListView.expandGroup(i);
            }
        }

        return true;
    }

    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(PasswordItem item);
        public void savePosition(Integer position);
    }

    public void updateAdapter() {
        mAdapter.clear();
        mAdapter.addAll(PasswordRepository.getPasswords(new File(getArguments().getString("Path"))));
    }

}
