/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.git.GitActivity
import com.zeapo.pwdstore.ui.adapters.PasswordRecyclerAdapter
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.getPasswords
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.getRepositoryDirectory
import com.zeapo.pwdstore.utils.PasswordRepository.PasswordSortOrder.Companion.getSortOrder
import java.io.File
import java.util.Stack
import me.zhanghai.android.fastscroll.FastScrollerBuilder

/**
 * A fragment representing a list of Items.
 *
 * Large screen devices (such as tablets) are supported by replacing the ListView with a
 * GridView.
 *
 */

class PasswordFragment : Fragment() {
    // store the pass files list in a stack
    private var passListStack: Stack<ArrayList<PasswordItem>> = Stack()
    private var pathStack: Stack<File> = Stack()
    private var scrollPosition: Stack<Int> = Stack()
    private lateinit var recyclerAdapter: PasswordRecyclerAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var listener: OnFragmentInteractionListener
    private lateinit var settings: SharedPreferences
    private lateinit var swipeRefresher: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = requireNotNull(requireArguments().getString("Path"))
        settings = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        recyclerAdapter = PasswordRecyclerAdapter((requireActivity() as PasswordStore),
                listener, getPasswords(File(path), getRepositoryDirectory(requireContext()), sortOrder))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.password_recycler_view, container, false)
        // use a linear layout manager
        val layoutManager = LinearLayoutManager(requireContext())
        swipeRefresher = view.findViewById(R.id.swipe_refresher)
        swipeRefresher.setOnRefreshListener {
            if (!PasswordRepository.isGitRepo()) {
                Snackbar.make(view, getString(R.string.clone_git_repo), Snackbar.LENGTH_SHORT).show()
                swipeRefresher.isRefreshing = false
            } else {
                val intent = Intent(context, GitActivity::class.java)
                intent.putExtra("Operation", GitActivity.REQUEST_SYNC)
                startActivityForResult(intent, GitActivity.REQUEST_SYNC)
            }
        }
        recyclerView = view.findViewById(R.id.pass_recycler)
        recyclerView.layoutManager = layoutManager
        // use divider
        recyclerView.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        // Set the adapter
        recyclerView.adapter = recyclerAdapter
        // Setup fast scroller
        FastScrollerBuilder(recyclerView).build()
        val fab = view.findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            toggleFabExpand(fab)
        }

        view.findViewById<FloatingActionButton>(R.id.create_folder).setOnClickListener {
            (requireActivity() as PasswordStore).createFolder()
            toggleFabExpand(fab)
        }

        view.findViewById<FloatingActionButton>(R.id.create_password).setOnClickListener {
            (requireActivity() as PasswordStore).createPassword()
            toggleFabExpand(fab)
        }
        registerForContextMenu(recyclerView)
        return view
    }

    private fun toggleFabExpand(fab: FloatingActionButton) = with(fab) {
        isExpanded = !isExpanded
        isActivated = isExpanded
        animate().rotationBy(if (isExpanded) -45f else 45f).setDuration(100).start()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = object : OnFragmentInteractionListener {
                override fun onFragmentInteraction(item: PasswordItem) {
                    if (item.type == PasswordItem.TYPE_CATEGORY) { // push the current password list (non filtered plz!)
                        passListStack.push(
                                if (pathStack.isEmpty())
                                    getPasswords(getRepositoryDirectory(context), sortOrder)
                                else
                                    getPasswords(pathStack.peek(), getRepositoryDirectory(context), sortOrder)
                        )
                        // push the category were we're going
                        pathStack.push(item.file)
                        scrollPosition.push((recyclerView.layoutManager as LinearLayoutManager).findLastCompletelyVisibleItemPosition())
                        recyclerView.scrollToPosition(0)
                        recyclerAdapter.clear()
                        recyclerAdapter.addAll(getPasswords(item.file, getRepositoryDirectory(context), sortOrder))
                        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    } else {
                        if (requireArguments().getBoolean("matchWith", false)) {
                            (requireActivity() as PasswordStore).matchPasswordWithApp(item)
                        } else {
                            (requireActivity() as PasswordStore).decryptPassword(item)
                        }
                    }
                }
            }
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        swipeRefresher.isRefreshing = false
    }

    /** clears the adapter content and sets it back to the root view  */
    fun updateAdapter() {
        passListStack.clear()
        pathStack.clear()
        scrollPosition.clear()
        recyclerAdapter.clear()
        recyclerAdapter.addAll(getPasswords(getRepositoryDirectory(requireContext()), sortOrder))
        (requireActivity() as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    /** refreshes the adapter with the latest opened category  */
    fun refreshAdapter() {
        recyclerAdapter.clear()
        val currentDir = if (pathStack.isEmpty()) getRepositoryDirectory(requireContext()) else pathStack.peek()
        recyclerAdapter.addAll(
                if (pathStack.isEmpty())
                    getPasswords(currentDir, sortOrder)
                else
                    getPasswords(currentDir, getRepositoryDirectory(requireContext()), sortOrder)
        )
    }

    /**
     * filters the list adapter
     *
     * @param filter the filter to apply
     */
    fun filterAdapter(filter: String) {
        if (filter.isEmpty()) {
            refreshAdapter()
        } else {
            recursiveFilter(
                    filter,
                    if (pathStack.isEmpty() ||
                            settings.getBoolean("search_from_root", false))
                        null
                    else pathStack.peek())
        }
    }

    /**
     * fuzzy matches the filter against the given string
     *
     * based on https://www.forrestthewoods.com/blog/reverse_engineering_sublime_texts_fuzzy_match/
     *
     * @param filter the filter to apply
     * @param str the string to filter against
     *
     * @return true if the filter fuzzymatches the string
     */
    private fun fuzzyMatch(filter: String, str: String): Boolean {
        var i = 0
        var j = 0
        while (i < filter.length && j < str.length) {
            if (filter[i].isWhitespace() || filter[i].toLowerCase() == str[j].toLowerCase())
                i++
            j++
        }
        return i == filter.length
    }

    /**
     * recursively filters a directory and extract all the matching items
     *
     * @param filter the filter to apply
     * @param dir the directory to filter
     */
    private fun recursiveFilter(filter: String, dir: File?) { // on the root the pathStack is empty
        val passwordItems = if (dir == null)
            getPasswords(getRepositoryDirectory(requireContext()), sortOrder)
        else
            getPasswords(dir, getRepositoryDirectory(requireContext()), sortOrder)
        val rec = settings.getBoolean("filter_recursively", true)
        for (item in passwordItems) {
            if (item.type == PasswordItem.TYPE_CATEGORY && rec) {
                recursiveFilter(filter, item.file)
            }
            val matches = fuzzyMatch(filter, item.longName)
            val inAdapter = recyclerAdapter.values.contains(item)
            if (matches && !inAdapter) {
                recyclerAdapter.add(item)
            } else if (!matches && inAdapter) {
                recyclerAdapter.remove(recyclerAdapter.values.indexOf(item))
            }
        }
    }

    /** Goes back one level back in the path  */
    fun popBack() {
        if (passListStack.isEmpty()) return
        (recyclerView.layoutManager as LinearLayoutManager).scrollToPosition(scrollPosition.pop())
        recyclerAdapter.clear()
        recyclerAdapter.addAll(passListStack.pop())
        pathStack.pop()
    }

    /**
     * gets the current directory
     *
     * @return the current directory
     */
    val currentDir: File?
        get() = if (pathStack.isEmpty()) getRepositoryDirectory(requireContext()) else pathStack.peek()

    val isNotEmpty: Boolean
        get() = !passListStack.isEmpty()

    fun dismissActionMode() {
        recyclerAdapter.actionMode?.finish()
    }

    private val sortOrder: PasswordRepository.PasswordSortOrder
        get() = getSortOrder(settings)

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(item: PasswordItem)
    }
}
