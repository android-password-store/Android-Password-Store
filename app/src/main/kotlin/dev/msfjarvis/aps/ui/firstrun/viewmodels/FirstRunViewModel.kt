/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.ui.firstrun.viewmodels

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.msfjarvis.aps.StoreRepository
import dev.msfjarvis.aps.db.PasswordStoreDatabase
import dev.msfjarvis.aps.db.entity.StoreEntity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class FirstRunViewModel @Inject constructor(storeRepository: StoreRepository): ViewModel() {

  init {
  }

  private lateinit var db: PasswordStoreDatabase
  private val _uri: MutableLiveData<Uri> = MutableLiveData()
  private val _name: MutableLiveData<String> = MutableLiveData("")
  private val _external: MutableLiveData<Boolean> = MutableLiveData(false)
  private val _initialized: MutableLiveData<Boolean> = MutableLiveData(false)
  private val _isGitStore: MutableLiveData<Boolean> = MutableLiveData(false)

  val uri get() = _uri
  val name get() = _name
  val external get() = _external
  val initialized get() = _initialized
  val isGitStore get() = _isGitStore

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

  fun addPasswordStore(context: Context) {
    db = PasswordStoreDatabase.getInstance(context)
    viewModelScope.launch {
      db.getStoreDao().insertStore(StoreEntity(name = name.value!!, uri = uri.value, external = external.value!!, initialized = initialized.value!!, isGitStore = isGitStore.value!!))
      Log.d("DBTEST", "Store inserted")
      db.getStoreDao().getAllStores().collect { list ->
        Log.d("DBTEST", "Store inserted: ${list.last()}")
      }
    }
  }
}