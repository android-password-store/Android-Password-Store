/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.data.password

import app.passwordstore.data.passfile.Totp
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class FieldItem(val key: String, val value: String, val action: ActionType) {
  enum class ActionType {
    COPY,
    HIDE
  }

  enum class ItemType(val type: String, val label: String) {
    USERNAME("Username", "Username"),
    PASSWORD("Password", "Password"),
    OTP("OTP", "OTP (expires in %ds)"),
  }

  companion object {

    // Extra helper methods
    fun createOtpField(totp: Totp): FieldItem {
      return FieldItem(
        ItemType.OTP.label.format(totp.remainingTime.inWholeSeconds),
        totp.value,
        ActionType.COPY,
      )
    }

    fun createPasswordField(password: String): FieldItem {
      return FieldItem(ItemType.PASSWORD.label, password, ActionType.HIDE)
    }

    fun createUsernameField(username: String): FieldItem {
      return FieldItem(ItemType.USERNAME.label, username, ActionType.COPY)
    }
  }
}
