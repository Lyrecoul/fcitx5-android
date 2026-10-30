/*
 * SPDX-License-Identifier: GPL-3.0-only
 * SPDX-FileCopyrightText: Copyright rebron1900 and fcitx5-enhanced contributors
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable

/** The module is an effect applied to the selected theme, not a separate color palette. */
val Theme.isEnhancedGlass: Boolean
    get() = ThemeManager.prefs.enhancedGlass.getValue()

/** Same tint calculation as fcitx5-enhanced's FrostedGlassHelper. */
fun Theme.glassBackground(): Drawable {
    val alpha = ThemeManager.prefs.glassBackgroundAlpha.getValue()
    val color = keyBackgroundColor
    val top = if (color != 0) color else if (isDark) 0xff1e2332.toInt() else 0xfff5f8ff.toInt()
    val bottom = if (color != 0) Color.rgb(
        (Color.red(color) - 15).coerceAtLeast(0),
        (Color.green(color) - 15).coerceAtLeast(0),
        (Color.blue(color) - 15).coerceAtLeast(0)
    ) else if (isDark) 0xff141928.toInt() else 0xffe1e6f5.toInt()
    return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
        (top and 0x00ffffff) or (alpha shl 24),
        (bottom and 0x00ffffff) or ((if (color != 0) (alpha + 20).coerceAtMost(255) else alpha) shl 24)
    ))
}
