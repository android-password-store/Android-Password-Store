/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.data.password

import app.passwordstore.data.passfile.Totp

class FieldItem(val key: String, val value: String, val action: ActionType) {
  enum class ActionType {
    COPY,
    HIDE,
  }

  enum class ItemType(val type: String, val label: String) {
    USERNAME("Username", "User ID"),
    PASSWORD("Password", "Password"),
    OTP("OTP", "OTP (expires in %ds)"),
  }

  companion object {

    // Extra helper methods
    fun createOtpField(totp: Totp, label: String): FieldItem {
      return FieldItem(label.format(totp.remainingTime.inWholeSeconds), totp.value, ActionType.COPY)
    }

    fun createPasswordField(password: String, label: String): FieldItem {
      return FieldItem(label, password, ActionType.HIDE)
    }

    fun createUsernameField(username: String, label: String): FieldItem {
      return FieldItem(label, username, ActionType.COPY)
    }
  }
}
