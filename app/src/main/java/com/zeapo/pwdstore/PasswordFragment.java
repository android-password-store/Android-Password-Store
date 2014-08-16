package com.zeapo.pwdstore;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.app.Fragment;
import android.util.Log;
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
import java.util.List;

/**
 * A fragment representing a list of Items.
 * <p />
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p />
 */
public class PasswordFragment extends Fragment implements SwipeListViewListener {

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
        mAdapter = new PasswordAdapter((PasswordStore) getActivity(), PasswordRepository.getPasswords(new File(path)));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_password, container, false);

        // Set the adapter
        mListView = (SwipeListView) view.findViewById(R.id.pass_list);
        ((AdapterView<ListAdapter>) mListView).setAdapter(mAdapter);
        // Set OnItemClickListener so we can be notified on item clicks
//        mListView.setOnItemClickListener(this);
        mListView.setSwipeListViewListener(this);
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

//    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        PasswordItem item = mAdapter.getItem(position);
        if (item.getType() == PasswordItem.TYPE_PASSWORD) {
//            if (item.selected) {
//                item.selected = false;
//            } else {
//                View right = view.findViewById(R.id.row_buttons);
//                ScaleAnimation animation = new ScaleAnimation(view.getX(), 0, view.getY(), 0, Animation.RELATIVE_TO_SELF, (float)0.5, Animation.RELATIVE_TO_SELF, (float)0.5);
//                right.setAnimation(animation);
//                item.selected = true;
//            }
        } else if (null != mListener) {
            // Notify the active callbacks interface (the activity, if the
            // fragment is attached to one) that an item has been selected.
            mListener.onFragmentInteraction(mAdapter.getItem(position));
        }

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

}
