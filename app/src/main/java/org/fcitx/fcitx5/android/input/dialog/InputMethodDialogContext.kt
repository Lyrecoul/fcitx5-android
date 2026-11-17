/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.dialog

import android.content.Context
import android.view.ContextThemeWrapper
import com.google.android.material.color.DynamicColors
import org.fcitx.fcitx5.android.R

internal fun Context.inputMethodDialogContext(): Context =
    // Build per dialog so system night mode and wallpaper colors are not cached by the keyboard.
    DynamicColors.wrapContextIfAvailable(ContextThemeWrapper(this, R.style.Theme_InputMethodDialog))
