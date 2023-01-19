/*
 * Copyright © 2014-2022 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.viewmodel

import androidx.lifecycle.ViewModel
import app.passwordstore.ui.crypto.PasswordDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.update

/**
 * ViewModel for classes that use [PasswordDialog] for getting user input. [PasswordDialog] will
 * ensure that it retrieves an instance of this [ViewModel] from the Activity scope and will post
 * values into [PasswordViewModel.password].
 */
class PasswordViewModel : ViewModel() {
  private val _password = MutableStateFlow("")
  val password = _password.asStateFlow().dropWhile(String::isBlank)

  fun setPassword(pass: String) {
    _password.update { pass }
  }
}
