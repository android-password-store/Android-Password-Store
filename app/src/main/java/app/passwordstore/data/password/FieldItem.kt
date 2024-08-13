/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.data.password

import app.passwordstore.data.passfile.Totp

class FieldItem
private constructor(
  val type: ItemType,
  val label: String,
  val value: String,
  val action: ActionType,
) {
  enum class ActionType {
    COPY,
    HIDE,
  }

  enum class ItemType() {
    USERNAME,
    PASSWORD,
    OTP,
    FREEFORM,
  }

  companion object {
    fun createOtpField(label: String, totp: Totp): FieldItem {
      return FieldItem(
        ItemType.OTP,
        label.format(totp.remainingTime.inWholeSeconds),
        totp.value,
        ActionType.COPY,
      )
    }

    fun createPasswordField(label: String, password: String): FieldItem {
      return FieldItem(ItemType.PASSWORD, label, password, ActionType.HIDE)
    }

    fun createUsernameField(label: String, username: String): FieldItem {
      return FieldItem(ItemType.USERNAME, label, username, ActionType.COPY)
    }

    fun createFreeformField(label: String, content: String): FieldItem {
      return FieldItem(ItemType.FREEFORM, label, content, ActionType.COPY)
    }
  }
}
