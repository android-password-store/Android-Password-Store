/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.widget.fab

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.animation.addListener
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.transformation.ExpandableTransformationBehavior
import java.util.ArrayList

/**
 * Taken from Mao Yufeng's excellent example at https://git.io/Jvml9, all credits to him for this.
 * It's hard to create per-file copyright rules for Spotless so I'm choosing to credit him here.
 */
class EmitExpandableTransformationBehavior @JvmOverloads constructor(
    context: Context? = null,
    attrs: AttributeSet? = null
) : ExpandableTransformationBehavior(context, attrs) {

    companion object {
        private const val EXPAND_DELAY = 60L
        private const val EXPAND_DURATION = 150L
        private const val COLLAPSE_DELAY = 60L
        private const val COLLAPSE_DURATION = 150L
    }

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        return dependency is FloatingActionButton && child is ViewGroup
    }

    override fun onCreateExpandedStateChangeAnimation(
        dependency: View,
        child: View,
        expanded: Boolean,
        isAnimating: Boolean
    ): AnimatorSet {

        if (child !is ViewGroup) {
            return AnimatorSet()
        }

        val animations = ArrayList<Animator>()

        if (expanded) {
            createExpandAnimation(child, isAnimating, animations)
        } else {
            createCollapseAnimation(child, animations)
        }

        val set = AnimatorSet()
        set.playTogether(animations)
        set.addListener(
            onStart = {
                if (expanded) {
                    child.isVisible = true
                }
            },
            onEnd = {
                if (!expanded) {
                    child.isInvisible = true
                }
            }
        )
        return set
    }

    private fun createExpandAnimation(
        child: ViewGroup,
        currentlyAnimating: Boolean,
        animations: MutableList<Animator>
    ) {
        if (!currentlyAnimating) {
            child.children.forEach {
                it.alpha = 0f
                it.scaleX = 0.4f
                it.scaleY = 0.4f
            }
        }
        val delays = List(child.childCount) {
            it * EXPAND_DELAY
        }.reversed().asSequence()
        val scaleXHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f)
        val scaleYHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f)
        val alphaHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 1f)
        val animators = child.children.zip(delays) { view, delay ->
            ObjectAnimator.ofPropertyValuesHolder(
                view,
                scaleXHolder,
                scaleYHolder,
                alphaHolder
            ).apply {
                duration = EXPAND_DURATION
                startDelay = delay
            }
        }.toList()
        val animatorSet = AnimatorSet().apply {
            playTogether(animators)
        }
        animations.add(animatorSet)
    }

    private fun createCollapseAnimation(
        child: ViewGroup,
        animations: MutableList<Animator>
    ) {
        val delays = List(child.childCount) {
            it * COLLAPSE_DELAY
        }.asSequence()
        val scaleXHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.4f)
        val scaleYHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.4f)
        val alphaHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 0f)
        val animators = child.children.zip(delays) { view, delay ->
            ObjectAnimator.ofPropertyValuesHolder(
                view,
                scaleXHolder,
                scaleYHolder,
                alphaHolder
            ).apply {
                duration = COLLAPSE_DURATION
                startDelay = delay
            }
        }.toList()
        val animatorSet = AnimatorSet().apply {
            playTogether(animators)
        }
        animations.add(animatorSet)
    }
}
