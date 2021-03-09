/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.util

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

class OnOffItemAnimator : DefaultItemAnimator() {

    var isEnabled: Boolean = true
        set(value) {
            // Defer update until no animation is running anymore.
            isRunning { field = value }
        }

    private fun dontAnimate(viewHolder: RecyclerView.ViewHolder): Boolean {
        dispatchAnimationFinished(viewHolder)
        return false
    }

    override fun animateAppearance(
        viewHolder: RecyclerView.ViewHolder,
        preLayoutInfo: ItemHolderInfo?,
        postLayoutInfo: ItemHolderInfo
    ): Boolean {
        return if (isEnabled) {
            super.animateAppearance(viewHolder, preLayoutInfo, postLayoutInfo)
        } else {
            dontAnimate(viewHolder)
        }
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder,
        preInfo: ItemHolderInfo,
        postInfo: ItemHolderInfo
    ): Boolean {
        return if (isEnabled) {
            super.animateChange(oldHolder, newHolder, preInfo, postInfo)
        } else {
            dontAnimate(oldHolder)
        }
    }

    override fun animateDisappearance(
        viewHolder: RecyclerView.ViewHolder,
        preLayoutInfo: ItemHolderInfo,
        postLayoutInfo: ItemHolderInfo?
    ): Boolean {
        return if (isEnabled) {
            super.animateDisappearance(viewHolder, preLayoutInfo, postLayoutInfo)
        } else {
            dontAnimate(viewHolder)
        }
    }

    override fun animatePersistence(
        viewHolder: RecyclerView.ViewHolder,
        preInfo: ItemHolderInfo,
        postInfo: ItemHolderInfo
    ): Boolean {
        return if (isEnabled) {
            super.animatePersistence(viewHolder, preInfo, postInfo)
        } else {
            dontAnimate(viewHolder)
        }
    }
}
