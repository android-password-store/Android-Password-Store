/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.passwordstore.crypto.KeyUtils
import app.passwordstore.crypto.PGPIdentifier
import app.passwordstore.crypto.PGPKeyManager
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch

@HiltViewModel
class PGPKeyListViewModel @Inject constructor(private val keyManager: PGPKeyManager) : ViewModel() {
  var keys: ImmutableList<PGPIdentifier> by mutableStateOf(persistentListOf())

  init {
    updateKeySet()
  }

  fun updateKeySet() {
    viewModelScope.launch {
      keyManager
        .getAllKeys()
        .map { keys -> keys.mapNotNull { key -> KeyUtils.tryGetEmail(key) } }
        .onSuccess { keys = it.toPersistentList() }
    }
  }

  fun deleteKey(identifier: PGPIdentifier) {
    viewModelScope.launch {
      keyManager.removeKey(identifier)
      updateKeySet()
    }
  }
}
