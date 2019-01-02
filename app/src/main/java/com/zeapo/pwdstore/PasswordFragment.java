package com.zeapo.pwdstore;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.zeapo.pwdstore.utils.PasswordItem;
import com.zeapo.pwdstore.utils.PasswordRecyclerAdapter;
import com.zeapo.pwdstore.utils.PasswordRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.Stack;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p/>
 */
public class PasswordFragment extends Fragment {

    // store the pass files list in a stack
    private Stack<ArrayList<PasswordItem>> passListStack;
    private Stack<File> pathStack;
    private Stack<Integer> scrollPosition;
    private PasswordRecyclerAdapter recyclerAdapter;
    private RecyclerView recyclerView;
    private OnFragmentInteractionListener mListener;
    private SharedPreferences settings;
    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public PasswordFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getArguments().getString("Path");

        settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
        passListStack = new Stack<>();
        scrollPosition = new Stack<>();
        pathStack = new Stack<>();
        recyclerAdapter = new PasswordRecyclerAdapter((PasswordStore) getActivity(), mListener,
                PasswordRepository.getPasswords(new File(path), PasswordRepository.getRepositoryDirectory(getActivity()), getSortOrder()));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.password_recycler_view, container, false);

        // use a linear layout manager
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());

        recyclerView = (RecyclerView) view.findViewById(R.id.pass_recycler);
        recyclerView.setLayoutManager(mLayoutManager);

        // use divider
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), R.drawable.divider));

        // Set the adapter
        recyclerView.setAdapter(recyclerAdapter);

        final FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab);
        fab.setOnClickListener(v -> ((PasswordStore) getActivity()).createPassword());

        registerForContextMenu(recyclerView);
        return view;
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            mListener = item -> {
                if (item.getType() == PasswordItem.TYPE_CATEGORY) {
                    // push the current password list (non filtered plz!)
                    passListStack.push(pathStack.isEmpty() ?
                            PasswordRepository.getPasswords(PasswordRepository.getRepositoryDirectory(context), getSortOrder()) :
                            PasswordRepository.getPasswords(pathStack.peek(), PasswordRepository.getRepositoryDirectory(context), getSortOrder()));
                    //push the category were we're going
                    pathStack.push(item.getFile());
                    scrollPosition.push(recyclerView.getVerticalScrollbarPosition());

                    recyclerView.scrollToPosition(0);
                    recyclerAdapter.clear();
                    recyclerAdapter.addAll(PasswordRepository.getPasswords(item.getFile(), PasswordRepository.getRepositoryDirectory(context), getSortOrder()));

                    ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                } else {
                    if (getArguments().getBoolean("matchWith", false)) {
                        ((PasswordStore) getActivity()).matchPasswordWithApp(item);
                    } else {
                        ((PasswordStore) getActivity()).decryptPassword(item);
                    }
                }
            };
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    /**
     * clears the adapter content and sets it back to the root view
     */
    public void updateAdapter() {
        passListStack.clear();
        pathStack.clear();
        scrollPosition.clear();
        recyclerAdapter.clear();
        recyclerAdapter.addAll(PasswordRepository.getPasswords(PasswordRepository.getRepositoryDirectory(getActivity()), getSortOrder()));

        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    }

    /**
     * refreshes the adapter with the latest opened category
     */
    public void refreshAdapter() {
        recyclerAdapter.clear();
        recyclerAdapter.addAll(pathStack.isEmpty() ?
                PasswordRepository.getPasswords(PasswordRepository.getRepositoryDirectory(getActivity()), getSortOrder()) :
                PasswordRepository.getPasswords(pathStack.peek(), PasswordRepository.getRepositoryDirectory(getActivity()), getSortOrder()));
    }

    /**
     * filters the list adapter
     *
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
     *
     * @param filter the filter to apply
     * @param dir    the directory to filter
     */
    private void recursiveFilter(String filter, File dir) {
        // on the root the pathStack is empty
        ArrayList<PasswordItem> passwordItems = dir == null ?
                PasswordRepository.getPasswords(PasswordRepository.getRepositoryDirectory(getActivity()), getSortOrder()) :
                PasswordRepository.getPasswords(dir, PasswordRepository.getRepositoryDirectory(getActivity()), getSortOrder());

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
     *
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

    public void dismissActionMode() {
        if (recyclerAdapter != null && recyclerAdapter.mActionMode != null) {
            recyclerAdapter.mActionMode.finish();
        }
    }

    private PasswordRepository.PasswordSortOrder getSortOrder() {
        return PasswordRepository.PasswordSortOrder.getSortOrder(settings);
    }

    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(PasswordItem item);
    }
}
