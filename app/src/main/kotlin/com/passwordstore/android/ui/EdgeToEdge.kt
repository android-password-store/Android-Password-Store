/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.ui

import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

/**
 * A utility for edge-to-edge display. It provides several features needed to make the app
 * displayed edge-to-edge on Android Q with gestural navigation.
 */
object EdgeToEdge : EdgeToEdgeImpl by RealEdgeToEdge()

private interface EdgeToEdgeImpl {

    /**
     * Configures a root view of an Activity in edge-to-edge display.
     * @param root A root view of an Activity.
     */
    fun setUpRoot(root: ViewGroup) {}

    /**
     * Configures an app bar and a toolbar for edge-to-edge display.
     * @param appBar An [AppBarLayout].
     * @param toolbar A [Toolbar] in the [appBar].
     */
    fun setUpAppBar(appBar: AppBarLayout, toolbar: Toolbar?) {}

    /**
     * Configures an app bar and a toolbar for edge-to-edge display.
     * @param appBar An [AppBarLayout].
     */
    fun setUpAppBar(appBar: AppBarLayout) {}

    /**
     * Configures a scrolling content for edge-to-edge display.
     * @param scrollingContent A scrolling ViewGroup. This is typically a RecyclerView or a
     * ScrollView. It should be as wide as the screen, and should touch the bottom edge of
     * the screen.
     * @param fab A [ExtendedFloatingActionButton] to show last item on scrolling ViewGroup above fab.
     */
    fun setUpScrollingContent(scrollingContent: ViewGroup, fab: ExtendedFloatingActionButton?) {}

    /**
     * Configures a floating action button for edge-to-edge display.
     * @param fab An [ExtendedFloatingActionButton].
     */
    fun setUpFAB(fab: ExtendedFloatingActionButton) {}
}

@RequiresApi(21)
private class RealEdgeToEdge : EdgeToEdgeImpl {

    override fun setUpRoot(root: ViewGroup) {
        root.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun setUpAppBar(appBar: AppBarLayout, toolbar: Toolbar?) {
        val originalPaddingTop = appBar.marginTop

        appBar.setOnApplyWindowInsetsListener { _, windowInsets ->
            appBar.updatePadding(top = originalPaddingTop + windowInsets.systemWindowInsetTop)
            windowInsets
        }
    }

    override fun setUpAppBar(appBar: AppBarLayout) {
        setUpAppBar(appBar, null)
    }

    override fun setUpScrollingContent(scrollingContent: ViewGroup, fab: ExtendedFloatingActionButton?) {
        val originalPaddingLeft = scrollingContent.paddingLeft
        val originalPaddingRight = scrollingContent.paddingRight
        val originalPaddingBottom = scrollingContent.paddingBottom

        val fabPaddingBottom = fab?.height ?: 0

        val originalMarginTop = scrollingContent.marginTop

        scrollingContent.setOnApplyWindowInsetsListener { _, windowInsets ->
            scrollingContent.updatePadding(
                    left = originalPaddingLeft + windowInsets.systemWindowInsetLeft,
                    right = originalPaddingRight + windowInsets.systemWindowInsetRight,
                    bottom = originalPaddingBottom + fabPaddingBottom + windowInsets.systemWindowInsetBottom
            )
            scrollingContent.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = originalMarginTop + windowInsets.systemWindowInsetTop
            }
            windowInsets
        }
    }

    override fun setUpFAB(fab: ExtendedFloatingActionButton) {
        val originalMarginLeft = fab.marginLeft
        val originalMarginRight = fab.marginRight
        val originalMarginBottom = fab.marginBottom
        fab.setOnApplyWindowInsetsListener { _, windowInsets ->
            fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = originalMarginLeft + windowInsets.systemWindowInsetLeft
                rightMargin = originalMarginRight + windowInsets.systemWindowInsetRight
                bottomMargin = originalMarginBottom + windowInsets.systemWindowInsetBottom
            }
            windowInsets
        }
    }
}
