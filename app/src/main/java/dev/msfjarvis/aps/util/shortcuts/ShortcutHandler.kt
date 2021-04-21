/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.data.password.PasswordItem
import javax.inject.Inject

@Reusable
class ShortcutHandler
@Inject
constructor(
  @ApplicationContext val context: Context,
) {

  private companion object {

    // The max shortcut count from the system is set to 15 for some godforsaken reason, which
    // makes zero sense and is why our update logic just never worked. Capping it at 4 which is
    // what most launchers seem to have agreed upon is the only reasonable solution.
    private const val MAX_SHORTCUT_COUNT = 4
  }

  /**
   * Creates a
   * [dynamic shortcut](https://developer.android.com/guide/topics/ui/shortcuts/creating-shortcuts#dynamic)
   * that shows up with the app icon on long press. The list of items is capped to
   * [MAX_SHORTCUT_COUNT] and older items are removed by a simple LRU sweep.
   */
  fun addDynamicShortcut(item: PasswordItem, intent: Intent) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
    val shortcutManager: ShortcutManager = context.getSystemService() ?: return
    val shortcut = buildShortcut(item, intent)
    val shortcuts = shortcutManager.dynamicShortcuts
    // If we're above or equal to the maximum shortcuts allowed, drop the last item.
    if (shortcuts.size >= MAX_SHORTCUT_COUNT) {
      shortcuts.removeLast()
    }
    // Reverse the list so we can append our new shortcut at the 'end'.
    shortcuts.reverse()
    shortcuts.add(shortcut)
    // Reverse it again, so the previous items are now in the correct order and our new item
    // is at the front like it's supposed to.
    shortcuts.reverse()
    // Write back the new shortcuts.
    shortcutManager.dynamicShortcuts = shortcuts.map(::rebuildShortcut)
  }

  /** Creates a [ShortcutInfo] from [item] and assigns [intent] to it. */
  @RequiresApi(Build.VERSION_CODES.N_MR1)
  private fun buildShortcut(item: PasswordItem, intent: Intent): ShortcutInfo {
    return ShortcutInfo.Builder(context, item.fullPathToParent)
      .setShortLabel(item.toString())
      .setLongLabel(item.fullPathToParent + item.toString())
      .setIcon(Icon.createWithResource(context, R.drawable.ic_lock_open_24px))
      .setIntent(intent)
      .build()
  }

  /**
   * Takes an existing [ShortcutInfo] and builds a fresh instance of [ShortcutInfo] with the same
   * data, which ensures that the get/set dance in [addDynamicShortcut] does not cause invalidation
   * of icon assets, resulting in invisible icons in all but the newest launcher shortcut.
   */
  @RequiresApi(Build.VERSION_CODES.N_MR1)
  private fun rebuildShortcut(shortcut: ShortcutInfo): ShortcutInfo {
    // Non-null assertions are fine since we know these values aren't null.
    return ShortcutInfo.Builder(context, shortcut.id)
      .setShortLabel(shortcut.shortLabel!!)
      .setLongLabel(shortcut.longLabel!!)
      .setIcon(Icon.createWithResource(context, R.drawable.ic_lock_open_24px))
      .setIntent(shortcut.intent!!)
      .build()
  }
}
