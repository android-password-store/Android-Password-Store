package com.zeapo.pwdstore;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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
        void onFragmentInteraction(PasswordItem item);
    }

    // store the pass files list in a stack
    private Stack<File> pathStack;
    private FolderRecyclerAdapter recyclerAdapter;
    private RecyclerView recyclerView;
    private OnFragmentInteractionListener mListener;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public SelectFolderFragment() {   }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getArguments().getString("Path");

        pathStack = new Stack<>();
        recyclerAdapter = new FolderRecyclerAdapter((SelectFolderActivity) getActivity(), mListener,
                                                      PasswordRepository.getPasswords(new File(path), PasswordRepository.getRepositoryDirectory(getActivity()), getSortOrder()));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.password_recycler_view, container, false);

        // use a linear layout manager
        recyclerView = view.findViewById(R.id.pass_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        // use divider
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), R.drawable.divider));

        // Set the adapter
        recyclerView.setAdapter(recyclerAdapter);
        FloatingActionButton fab = view.findViewById(R.id.fab);
        if (fab != null) fab.hide();

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
                        //push the category were we're going
                        pathStack.push(item.getFile());

                        recyclerView.scrollToPosition(0);
                        recyclerAdapter.clear();
                        recyclerAdapter.addAll(PasswordRepository.getPasswords(item.getFile(), PasswordRepository.getRepositoryDirectory(context), getSortOrder()));

                        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    }
                }
            };
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                + " must implement OnFragmentInteractionListener");
        }
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

    private PasswordRepository.PasswordSortOrder getSortOrder() {
        return PasswordRepository.PasswordSortOrder.getSortOrder(PreferenceManager.getDefaultSharedPreferences(getActivity()));
    }
}
