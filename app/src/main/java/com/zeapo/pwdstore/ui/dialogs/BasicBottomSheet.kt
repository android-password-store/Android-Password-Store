/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.ui.dialogs

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.BasicBottomSheetBinding
import com.zeapo.pwdstore.utils.resolveAttribute
import com.zeapo.pwdstore.utils.viewBinding

/**
 * [BottomSheetDialogFragment] that exposes a simple [androidx.appcompat.app.AlertDialog] like
 * API through [Builder] to create a similar UI, just at the bottom of the screen.
 */
class BasicBottomSheet private constructor(
    val title: String,
    val message: String,
    val positiveButtonClickListener: View.OnClickListener?,
    val negativeButtonClickListener: View.OnClickListener?,
) : BottomSheetDialogFragment() {

    private val binding by viewBinding(BasicBottomSheetBinding::bind)

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
        return layoutInflater.inflate(R.layout.basic_bottom_sheet, container, false)
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
                binding.bottomSheetTitle.text = title
                binding.bottomSheetMessage.text = message
                if (positiveButtonClickListener != null) {
                    binding.bottomSheetOkButton.isVisible = true
                    binding.bottomSheetOkButton.setOnClickListener {
                        positiveButtonClickListener.onClick(it)
                        dismiss()
                    }
                }
                if (negativeButtonClickListener != null) {
                    binding.bottomSheetCancelButton.isVisible = true
                    binding.bottomSheetCancelButton.setOnClickListener {
                        negativeButtonClickListener.onClick(it)
                        dismiss()
                    }
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

    class Builder(val context: Context) {

        private var title: String? = null
        private var message: String? = null
        private var positiveButtonClickListener: View.OnClickListener? = null
        private var negativeButtonClickListener: View.OnClickListener? = null

        fun setTitleRes(@StringRes titleRes: Int): Builder {
            this.title = context.resources.getString(titleRes)
            return this
        }

        fun setTitle(title: String): Builder {
            this.title = title
            return this
        }

        fun setMessageRes(@StringRes messageRes: Int): Builder {
            this.message = context.resources.getString(messageRes)
            return this
        }

        fun setMessage(message: String): Builder {
            this.message = message
            return this
        }

        fun setPositiveButtonClickListener(listener: View.OnClickListener): Builder {
            this.positiveButtonClickListener = listener
            return this
        }

        fun setNegativeButtonClickListener(listener: View.OnClickListener): Builder {
            this.negativeButtonClickListener = listener
            return this
        }

        fun build(): BasicBottomSheet {
            require(title != null) { "Title needs to be set" }
            require(message != null) { "Message needs to be set" }
            return BasicBottomSheet(
                title!!,
                message!!,
                positiveButtonClickListener,
                negativeButtonClickListener
            )
        }
    }
}
