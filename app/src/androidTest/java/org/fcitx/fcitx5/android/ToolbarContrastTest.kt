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
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.bar.ui.ToolButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import splitties.dimensions.dp

class ToolbarContrastTest {
    @Test
    fun iconsRemainReadableOnSolidImageAndGlassBackgrounds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            val prefs = ThemeManager.prefs
            val glass = prefs.enhancedGlass.getValue()
            val border = prefs.keyBorder.getValue()
            try {
                val context = ContextThemeWrapper(instrumentation.targetContext,
                    R.style.Theme_InputViewTheme)
                val light = ThemePreset.PixelLight.deriveCustomNoBackground("contrast")
                for (enhanced in listOf(false, true)) {
                    prefs.enhancedGlass.setValue(enhanced)
                    for (keyBorder in listOf(false, true)) {
                        prefs.keyBorder.setValue(keyBorder)
                        val themes = listOf(
                            light.copy(altKeyTextColor = Color.WHITE),
                            light.copy(backgroundColor = Color.BLACK, keyboardColor = Color.BLACK,
                                barColor = Color.BLACK, altKeyTextColor = Color.BLACK),
                            light.copy(barColor = 0xff808080.toInt(),
                                backgroundColor = 0xff808080.toInt(),
                                altKeyTextColor = 0x40808080),
                            ThemePreset.PixelDark.deriveCustomBackground("image", "missing", "missing")
                        )
                        for (theme in themes) {
                            val backgrounds = if (enhanced || (keyBorder && theme.backgroundImage != null))
                                listOf(Color.BLACK, Color.WHITE, Color.RED, Color.GREEN)
                            else listOf(if (keyBorder) theme.backgroundColor else theme.barColor)
                            for (background in backgrounds) {
                                val button = ToolButton(context,
                                    R.drawable.ic_baseline_more_horiz_24, theme)
                                val size = context.dp(40)
                                button.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY))
                                button.layout(0, 0, size, size)
                                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                                try {
                                    val canvas = Canvas(bitmap)
                                    button.draw(canvas)
                                    assertEquals("Button padding must stay transparent", 0,
                                        Color.alpha(bitmap.getPixel(size / 2, context.dp(3))))
                                    assertEquals("No filled backing beside the icon", 0,
                                        Color.alpha(bitmap.getPixel(context.dp(5), size / 2)))
                                    canvas.drawColor(background)
                                    button.draw(canvas)
                                    val icon = bitmap.getPixel(size / 2, size / 2)
                                    val contrast = if (enhanced ||
                                        (keyBorder && theme.backgroundImage != null)) {
                                        // The opposite-color edge, rather than a filled plate,
                                        // separates the glyph from an unknown background.
                                        var strongestEdge = 1.0
                                        for (dy in 1..context.dp(4)) {
                                            strongestEdge = maxOf(strongestEdge,
                                                ColorUtils.calculateContrast(icon,
                                                    bitmap.getPixel(size / 2, size / 2 + dy)))
                                        }
                                        strongestEdge
                                    } else ColorUtils.calculateContrast(icon,
                                        bitmap.getPixel(size / 2, size / 2 + context.dp(5)))
                                    assertTrue("Icon contrast $contrast: glass=$enhanced border=$keyBorder " +
                                        "theme=$theme background=$background", contrast >= 4.5)
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                failure = error
            } finally {
                prefs.enhancedGlass.setValue(glass)
                prefs.keyBorder.setValue(border)
            }
        }
        failure?.let { throw it }
    }
}
