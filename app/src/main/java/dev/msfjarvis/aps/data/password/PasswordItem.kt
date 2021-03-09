/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.data.password

import dev.msfjarvis.aps.ui.crypto.BasePgpActivity
import java.io.File

data class PasswordItem(
  val name: String,
  val parent: PasswordItem? = null,
  val type: Char,
  val file: File,
  val rootDir: File
) : Comparable<PasswordItem> {

  val fullPathToParent = file.absolutePath.replace(rootDir.absolutePath, "").replace(file.name, "")

  val longName = BasePgpActivity.getLongName(fullPathToParent, rootDir.absolutePath, toString())

  override fun equals(other: Any?): Boolean {
    return (other is PasswordItem) && (other.file == file)
  }

  override fun compareTo(other: PasswordItem): Int {
    return (type + name).compareTo(other.type + other.name, ignoreCase = true)
  }

  override fun toString(): String {
    return name.replace("\\.gpg$".toRegex(), "")
  }

  override fun hashCode(): Int {
    return 0
  }

  companion object {

    const val TYPE_CATEGORY = 'c'
    const val TYPE_PASSWORD = 'p'

    @JvmStatic
    fun newCategory(name: String, file: File, parent: PasswordItem, rootDir: File): PasswordItem {
      return PasswordItem(name, parent, TYPE_CATEGORY, file, rootDir)
    }

    @JvmStatic
    fun newCategory(name: String, file: File, rootDir: File): PasswordItem {
      return PasswordItem(name, null, TYPE_CATEGORY, file, rootDir)
    }

    @JvmStatic
    fun newPassword(name: String, file: File, parent: PasswordItem, rootDir: File): PasswordItem {
      return PasswordItem(name, parent, TYPE_PASSWORD, file, rootDir)
    }

    @JvmStatic
    fun newPassword(name: String, file: File, rootDir: File): PasswordItem {
      return PasswordItem(name, null, TYPE_PASSWORD, file, rootDir)
    }
  }
}
