package com.zeapo.pwdstore;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.zeapo.pwdstore.crypto.PgpHandler;
import com.zeapo.pwdstore.utils.FolderRecyclerAdapter;
import com.zeapo.pwdstore.utils.PasswordItem;
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
public class SelectFolderFragment extends Fragment{

    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(PasswordItem item);
    }

    // store the pass files list in a stack
    private Stack<ArrayList<PasswordItem>> passListStack;
    private Stack<File> pathStack;
    private Stack<Integer> scrollPosition;
    private FolderRecyclerAdapter recyclerAdapter;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager mLayoutManager;
    private OnFragmentInteractionListener mListener;
    private SharedPreferences settings;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public SelectFolderFragment() {   }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getArguments().getString("Path");

        settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
        passListStack = new Stack<ArrayList<PasswordItem>>();
        scrollPosition = new Stack<Integer>();
        pathStack = new Stack<File>();
        recyclerAdapter = new FolderRecyclerAdapter((PgpHandler) getActivity(), mListener,
                                                      PasswordRepository.getPasswords(new File(path), PasswordRepository.getRepositoryDirectory(getActivity())));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.password_recycler_view, container, false);

        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(getActivity());

        recyclerView = (RecyclerView) view.findViewById(R.id.pass_recycler);
        recyclerView.setLayoutManager(mLayoutManager);

        // use divider
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), R.drawable.divider));

        // Set the adapter
        recyclerView.setAdapter(recyclerAdapter);

        final FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((PasswordStore) getActivity()).createPassword();
            }
        });

        registerForContextMenu(recyclerView);
        return view;
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            mListener = new OnFragmentInteractionListener() {
                public void onFragmentInteraction(PasswordItem item) {
                    if (item.getType() == PasswordItem.TYPE_CATEGORY) {
                        // push the current password list (non filtered plz!)
                        passListStack.push(pathStack.isEmpty() ?
                                                PasswordRepository.getPasswords(PasswordRepository.getRepositoryDirectory(context)) :
                                                PasswordRepository.getPasswords(pathStack.peek(), PasswordRepository.getRepositoryDirectory(context)));
                        //push the category were we're going
                        pathStack.push(item.getFile());
                        scrollPosition.push(recyclerView.getVerticalScrollbarPosition());

                        recyclerView.scrollToPosition(0);
                        recyclerAdapter.clear();
                        recyclerAdapter.addAll(PasswordRepository.getPasswords(item.getFile(), PasswordRepository.getRepositoryDirectory(context)));

                        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    }
                }

                public void savePosition(Integer position) {

                }
            };
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
//        mListener.savePosition(mListView.getFirstVisiblePosition());
//        mListView.closeOpenedItems();
    }

    /**
     * clears the adapter content and sets it back to the root view
     */
    public void updateAdapter() {
        passListStack.clear();
        pathStack.clear();
        scrollPosition.clear();
        recyclerAdapter.clear();
        recyclerAdapter.addAll(PasswordRepository.getPasswords(PasswordRepository.getRepositoryDirectory(getActivity())));

        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    }

    /**
     * refreshes the adapter with the latest opened category
     */
    public void refreshAdapter() {
        recyclerAdapter.clear();
        recyclerAdapter.addAll(pathStack.isEmpty() ?
                                        PasswordRepository.getPasswords(PasswordRepository.getRepositoryDirectory(getActivity())) :
                                        PasswordRepository.getPasswords(pathStack.peek(), PasswordRepository.getRepositoryDirectory(getActivity())));
    }

    /**
     * filters the list adapter
     * @param filter the filter to apply
     */
    public void filterAdapter(String filter) {
        Log.d("FRAG", "filter: " + filter);

        if (filter.isEmpty()) {
            refreshAdapter();
        } else {
            recursiveFilter(filter, pathStack.isEmpty() ? null : pathStack.peek());
        }
    }

    /**
     * recursively filters a directory and extract all the matching items
     * @param filter the filter to apply
     * @param dir the directory to filter
     */
    private void recursiveFilter(String filter, File dir) {
        // on the root the pathStack is empty
        ArrayList<PasswordItem> passwordItems = dir == null ?
                PasswordRepository.getPasswords(PasswordRepository.getRepositoryDirectory(getActivity())) :
                PasswordRepository.getPasswords(dir, PasswordRepository.getRepositoryDirectory(getActivity()));

        boolean rec = settings.getBoolean("filter_recursively", true);
        for (PasswordItem item : passwordItems) {
            if (item.getType() == PasswordItem.TYPE_CATEGORY && rec) {
                recursiveFilter(filter, item.getFile());
            }
            boolean matches = item.toString().toLowerCase().contains(filter.toLowerCase());
            boolean inAdapter = recyclerAdapter.getValues().contains(item);
            if (matches && !inAdapter) {
                recyclerAdapter.add(item);
            } else if (!matches && inAdapter) {
                recyclerAdapter.remove(recyclerAdapter.getValues().indexOf(item));
            }
        }
    }

    /**
     * Goes back one level back in the path
     */
    public void popBack() {
        if (passListStack.isEmpty())
            return;

        recyclerView.scrollToPosition(scrollPosition.pop());
        recyclerAdapter.clear();
        recyclerAdapter.addAll(passListStack.pop());
        pathStack.pop();
    }

    /**
     * gets the current directory
     * @return the current directory
     */
    public File getCurrentDir() {
        if (pathStack.isEmpty())
            return PasswordRepository.getRepositoryDirectory(getActivity().getApplicationContext());
        else
            return pathStack.peek();
    }

    public boolean isNotEmpty() {
        return !passListStack.isEmpty();
    }
}
