/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.preedit.PreeditComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.wrapToUniqueComponent
import org.mechdancer.dependency.plusAssign

class GlassPreeditTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun preeditHasOneRoundedSurfaceAcrossTextUpdatesAndThemes() = onMain {
        withPreferences {
            ThemeManager.prefs.enhancedGlass.setValue(true)
            ThemeManager.prefs.glassCornerRadius.setValue(20)
            for (theme in listOf(ThemePreset.PixelLight, ThemePreset.PixelDark,
                ThemePreset.PixelDark.copy(barColor = 0))) {
                withPreedit(theme) { component ->
                    component.onInputPanelUpdate(panel("ni hao", "help"))
                    val root = component.ui.root as ViewGroup
                    measure(root)
                    val pixels = draw(root)
                    assertEquals("The preedit corner must not be a rectangular text fill", 0,
                        Color.alpha(pixels.getPixel(0, 0)))
                    val center = Color.alpha(pixels.getPixel(root.width / 2, root.height / 2))
                    assertTrue("The shared preedit surface must retain its theme fill", center > 0)
                    pixels.recycle()
                    root.children.forEach { assertEquals(null, it.background) }
                    val shortWidth = root.width
                    component.onInputPanelUpdate(panel("ni hao shi jie", ""))
                    measure(root)
                    assertTrue(root.width > shortWidth)
                    assertEquals(View.VISIBLE, root.visibility)
                    component.onInputPanelUpdate(FcitxEvent.InputPanelEvent.Data())
                    assertEquals(View.INVISIBLE, root.visibility)
                    component.onInputPanelUpdate(panel("ni", ""))
                    measure(root)
                    val restored = draw(root)
                    assertEquals(0, Color.alpha(restored.getPixel(0, 0)))
                    restored.recycle()
                }
            }
        }
    }

    @Test
    fun disabledEnhancementOrZeroCornerPreservesOriginalTextBackgrounds() = onMain {
        withPreferences {
            for ((enabled, radius) in listOf(false to 20, true to 0)) {
                ThemeManager.prefs.enhancedGlass.setValue(enabled)
                ThemeManager.prefs.glassCornerRadius.setValue(radius)
                ThemeManager.prefs.keyBorder.setValue(false)
                withPreedit(ThemePreset.PixelDark) { component ->
                    component.onInputPanelUpdate(panel("ni hao", ""))
                    val root = component.ui.root as ViewGroup
                    assertEquals(null, root.background)
                    val text = root.children.filterIsInstance<TextView>().first()
                    text.background.setBounds(0, 0, 8, 8)
                    val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                    text.background.draw(Canvas(bitmap))
                    assertEquals(ThemePreset.PixelDark.barColor, bitmap.getPixel(0, 0))
                    bitmap.recycle()
                }
            }
        }
    }

    private fun panel(preedit: String, auxiliary: String) = FcitxEvent.InputPanelEvent.Data(
        FormattedText(arrayOf(preedit), intArrayOf(0), preedit.length), FormattedText.Empty,
        FormattedText(arrayOf(auxiliary), intArrayOf(0), -1), emptyArray()
    )

    private fun withPreedit(theme: Theme, block: (PreeditComponent) -> Unit) {
        val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_InputViewTheme)
        val scope = DynamicScope()
        val component = PreeditComponent()
        scope += context.wrapToUniqueComponent()
        scope += theme.wrapToUniqueComponent()
        scope += component
        try { block(component) } finally { scope.clear() }
    }

    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun draw(view: View) = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
        view.draw(Canvas(it))
    }

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private fun withPreferences(block: () -> Unit) {
        val prefs = ThemeManager.prefs
        val enabled = prefs.enhancedGlass.getValue()
        val radius = prefs.glassCornerRadius.getValue()
        val border = prefs.keyBorder.getValue()
        try { block() } finally {
            prefs.enhancedGlass.setValue(enabled)
            prefs.glassCornerRadius.setValue(radius)
            prefs.keyBorder.setValue(border)
        }
    }
}
