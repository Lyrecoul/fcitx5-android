/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable

/** Glass reflections inspired by rebron1900/fcitx5-enhanced, drawn inside key margins. */
fun glassKeyBackgroundDrawable(
    color: Int,
    dark: Boolean,
    radius: Float,
    strokeWidth: Float,
    hMargin: Int,
    vMargin: Int
): Drawable = object : LayerDrawable(arrayOf(
    insetRadiusDrawable(hMargin, vMargin, radius, color),
    object : Drawable() {
        private val rect = RectF()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }

        override fun onBoundsChange(bounds: Rect) {
            val half = strokeWidth / 2
            rect.set(
                bounds.left + hMargin + half, bounds.top + vMargin + half,
                bounds.right - hMargin - half, bounds.bottom - vMargin - half
            )
            val reflection = if (dark) Color.WHITE else 0xff7a8599.toInt()
            paint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(reflection, Color.TRANSPARENT, reflection),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            paint.alpha = if (dark) 100 else 90
        }

        override fun draw(canvas: Canvas) {
            if (!rect.isEmpty) canvas.drawRoundRect(rect, radius, radius, paint)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
)) {
    override fun getPadding(padding: Rect): Boolean {
        padding.setEmpty()
        return false
    }
}
