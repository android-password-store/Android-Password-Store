/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.data.password

class FieldItem(val key: String, val value: String, val action: ActionType) {
  enum class ActionType {
    COPY,
    HIDE
  }

  enum class ItemType(val type: String) {
    USERNAME("Username"),
    PASSWORD("Password"),
    OTP("OTP")
  }

  companion object {

    // Extra helper methods
    fun createOtpField(otp: String): FieldItem {
      return FieldItem(ItemType.OTP.type, otp, ActionType.COPY)
    }

    fun createPasswordField(password: String): FieldItem {
      return FieldItem(ItemType.PASSWORD.type, password, ActionType.HIDE)
    }

    fun createUsernameField(username: String): FieldItem {
      return FieldItem(ItemType.USERNAME.type, username, ActionType.COPY)
    }
  }
}
