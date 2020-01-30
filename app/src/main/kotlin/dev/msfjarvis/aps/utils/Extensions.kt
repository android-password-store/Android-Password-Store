/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.utils

import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import dev.msfjarvis.aps.R

fun FragmentManager.performTransaction(destinationFragment: Fragment, @IdRes containerViewId: Int = android.R.id.content) {
  this.commit {
    beginTransaction()
    setCustomAnimations(
      R.animator.slide_in_left,
      R.animator.slide_out_left,
      R.animator.slide_in_right,
      R.animator.slide_out_right)
    replace(containerViewId, destinationFragment)
  }
}

fun FragmentManager.performTransactionWithBackStack(destinationFragment: Fragment, @IdRes containerViewId: Int = android.R.id.content) {
  this.commit {
    beginTransaction()
    addToBackStack(destinationFragment.tag)
    setCustomAnimations(
      R.animator.slide_in_left,
      R.animator.slide_out_left,
      R.animator.slide_in_right,
      R.animator.slide_out_right)
    replace(containerViewId, destinationFragment)
  }
}

fun FragmentManager.performSharedElementTransaction(destinationFragment: Fragment, views: List<View>, @IdRes containerViewId: Int = android.R.id.content) {
  this.commit {
    beginTransaction()
    for (view in views) {
      addSharedElement(view, view.transitionName)
    }
    addToBackStack(destinationFragment.tag)
    replace(containerViewId, destinationFragment)
  }
}
