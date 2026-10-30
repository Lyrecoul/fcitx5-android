/*
 * SPDX-License-Identifier: GPL-3.0-only
 * SPDX-FileCopyrightText: Copyright rebron1900 and fcitx5-enhanced contributors
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import org.fcitx.fcitx5.android.data.theme.ThemeManager

/** Match KeyEffectsHelper's alpha handling without reflection or traversing the view tree. */
fun Drawable.applyGlassAlpha() {
    val alpha = ThemeManager.prefs.glassKeyAlpha.getValue()
    if (alpha > 250) return
    setAlpha(alpha)
    when (this) {
        is InsetDrawable -> drawable?.applyGlassAlpha()
        is LayerDrawable -> for (index in 0 until numberOfLayers) getDrawable(index).applyGlassAlpha()
    }
}

fun glassKeyBorder(
    dark: Boolean, radius: Float, density: Float, hMargin: Int, vMargin: Int, oval: Boolean = false
): Drawable {
    val color = if (dark) 0x33ffffff else 0x88ffffff.toInt()
    return GlassBorderDrawable(
        0, color, color, radius, 0.8f * density * if (oval) 1.8f else 1f,
        GlassBorderDrawable.MODE_DIAGONAL, oval, hMargin, vMargin
    )
}

fun glassKeyboardBorder(dark: Boolean, radius: Float, density: Float): Drawable {
    val color = if (dark) 0x33ffffff else 0x99ffffff.toInt()
    return GlassBorderDrawable(0, color, color, radius, density, GlassBorderDrawable.MODE_KEYBOARD)
}
