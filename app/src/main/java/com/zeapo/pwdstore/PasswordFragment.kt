/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.databinding.PasswordRecyclerViewBinding
import com.zeapo.pwdstore.git.BaseGitActivity
import com.zeapo.pwdstore.git.GitOperationActivity
import com.zeapo.pwdstore.git.GitServerConfigActivity
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.ui.OnOffItemAnimator
import com.zeapo.pwdstore.ui.adapters.PasswordItemRecyclerAdapter
import com.zeapo.pwdstore.ui.dialogs.ItemCreationBottomSheet
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.viewBinding
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import java.io.File

class PasswordFragment : Fragment(R.layout.password_recycler_view) {
    private lateinit var recyclerAdapter: PasswordItemRecyclerAdapter
    private lateinit var listener: OnFragmentInteractionListener
    private lateinit var settings: SharedPreferences

    private var recyclerViewStateToRestore: Parcelable? = null
    private var actionMode: ActionMode? = null

    private val model: SearchableRepositoryViewModel by activityViewModels()
    private val binding by viewBinding(PasswordRecyclerViewBinding::bind)

    private fun requireStore() = requireActivity() as PasswordStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = PreferenceManager.getDefaultSharedPreferences(requireContext())
        initializePasswordList()
        binding.fab.setOnClickListener {
            ItemCreationBottomSheet().apply {
                setTargetFragment(this@PasswordFragment, 1000)
            }.show(parentFragmentManager, "BOTTOM_SHEET")
        }
    }

    private fun initializePasswordList() {
        val gitDir = File(PasswordRepository.getRepositoryDirectory(requireContext()), ".git")
        val hasGitDir = gitDir.exists() && gitDir.isDirectory && (gitDir.listFiles()?.isNotEmpty() == true)
        binding.swipeRefresher.setOnRefreshListener {
            if (!hasGitDir) {
                requireStore().refreshPasswordList()
                binding.swipeRefresher.isRefreshing = false
            } else if (!PasswordRepository.isGitRepo()) {
                Snackbar.make(binding.root, getString(R.string.clone_git_repo), Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.clone_button) {
                        val intent = Intent(context, GitServerConfigActivity::class.java)
                        intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, BaseGitActivity.REQUEST_CLONE)
                        startActivityForResult(intent, BaseGitActivity.REQUEST_CLONE)
                    }
                    .show()
                binding.swipeRefresher.isRefreshing = false
            } else {
                // When authentication is set to ConnectionMode.None then the only git operation we
                // can run is a pull, so automatically fallback to that.
                val operationId = when (ConnectionMode.fromString(settings.getString("git_remote_auth", null))) {
                    ConnectionMode.None -> BaseGitActivity.REQUEST_PULL
                    else -> BaseGitActivity.REQUEST_SYNC
                }
                val intent = Intent(context, GitOperationActivity::class.java)
                intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, operationId)
                startActivityForResult(intent, operationId)
            }
        }

        recyclerAdapter = PasswordItemRecyclerAdapter()
            .onItemClicked { _, item ->
                listener.onFragmentInteraction(item)
            }
            .onSelectionChanged { selection ->
                // In order to not interfere with drag selection, we disable the SwipeRefreshLayout
                // once an item is selected.
                binding.swipeRefresher.isEnabled = selection.isEmpty

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
        val recyclerView = binding.passRecycler
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

    private val actionModeCallback = object : ActionMode.Callback {
        // Called when the action mode is created; startActionMode() was called
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Inflate a menu resource providing context menu items
            mode.menuInflater.inflate(R.menu.context_pass, menu)
            // hide the fab
            animateFab(false)
            return true
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.findItem(R.id.menu_edit_password).isVisible =
                recyclerAdapter.getSelectedItems(requireContext())
                    .all { it.type == PasswordItem.TYPE_CATEGORY }
            return true
        }

        // Called when the user selects a contextual menu item
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.menu_delete_password -> {
                    requireStore().deletePasswords(recyclerAdapter.getSelectedItems(requireContext()))
                    // Action picked, so close the CAB
                    mode.finish()
                    true
                }
                R.id.menu_move_password -> {
                    requireStore().movePasswords(recyclerAdapter.getSelectedItems(requireContext()))
                    false
                }
                R.id.menu_edit_password -> {
                    requireStore().renameCategory(recyclerAdapter.getSelectedItems(requireContext()))
                    mode.finish()
                    false
                }
                else -> false
            }
        }

        // Called when the user exits the action mode
        override fun onDestroyActionMode(mode: ActionMode) {
            recyclerAdapter.requireSelectionTracker().clearSelection()
            actionMode = null
            // show the fab
            animateFab(true)
        }

        private fun animateFab(show: Boolean) = with(binding.fab) {
            val animation = AnimationUtils.loadAnimation(
                context, if (show) R.anim.scale_up else R.anim.scale_down
            )
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {
                }

                override fun onAnimationEnd(animation: Animation?) {
                    if (!show) visibility = View.GONE
                }

                override fun onAnimationStart(animation: Animation?) {
                    if (show) visibility = View.VISIBLE
                }
            })
            animate().rotationBy(if (show) -90f else 90f)
                .setStartDelay(if (show) 100 else 0)
                .setDuration(100)
                .start()
            startAnimation(animation)
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
                            recyclerViewState = binding.passRecycler.layoutManager!!.onSaveInstanceState()
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
        binding.swipeRefresher.isRefreshing = false
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

    fun createFolder() = requireStore().createFolder()

    fun createPassword() = requireStore().createPassword()

    interface OnFragmentInteractionListener {
        fun onFragmentInteraction(item: PasswordItem)
    }
}
