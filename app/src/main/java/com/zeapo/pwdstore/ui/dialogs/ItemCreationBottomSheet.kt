/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.dialogs

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zeapo.pwdstore.PasswordFragment.Companion.ACTION_FOLDER
import com.zeapo.pwdstore.PasswordFragment.Companion.ACTION_KEY
import com.zeapo.pwdstore.PasswordFragment.Companion.ACTION_PASSWORD
import com.zeapo.pwdstore.PasswordFragment.Companion.ITEM_CREATION_REQUEST_KEY
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.resolveAttribute

class ItemCreationBottomSheet : BottomSheetDialogFragment() {

    private var behavior: BottomSheetBehavior<FrameLayout>? = null
    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                dismiss()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (savedInstanceState != null) dismiss()
        return inflater.inflate(R.layout.item_create_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val dialog = dialog as BottomSheetDialog? ?: return
                behavior = dialog.behavior
                behavior?.apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    peekHeight = 0
                    addBottomSheetCallback(bottomSheetCallback)
                }
                dialog.findViewById<View>(R.id.create_folder)?.setOnClickListener {
                    setFragmentResult(ITEM_CREATION_REQUEST_KEY, bundleOf(ACTION_KEY to ACTION_FOLDER))
                    dismiss()
                }
                dialog.findViewById<View>(R.id.create_password)?.setOnClickListener {
                    setFragmentResult(ITEM_CREATION_REQUEST_KEY, bundleOf(ACTION_KEY to ACTION_PASSWORD))
                    dismiss()
                }
            }
        })
        val gradientDrawable = GradientDrawable().apply {
            setColor(requireContext().resolveAttribute(android.R.attr.windowBackground))
        }
        view.background = gradientDrawable
    }

    override fun dismiss() {
        super.dismiss()
        behavior?.removeBottomSheetCallback(bottomSheetCallback)
    }
}
