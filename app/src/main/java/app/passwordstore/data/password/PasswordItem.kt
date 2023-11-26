/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.data.password

import android.content.Context
import android.content.Intent
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.ui.crypto.BasePGPActivity
import app.passwordstore.ui.main.LaunchActivity
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

data class PasswordItem(
  val parent: PasswordItem? = null,
  val type: Char,
  val file: Path,
  val rootDir: Path
) : Comparable<PasswordItem> {

  val name = file.nameWithoutExtension

  val fullPathToParent = file.relativeTo(rootDir).parent.pathString

  val longName =
    BasePGPActivity.getLongName(fullPathToParent, rootDir.absolutePathString(), toString())

  override fun equals(other: Any?): Boolean {
    return (other is PasswordItem) && (other.file == file)
  }

  override fun compareTo(other: PasswordItem): Int {
    return (type + name).compareTo(other.type + other.name, ignoreCase = true)
  }

  override fun toString(): String {
    return name
  }

  override fun hashCode(): Int {
    return 0
  }

  /** Creates an [Intent] to launch this [PasswordItem] through the authentication process. */
  fun createAuthEnabledIntent(context: Context): Intent {
    val intent = Intent(context, LaunchActivity::class.java)
    intent.putExtra("NAME", toString())
    intent.putExtra("FILE_PATH", file.absolutePathString())
    intent.putExtra("REPO_PATH", PasswordRepository.getRepositoryDirectory().absolutePathString())
    intent.action = LaunchActivity.ACTION_DECRYPT_PASS
    return intent
  }

  companion object {

    const val TYPE_CATEGORY = 'c'
    const val TYPE_PASSWORD = 'p'

    @JvmStatic
    fun newCategory(path: Path, parent: PasswordItem, rootDir: Path): PasswordItem {
      return PasswordItem(parent, TYPE_CATEGORY, path, rootDir)
    }

    @JvmStatic
    fun newCategory(path: Path, rootDir: Path): PasswordItem {
      return PasswordItem(null, TYPE_CATEGORY, path, rootDir)
    }

    @JvmStatic
    fun newPassword(path: Path, parent: PasswordItem, rootDir: Path): PasswordItem {
      return PasswordItem(parent, TYPE_PASSWORD, path, rootDir)
    }

    @JvmStatic
    fun newPassword(path: Path, rootDir: Path): PasswordItem {
      return PasswordItem(null, TYPE_PASSWORD, path, rootDir)
    }
  }
}
