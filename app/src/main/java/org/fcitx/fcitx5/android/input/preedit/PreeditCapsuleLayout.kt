/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.preedit

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import kotlin.math.roundToInt

/** Animate measured bounds, not scale: glyphs and corner radii keep their real size. */
internal class PreeditCapsuleLayout(context: Context) : LinearLayout(context) {
    private var populated = false
    private var targetWidth = 0
    private var targetHeight = 0
    private var surfaceWidth = 0f
    private var surfaceHeight = 0f
    private var animator: ValueAnimator? = null
    private var contentWidthSpec: Int? = null
    private var contentHeightSpec: Int? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.BOTTOM
        clipToOutline = true
    }

    fun setContentVisible(visible: Boolean) {
        populated = visible
        if (visible) visibility = VISIBLE
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // ConstraintLayout also issues exact passes for the current animated bounds.
        // Measure content against its available space, not that intermediate frame.
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
            contentWidthSpec = widthMeasureSpec
        }
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            contentHeightSpec = heightMeasureSpec
        }
        super.onMeasure(contentWidthSpec ?: widthMeasureSpec, contentHeightSpec ?: heightMeasureSpec)
        val width = if (populated) measuredWidth else 0
        val height = if (populated) measuredHeight else 0
        if (width != targetWidth || height != targetHeight) {
            animator?.removeAllListeners()
            animator?.cancel()
            animator = null
            targetWidth = width
            targetHeight = height
            if (!isAttachedToWindow) {
                surfaceWidth = width.toFloat()
                surfaceHeight = height.toFloat()
            } else {
                val fromWidth = surfaceWidth
                val fromHeight = surfaceHeight
                animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 160L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        val fraction = it.animatedValue as Float
                        surfaceWidth = fromWidth + (targetWidth - fromWidth) * fraction
                        surfaceHeight = fromHeight + (targetHeight - fromHeight) * fraction
                        requestLayout()
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (!populated) visibility = INVISIBLE
                        }
                    })
                    start()
                }
            }
        }
        setMeasuredDimension(
            resolveSize(surfaceWidth.roundToInt(), widthMeasureSpec),
            resolveSize(surfaceHeight.roundToInt(), heightMeasureSpec)
        )
    }

    override fun onDetachedFromWindow() {
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
        surfaceWidth = targetWidth.toFloat()
        surfaceHeight = targetHeight.toFloat()
        super.onDetachedFromWindow()
    }
}
