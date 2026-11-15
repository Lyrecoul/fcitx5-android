/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.keyboard.TextKeyView
import org.junit.Assert.assertEquals
import org.fcitx.fcitx5.android.ui.main.MainActivity
import org.junit.Test
import org.junit.Rule

class KeyPreviewPositionTest {
    @get:Rule
    val activity = ActivityTestRule(MainActivity::class.java)

    @Test
    fun previewBoundsFollowParentMovementWithoutRelayingOutTheKey() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_InputViewTheme)
                val keyboard = FrameLayout(context)
                val key = TextKeyView(context, ThemePreset.PixelLight, KeyDef.Appearance.Text("d", 20f))
                val decor = activity.activity.window.decorView as ViewGroup
                decor.addView(keyboard, ViewGroup.LayoutParams(400, 300))
                keyboard.addView(key, FrameLayout.LayoutParams(100, 80))
                keyboard.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
                keyboard.layout(0, 0, 400, 300)
                val before = Rect(key.bounds)
                // Dragging moves the ancestor, not the key's local layout.
                keyboard.offsetLeftAndRight(180)
                keyboard.offsetTopAndBottom(90)
                try {
                    val expected = Rect(before).apply { offset(180, 90) }
                    assertEquals("Preview must follow the key after dragging", expected, key.bounds)
                } finally {
                    decor.removeView(keyboard)
                }
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }
}
