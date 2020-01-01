/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.utils

import android.view.View
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.passwordstore.android.R

fun FragmentManager.performTransaction(destinationFragment: Fragment, @IdRes containerViewId: Int = android.R.id.content) {
    this.beginTransaction()
            .setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_left, R.animator.slide_in_right, R.animator.slide_out_right)
            .replace(containerViewId, destinationFragment)
            .commit()
}

fun FragmentManager.performTransactionWithBackStack(destinationFragment: Fragment, @IdRes containerViewId: Int = android.R.id.content) {
    this.beginTransaction()
            .addToBackStack(destinationFragment.tag)
            .setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_left, R.animator.slide_in_right, R.animator.slide_out_right)
            .replace(containerViewId, destinationFragment)
            .commit()
}

fun FragmentManager.performSharedElementTransaction(destinationFragment: Fragment, views: List<View>, @IdRes containerViewId: Int = android.R.id.content) {
    this.beginTransaction().apply {
        for (view in views) {
            addSharedElement(view, view.transitionName)
        }
        addToBackStack(destinationFragment.tag)
        replace(containerViewId, destinationFragment)
    }.commit()
}
