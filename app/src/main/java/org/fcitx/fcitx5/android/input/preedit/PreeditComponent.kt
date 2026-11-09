/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.preedit

import android.graphics.drawable.GradientDrawable
import android.view.View
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.isEnhancedGlass
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.dependency.context
import org.fcitx.fcitx5.android.input.dependency.theme
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.UniqueComponent
import org.mechdancer.dependency.manager.ManagedHandler
import org.mechdancer.dependency.manager.managedHandler
import splitties.dimensions.dp
import splitties.views.backgroundColor
import splitties.views.horizontalPadding

class PreeditComponent : UniqueComponent<PreeditComponent>(), Dependent, InputBroadcastReceiver,
    ManagedHandler by managedHandler() {

    private val context by manager.context()
    private val theme by manager.theme()

    val roundedBackground: Boolean
        get() = theme.isEnhancedGlass && ThemeManager.prefs.glassCornerRadius.getValue() > 0

    val ui by lazy {
        val keyBorder = ThemeManager.prefs.keyBorder.getValue()
        val bkgColor =
            if (!keyBorder && theme is Theme.Builtin) theme.barColor else theme.backgroundColor
        PreeditUi(context, theme, setupTextView = {
            if (!roundedBackground) backgroundColor = bkgColor
            horizontalPadding = dp(8)
        }, animateCapsule = roundedBackground).apply {
            if (roundedBackground) {
                // One surface for both lines; child fills would cover its rounded corners.
                root.background = GradientDrawable().apply {
                    setColor(theme.barColor.takeUnless { it == 0 } ?: 0xff303030.toInt())
                    cornerRadius = context.dp(ThemeManager.prefs.glassCornerRadius.getValue().toFloat())
                }
            }
            // TODO make it customizable
            root.alpha = 0.8f
            root.visibility = View.INVISIBLE
        }
    }

    override fun onInputPanelUpdate(data: FcitxEvent.InputPanelEvent.Data) {
        ui.update(data)
        if (roundedBackground) {
            (ui.root as PreeditCapsuleLayout).setContentVisible(ui.visible)
        } else {
            ui.root.visibility = if (ui.visible) View.VISIBLE else View.INVISIBLE
        }
    }
}
