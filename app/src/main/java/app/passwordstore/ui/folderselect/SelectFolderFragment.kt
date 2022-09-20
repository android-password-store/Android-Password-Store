/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.folderselect

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.passwordstore.R
import app.passwordstore.data.password.PasswordItem
import app.passwordstore.databinding.PasswordRecyclerViewBinding
import app.passwordstore.ui.adapters.PasswordItemRecyclerAdapter
import app.passwordstore.ui.passwords.PasswordStore
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.viewmodel.ListMode
import app.passwordstore.util.viewmodel.SearchableRepositoryViewModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import java.io.File
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.zhanghai.android.fastscroll.FastScrollerBuilder

class SelectFolderFragment : Fragment(R.layout.password_recycler_view) {

  private val binding by viewBinding(PasswordRecyclerViewBinding::bind)
  private lateinit var recyclerAdapter: PasswordItemRecyclerAdapter
  private lateinit var listener: OnFragmentInteractionListener

  private val model: SearchableRepositoryViewModel by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.fab.hide()
    recyclerAdapter =
      PasswordItemRecyclerAdapter(lifecycleScope).onItemClicked { _, item ->
        listener.onFragmentInteraction(item)
      }
    binding.passRecycler.apply {
      layoutManager = LinearLayoutManager(requireContext())
      itemAnimator = null
      adapter = recyclerAdapter
    }

    FastScrollerBuilder(binding.passRecycler).build()
    registerForContextMenu(binding.passRecycler)

    val path = requireNotNull(requireArguments().getString(PasswordStore.REQUEST_ARG_PATH))
    model.navigateTo(File(path), listMode = ListMode.DirectoriesOnly, pushPreviousLocation = false)
    model.searchResult
      .flowWithLifecycle(lifecycle)
      .onEach { result -> recyclerAdapter.submitList(result.passwordItems) }
      .launchIn(lifecycleScope)
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    runCatching {
        listener =
          object : OnFragmentInteractionListener {
            override fun onFragmentInteraction(item: PasswordItem) {
              if (item.type == PasswordItem.TYPE_CATEGORY) {
                model.navigateTo(item.file, listMode = ListMode.DirectoriesOnly)
                (requireActivity() as AppCompatActivity)
                  .supportActionBar
                  ?.setDisplayHomeAsUpEnabled(true)
              }
            }
          }
      }
      .onFailure {
        throw ClassCastException("$context must implement OnFragmentInteractionListener")
      }
  }

  val currentDir: File
    get() = model.currentDir.value

  interface OnFragmentInteractionListener {

    fun onFragmentInteraction(item: PasswordItem)
  }
}
