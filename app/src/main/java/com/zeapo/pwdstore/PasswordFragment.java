package com.zeapo.pwdstore;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.fortysevendeg.swipelistview.BaseSwipeListViewListener;
import com.fortysevendeg.swipelistview.SwipeListView;
import com.fortysevendeg.swipelistview.SwipeListViewListener;
import com.zeapo.pwdstore.utils.PasswordAdapter;
import com.zeapo.pwdstore.utils.PasswordItem;
import com.zeapo.pwdstore.utils.PasswordRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * A fragment representing a list of Items.
 * <p />
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p />
 */
public class PasswordFragment extends Fragment implements SwipeListViewListener{

    // store the pass files list in a stack
    private Stack<ArrayList<PasswordItem>> passListStack;

    private OnFragmentInteractionListener mListener;

    /**
     * The fragment's ListView/GridView.
     */
    private SwipeListView mListView;

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

        passListStack = new Stack<ArrayList<PasswordItem>>();

        mAdapter = new PasswordAdapter((PasswordStore) getActivity(), PasswordRepository.getPasswords(new File(path)));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_password, container, false);

        // Set the adapter
        mListView = (SwipeListView) view.findViewById(R.id.pass_list);
        ((AdapterView<ListAdapter>) mListView).setAdapter(mAdapter);
        mListView.setSwipeListViewListener(this);
        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = new OnFragmentInteractionListener() {
                @Override
                public void onFragmentInteraction(PasswordItem item) {
                    if (item.getType() == PasswordItem.TYPE_CATEGORY) {
                        passListStack.push((ArrayList<PasswordItem>) mAdapter.getValues().clone());
                        mAdapter.clear();
                        mAdapter.addAll(PasswordRepository.getPasswords(item.getFile()));

                        ((ActionBarActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    }
                }

                @Override
                public void savePosition(Integer position) {

                }
            };
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
        mListView.closeOpenedItems();
    }

    @Override
    public void onOpened(int i, boolean b) {

    }

    @Override
    public void onClosed(int i, boolean b) {

    }

    @Override
    public void onListChanged() {

    }

    @Override
    public void onMove(int i, float v) {

    }

    @Override
    public void onStartOpen(int i, int i2, boolean b) {

    }

    @Override
    public void onStartClose(int i, boolean b) {

    }

    @Override
    public void onClickFrontView(int i) {
        if (mAdapter.getItem(i).getType() == PasswordItem.TYPE_PASSWORD) {
            mListView.openAnimate(i);
        } else if (null != mListener) {
            // Notify the active callbacks interface (the activity, if the
            // fragment is attached to one) that an item has been selected.
            mListener.onFragmentInteraction(mAdapter.getItem(i));
        }
    }

    @Override
    public void onClickBackView(int i) {
        mListView.closeAnimate(i);
    }

    @Override
    public void onDismiss(int[] ints) {

    }

    @Override
    public int onChangeSwipeMode(int i) {
        return 0;
    }

    @Override
    public void onChoiceChanged(int i, boolean b) {

    }

    @Override
    public void onChoiceStarted() {

    }

    @Override
    public void onChoiceEnded() {

    }

    @Override
    public void onFirstListItem() {

    }

    @Override
    public void onLastListItem() {

    }

    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(PasswordItem item);
        public void savePosition(Integer position);
    }

    public void updateAdapter() {
        mAdapter.clear();
        mAdapter.addAll(PasswordRepository.getPasswords(new File(getArguments().getString("Path"))));
        mListView.setAdapter((ListAdapter) mAdapter);
    }

    public void popBack() {
        mAdapter.clear();
        mAdapter.addAll(passListStack.pop());
    }

    public boolean isNotEmpty() {
        return !passListStack.isEmpty();
    }
}
