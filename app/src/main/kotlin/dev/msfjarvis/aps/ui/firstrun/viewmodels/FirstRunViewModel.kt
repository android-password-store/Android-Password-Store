/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.ui.firstrun.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.StoreRepository
import dev.msfjarvis.aps.db.entity.StoreEntity
import dev.msfjarvis.aps.utils.SAFUtils
import kotlinx.coroutines.launch
import javax.inject.Inject

class FirstRunViewModel @Inject constructor(private val storeRepository: StoreRepository, private val context: Context) : ViewModel() {

  private val _uri: MutableLiveData<Uri> = MutableLiveData()
  private val _name: MutableLiveData<String> = MutableLiveData("")
  private val _external: MutableLiveData<Boolean> = MutableLiveData(false)
  private val _initialized: MutableLiveData<Boolean> = MutableLiveData(false)
  private val _isGitStore: MutableLiveData<Boolean> = MutableLiveData(false)

  val uri: LiveData<Uri> get() = _uri
  val name: LiveData<String> get() = _name
  val external: LiveData<Boolean> get() = _external
  val initialized: LiveData<Boolean> get() = _initialized
  val isGitStore: LiveData<Boolean> get() = _isGitStore

  fun setStoreUri(uri: Uri) {
    _uri.value = uri
    _uri.postValue(uri)
  }

  fun setGitStore(isGitStore: Boolean) {
    _isGitStore.value = isGitStore
    _isGitStore.postValue(isGitStore)
  }

  fun setInitialized(initialized: Boolean) {
    _initialized.value = initialized
    _initialized.postValue(initialized)
  }

  fun setName(name: String) {
    _name.value = name
    _name.postValue(name)
  }

  fun setExternal(external: Boolean) {
    _external.value = external
    _external.postValue(external)
  }

  fun addPasswordStore() {
    val storeUri = createStoreDirectory()
    val storeEntity = StoreEntity(name = name.value!!, uri = storeUri, external = external.value!!, initialized = initialized.value!!, isGitStore = isGitStore.value!!)
    viewModelScope.launch {
      storeRepository.addStoreToDB(storeEntity)
    }
  }

  @Throws(Exception::class)
  private fun createStoreDirectory(): Uri {
    val rootUri = requireNotNull(uri.value)
    val rootDir = requireNotNull(SAFUtils.documentFileFromUri(context, rootUri))
    val storeDir = rootDir.createDirectory(requireNotNull(_name.value))
    Log.d("FirstRunViewModel", "Store directory created")
    if (storeDir != null) {
      return storeDir.uri
    } else {
      throw Exception(context.getString(R.string.exception_cannot_create_directory))
    }
  }
}