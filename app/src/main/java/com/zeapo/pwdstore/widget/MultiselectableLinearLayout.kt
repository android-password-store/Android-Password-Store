/*
 * Copyright © 2017-2018 WireGuard LLC.
 * Copyright © 2018-2019 Harsh Shandilya <msfjarvis@gmail.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.zeapo.pwdstore.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.zeapo.pwdstore.R

class MultiselectableLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    private var multiselected: Boolean = false

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        if (multiselected) {
            val drawableState = super.onCreateDrawableState(extraSpace + 1)
            View.mergeDrawableStates(drawableState, STATE_MULTISELECTED)
            return drawableState
        }
        return super.onCreateDrawableState(extraSpace)
    }

    fun setMultiSelected(on: Boolean) {
        if (!multiselected) {
            multiselected = true
            refreshDrawableState()
        }
        isActivated = on
    }

    fun setSingleSelected(on: Boolean) {
        if (multiselected) {
            multiselected = false
            refreshDrawableState()
        }
        isActivated = on
    }

    companion object {
        private val STATE_MULTISELECTED = intArrayOf(R.attr.state_multiselected)
    }
}
