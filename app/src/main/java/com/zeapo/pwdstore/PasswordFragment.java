package com.zeapo.pwdstore;

import android.app.Activity;
import android.os.Bundle;
import android.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.zeapo.pwdstore.utils.PasswordItem;
import com.zeapo.pwdstore.utils.PasswordRecyclerAdapter;
import com.zeapo.pwdstore.utils.PasswordRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.Stack;

/**
 * A fragment representing a list of Items.
 * <p />
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p />
 */
public class PasswordFragment extends Fragment{

    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(PasswordItem item);
    }

    // store the pass files list in a stack
    private Stack<ArrayList<PasswordItem>> passListStack;
    private Stack<Integer> scrollPosition;
    private PasswordRecyclerAdapter recyclerAdapter;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager mLayoutManager;
    private OnFragmentInteractionListener mListener;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public PasswordFragment() {   }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getArguments().getString("Path");

        passListStack = new Stack<ArrayList<PasswordItem>>();
        scrollPosition = new Stack<Integer>();
        recyclerAdapter = new PasswordRecyclerAdapter((PasswordStore) getActivity(), mListener, PasswordRepository.getPasswords(new File(path)));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.password_recycler_view, container, false);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(getActivity());

        recyclerView = (RecyclerView) view.findViewById(R.id.pass_recycler);
        recyclerView.setLayoutManager(mLayoutManager);
//
//        // Set the adapter
        recyclerView.setAdapter(recyclerAdapter);
        return view;
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        try {
            mListener = new OnFragmentInteractionListener() {
                public void onFragmentInteraction(PasswordItem item) {
                    if (item.getType() == PasswordItem.TYPE_CATEGORY) {
                        passListStack.push((ArrayList<PasswordItem>) recyclerAdapter.getValues().clone());
                        scrollPosition.push(recyclerView.getVerticalScrollbarPosition());
                        Log.d("FRAG", scrollPosition.peek() + "");
                        recyclerView.scrollToPosition(0);
                        recyclerAdapter.clear();
                        recyclerAdapter.addAll(PasswordRepository.getPasswords(item.getFile()));


                        ((ActionBarActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    } else {
                        ((PasswordStore) getActivity()).decryptPassword(item);
                    }
                }

                public void savePosition(Integer position) {

                }
            };
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
//        mListener.savePosition(mListView.getFirstVisiblePosition());
//        mListView.closeOpenedItems();
    }

    public void updateAdapter() {
        recyclerAdapter.clear();
        recyclerAdapter.addAll(PasswordRepository.getPasswords(new File(getArguments().getString("Path"))));
    }

    public void popBack() {
        recyclerView.scrollToPosition(scrollPosition.pop());
        recyclerAdapter.clear();
        recyclerAdapter.addAll(passListStack.pop());
    }

    public boolean isNotEmpty() {
        return !passListStack.isEmpty();
    }
}
