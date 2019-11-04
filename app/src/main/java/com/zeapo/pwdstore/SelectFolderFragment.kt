/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.zeapo.pwdstore.ui.adapters.FolderRecyclerAdapter
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.getPasswords
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.getRepositoryDirectory
import com.zeapo.pwdstore.utils.PasswordRepository.PasswordSortOrder.Companion.getSortOrder
import java.io.File
import java.util.Stack

/**
 * A fragment representing a list of Items.
 *
 *
 * Large screen devices (such as tablets) are supported by replacing the ListView with a
 * GridView.
 *
 *
 *
 */

class SelectFolderFragment : Fragment() {
    // store the pass files list in a stack
    private var pathStack: Stack<File> = Stack()
    private lateinit var recyclerAdapter: FolderRecyclerAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var listener: OnFragmentInteractionListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = requireNotNull(requireArguments().getString("Path"))
        recyclerAdapter = FolderRecyclerAdapter(listener, getPasswords(File(path), getRepositoryDirectory(requireActivity()), sortOrder))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.password_recycler_view, container, false)
        // use a linear layout manager
        recyclerView = view.findViewById(R.id.pass_recycler)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // use divider
        recyclerView.addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        // Set the adapter
        recyclerView.adapter = recyclerAdapter
        val fab: FloatingActionButton = view.findViewById(R.id.fab)
        fab.hide()
        registerForContextMenu(recyclerView)
        return view
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = object : OnFragmentInteractionListener {
                override fun onFragmentInteraction(item: PasswordItem) {
                    if (item.type == PasswordItem.TYPE_CATEGORY) {
                        // push the category were we're going
                        pathStack.push(item.file)
                        recyclerView.scrollToPosition(0)
                        recyclerAdapter.clear()
                        recyclerAdapter.addAll(getPasswords(
                                item.file,
                                getRepositoryDirectory(context),
                                sortOrder)
                        )
                        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    }
                }
            }
        } catch (e: ClassCastException) {
            throw ClassCastException(
                    "$context must implement OnFragmentInteractionListener")
        }
    }

    /**
     * gets the current directory
     *
     * @return the current directory
     */
    val currentDir: File?
        get() = if (pathStack.isEmpty()) getRepositoryDirectory(requireContext()) else pathStack.peek()

    private val sortOrder: PasswordRepository.PasswordSortOrder
        get() = getSortOrder(PreferenceManager.getDefaultSharedPreferences(requireContext()))

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(item: PasswordItem)
    }
}
