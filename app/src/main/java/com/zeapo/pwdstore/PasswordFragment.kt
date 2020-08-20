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
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.view.ActionMode
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.databinding.PasswordRecyclerViewBinding
import com.zeapo.pwdstore.git.BaseGitActivity
import com.zeapo.pwdstore.git.GitOperationActivity
import com.zeapo.pwdstore.git.GitServerConfigActivity
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.ui.OnOffItemAnimator
import com.zeapo.pwdstore.ui.adapters.PasswordItemRecyclerAdapter
import com.zeapo.pwdstore.ui.dialogs.ItemCreationBottomSheet
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.base64
import com.zeapo.pwdstore.utils.getString
import com.zeapo.pwdstore.utils.sharedPrefs
import com.zeapo.pwdstore.utils.viewBinding
import java.io.File
import me.zhanghai.android.fastscroll.FastScrollerBuilder

class PasswordFragment : Fragment(R.layout.password_recycler_view) {

    private lateinit var recyclerAdapter: PasswordItemRecyclerAdapter
    private lateinit var listener: OnFragmentInteractionListener
    private lateinit var settings: SharedPreferences

    private var recyclerViewStateToRestore: Parcelable? = null
    private var actionMode: ActionMode? = null
    private var scrollTarget: File? = null

    private val model: SearchableRepositoryViewModel by activityViewModels()
    private val binding by viewBinding(PasswordRecyclerViewBinding::bind)
    private val swipeResult = registerForActivityResult(StartActivityForResult()) {
        binding.swipeRefresher.isRefreshing = false
        requireStore().refreshPasswordList()
    }

    val currentDir: File
        get() = model.currentDir.value!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = requireContext().sharedPrefs
        initializePasswordList()
        binding.fab.setOnClickListener {
            ItemCreationBottomSheet().show(childFragmentManager, "BOTTOM_SHEET")
        }
        childFragmentManager.setFragmentResultListener(ITEM_CREATION_REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            when (bundle.getString(ACTION_KEY)) {
                ACTION_FOLDER -> requireStore().createFolder()
                ACTION_PASSWORD -> requireStore().createPassword()
            }
        }
    }

    private fun initializePasswordList() {
        val gitDir = File(PasswordRepository.getRepositoryDirectory(), ".git")
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
                        swipeResult.launch(intent)
                    }
                    .show()
                binding.swipeRefresher.isRefreshing = false
            } else {
                // When authentication is set to ConnectionMode.None then the only git operation we
                // can run is a pull, so automatically fallback to that.
                val operationId = when (GitSettings.connectionMode) {
                    ConnectionMode.None -> BaseGitActivity.REQUEST_PULL
                    else -> BaseGitActivity.REQUEST_SYNC
                }
                val intent = Intent(context, GitOperationActivity::class.java)
                intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, operationId)
                swipeResult.launch(intent)
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
            addItemDecoration(DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL))
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
                when {
                    result.isFiltered -> {
                        // When the result is filtered, we always scroll to the top since that is where
                        // the best fuzzy match appears.
                        recyclerView.scrollToPosition(0)
                    }
                    scrollTarget != null -> {
                        scrollTarget?.let {
                            recyclerView.scrollToPosition(recyclerAdapter.getPositionForFile(it))
                        }
                        scrollTarget = null
                    }
                    else -> {
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
                recyclerAdapter.getSelectedItems()
                    .all { it.type == PasswordItem.TYPE_CATEGORY }
            return true
        }

        // Called when the user selects a contextual menu item
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.menu_delete_password -> {
                    requireStore().deletePasswords(recyclerAdapter.getSelectedItems())
                    // Action picked, so close the CAB
                    mode.finish()
                    true
                }
                R.id.menu_move_password -> {
                    requireStore().movePasswords(recyclerAdapter.getSelectedItems())
                    false
                }
                R.id.menu_edit_password -> {
                    requireStore().renameCategory(recyclerAdapter.getSelectedItems())
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
                    if (settings.getString(PreferenceKeys.SORT_ORDER) == PasswordRepository.PasswordSortOrder.RECENTLY_USED.name) {
                        //save the time when password was used
                        val preferences = context.getSharedPreferences("recent_password_history", Context.MODE_PRIVATE)
                        preferences.edit {
                            putString(item.file.absolutePath.base64(), System.currentTimeMillis().toString())
                        }
                    }

                    if (item.type == PasswordItem.TYPE_CATEGORY) {
                        navigateTo(item.file)
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

    private fun requireStore() = requireActivity() as PasswordStore

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

    fun dismissActionMode() {
        actionMode?.finish()
    }

    companion object {

        const val ITEM_CREATION_REQUEST_KEY = "creation_key"
        const val ACTION_KEY = "action"
        const val ACTION_FOLDER = "folder"
        const val ACTION_PASSWORD = "password"
    }


    fun navigateTo(file: File) {
        requireStore().clearSearch()
        model.navigateTo(
            file,
            recyclerViewState = binding.passRecycler.layoutManager!!.onSaveInstanceState()
        )
        requireStore().supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun scrollToOnNextRefresh(file: File) {
        scrollTarget = file
    }

    interface OnFragmentInteractionListener {

        fun onFragmentInteraction(item: PasswordItem)
    }
}
