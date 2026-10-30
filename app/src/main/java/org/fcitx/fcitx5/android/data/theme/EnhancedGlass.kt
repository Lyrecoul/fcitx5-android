/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.data.theme

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils

/** Native glass skin inspired by rebron1900/fcitx5-enhanced. */
val Theme.isEnhancedGlass: Boolean
    get() = this is Theme.Builtin && (name == "EnhancedGlassLight" || name == "EnhancedGlassDark")

fun Theme.glassBackground(translucent: Boolean = false): Drawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(
        ColorUtils.setAlphaComponent(backgroundColor, if (translucent) 60 else 255),
        ColorUtils.setAlphaComponent(keyboardColor, if (translucent) 80 else 255)
    )
)
