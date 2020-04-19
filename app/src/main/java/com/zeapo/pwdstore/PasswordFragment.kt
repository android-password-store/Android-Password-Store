/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import androidx.recyclerview.widget.FixOnItemTouchDispatchRecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.databinding.PasswordRecyclerViewBinding
import com.zeapo.pwdstore.git.BaseGitActivity
import com.zeapo.pwdstore.git.GitOperationActivity
import com.zeapo.pwdstore.ui.OnOffItemAnimator
import com.zeapo.pwdstore.ui.adapters.PasswordItemRecyclerAdapter
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.File
import java.util.Stack
import me.zhanghai.android.fastscroll.FastScrollerBuilder

class PasswordFragment : Fragment() {
    private lateinit var recyclerAdapter: PasswordItemRecyclerAdapter
    private lateinit var recyclerView: FixOnItemTouchDispatchRecyclerView
    private lateinit var listener: OnFragmentInteractionListener
    private lateinit var swipeRefresher: SwipeRefreshLayout

    private var recyclerViewStateToRestore: Parcelable? = null
    private var actionMode: ActionMode? = null
    private var _binding: PasswordRecyclerViewBinding? = null

    private val model: SearchableRepositoryViewModel by activityViewModels()
    private val binding get() = _binding!!

    private fun requireStore() = requireActivity() as PasswordStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = PasswordRecyclerViewBinding.inflate(inflater, container, false)
        initializePasswordList()
        val fab = binding.fab
        fab.setOnClickListener {
            toggleFabExpand(fab)
        }
        binding.createFolder.setOnClickListener {
            requireStore().createFolder()
            toggleFabExpand(fab)
        }
        binding.createPassword.setOnClickListener {
            requireStore().createPassword()
            toggleFabExpand(fab)
        }
        return binding.root
    }

    private fun initializePasswordList() {
        swipeRefresher = binding.swipeRefresher
        swipeRefresher.setOnRefreshListener {
            if (!PasswordRepository.isGitRepo()) {
                Snackbar.make(binding.root, getString(R.string.clone_git_repo), Snackbar.LENGTH_SHORT)
                    .show()
                swipeRefresher.isRefreshing = false
            } else {
                val intent = Intent(context, GitOperationActivity::class.java)
                intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, BaseGitActivity.REQUEST_SYNC)
                startActivityForResult(intent, BaseGitActivity.REQUEST_SYNC)
            }
        }

        recyclerAdapter = PasswordItemRecyclerAdapter()
            .onItemClicked { _, item ->
                listener.onFragmentInteraction(item)
            }
            .onSelectionChanged { selection ->
                // In order to not interfere with drag selection, we disable the SwipeRefreshLayout
                // once an item is selected.
                swipeRefresher.isEnabled = selection.isEmpty

                if (actionMode == null)
                    actionMode = requireStore().startSupportActionMode(actionModeCallback)
                        ?: return@onSelectionChanged

                if (!selection.isEmpty) {
                    actionMode!!.title = resources.getQuantityString(R.plurals.delete_title, selection.size(), selection.size())
                    actionMode!!.invalidate()
                } else {
                    actionMode!!.finish()
                }
            }
        recyclerView = binding.passRecycler
        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            itemAnimator = OnOffItemAnimator()
            adapter = recyclerAdapter
        }

        FastScrollerBuilder(recyclerView).build()
        recyclerAdapter.makeSelectable(recyclerView)
        registerForContextMenu(recyclerView)

        val path = requireNotNull(requireArguments().getString(PasswordStore.REQUEST_ARG_PATH))
        model.navigateTo(File(path), pushPreviousLocation = false)
        model.searchResult.observe(viewLifecycleOwner) { result ->
            // Only run animations when the new list is filtered, i.e., the user submitted a search,
            // and not on folder navigations since the latter leads to too many removal animations.
            (recyclerView.itemAnimator as OnOffItemAnimator).isEnabled = result.isFiltered
            recyclerAdapter.submitList(result.passwordItems) {
                if (result.isFiltered) {
                    // When the result is filtered, we always scroll to the top since that is where
                    // the best fuzzy match appears.
                    recyclerView.scrollToPosition(0)
                } else {
                    // When the result is not filtered and there is a saved scroll position for it,
                    // we try to restore it.
                    recyclerViewStateToRestore?.let {
                        recyclerView.layoutManager!!.onRestoreInstanceState(it)
                    }
                    recyclerViewStateToRestore = null
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun toggleFabExpand(fab: FloatingActionButton) = with(fab) {
        isExpanded = !isExpanded
        isActivated = isExpanded
        animate().rotationBy(if (isExpanded) -45f else 45f).setDuration(100).start()
    }

    private val actionModeCallback = object : ActionMode.Callback {

        // Called when the action mode is created; startActionMode() was called
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Inflate a menu resource providing context menu items
            mode.menuInflater.inflate(R.menu.context_pass, menu)
            // hide the fab
            binding.fab.visibility = View.GONE
            return true
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.findItem(R.id.menu_edit_password).isVisible =
                recyclerAdapter.getSelectedItems(requireContext())
                    .map { it.type == PasswordItem.TYPE_PASSWORD }
                    .singleOrNull() == true
            return true // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.menu_delete_password -> {
                    requireStore().deletePasswords(
                        Stack<PasswordItem>().apply {
                            recyclerAdapter.getSelectedItems(requireContext()).forEach { push(it) }
                        }
                    )
                    mode.finish() // Action picked, so close the CAB
                    return true
                }
                R.id.menu_edit_password -> {
                    requireStore().editPassword(
                        recyclerAdapter.getSelectedItems(requireContext()).first()
                    )
                    mode.finish()
                    return true
                }
                R.id.menu_move_password -> {
                    requireStore().movePasswords(recyclerAdapter.getSelectedItems(requireContext()))
                    return false
                }
                else -> return false
            }
        }

        // Called when the user exits the action mode
        override fun onDestroyActionMode(mode: ActionMode) {
            recyclerAdapter.requireSelectionTracker().clearSelection()
            actionMode = null
            // show the fab
            binding.fab.visibility = View.VISIBLE
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = object : OnFragmentInteractionListener {
                override fun onFragmentInteraction(item: PasswordItem) {
                    if (item.type == PasswordItem.TYPE_CATEGORY) {
                        requireStore().clearSearch()
                        model.navigateTo(
                            item.file,
                            recyclerViewState = recyclerView.layoutManager!!.onSaveInstanceState()
                        )
                        requireStore().supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    } else {
                        if (requireArguments().getBoolean("matchWith", false)) {
                            requireStore().matchPasswordWithApp(item)
                        } else {
                            requireStore().decryptPassword(item)
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

    /**
     * Returns true if the back press was handled by the [Fragment].
     */
    fun onBackPressedInActivity(): Boolean {
        if (!model.canNavigateBack)
            return false
        // The RecyclerView state is restored when the asynchronous update operation on the
        // adapter is completed.
        recyclerViewStateToRestore = model.navigateBack()
        if (!model.canNavigateBack)
            requireStore().supportActionBar?.setDisplayHomeAsUpEnabled(false)
        return true
    }

    val currentDir: File
        get() = model.currentDir.value!!

    fun dismissActionMode() {
        actionMode?.finish()
    }

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(item: PasswordItem)
    }
}
