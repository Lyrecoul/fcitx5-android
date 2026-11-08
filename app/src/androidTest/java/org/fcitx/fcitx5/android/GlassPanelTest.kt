/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.allViews
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePreset
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcaster
import org.fcitx.fcitx5.android.input.candidates.expanded.ExpandedCandidateLayout
import org.fcitx.fcitx5.android.input.clipboard.ClipboardStateMachine
import org.fcitx.fcitx5.android.input.clipboard.ClipboardUi
import org.fcitx.fcitx5.android.input.editing.TextEditingUi
import org.fcitx.fcitx5.android.input.keyboard.KeyView
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import org.fcitx.fcitx5.android.input.status.StatusAreaWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.wrapToUniqueComponent
import org.mechdancer.dependency.plusAssign

class GlassPanelTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Exercises the same view creation and replacement used by toolbar panels. */
    @Test
    fun panelBackgroundsSurviveWindowReplacementAcrossBorderOptions() = onMainThread {
        withSavedPreferences {
            for (glass in listOf(false, true)) {
                for (border in listOf(false, true)) {
                    ThemeManager.prefs.enhancedGlass.setValue(glass)
                    ThemeManager.prefs.keyBorder.setValue(border)
                    checkPanelReplacement(glass, border)
                }
            }
        }
    }

    @Test
    fun englishInputMethodUpdatesAndKeyboardReattachmentPreserveGlassKeys() = onMainThread {
        withSavedPreferences {
            ThemeManager.prefs.enhancedGlass.setValue(true)
            ThemeManager.prefs.glassKeyAlpha.setValue(90)
            for (border in listOf(false, true)) {
                for (stroke in if (border) listOf(false, true) else listOf(false)) {
                    for (glassBorder in listOf(false, true)) {
                        ThemeManager.prefs.keyBorder.setValue(border)
                        ThemeManager.prefs.keyBorderStroke.setValue(stroke)
                        ThemeManager.prefs.glassKeyBorder.setValue(glassBorder)
                        val keyboard = TextKeyboard(context(), ThemePreset.PixelDark)
                        keyboard.onAttach()
                        layout(keyboard)
                        val appearances = keyboard.allViews.filterIsInstance<KeyView>()
                            .map { it.getChildAt(0) }.toList()
                        val fills = appearances.map { drawablePixels(it.background, it) }
                        val reflections = appearances.map { drawablePixels(it.foreground, it) }
                        appearances.forEachIndexed { index, appearance ->
                            if (appearance.background != null) {
                                val fill = fills[index][appearance.width * (appearance.height / 2) +
                                    appearance.width / 2]
                                assertTrue("Key fill must remain translucent", Color.alpha(fill) in 1..254)
                            }
                            assertEquals("Glass border must match the setting", glassBorder,
                                reflections[index].any { Color.alpha(it) > 0 })
                        }

                        keyboard.onInputMethodUpdate(InputMethodEntry("pinyin"))
                        keyboard.onInputMethodUpdate(InputMethodEntry(
                            "keyboard-us", "English", "", "English", "EN", "en", "keyboard", false
                        ))
                        keyboard.onDetach()
                        keyboard.onAttach()
                        layout(keyboard)
                        assertEquals("English", keyboard.space.mainText.text.toString())
                        appearances.forEachIndexed { index, appearance ->
                            assertArrayEquals(fills[index], drawablePixels(appearance.background, appearance))
                            assertArrayEquals(reflections[index], drawablePixels(appearance.foreground, appearance))
                        }
                        keyboard.onDetach()
                    }
                }
            }
        }
    }

    private fun checkPanelReplacement(glass: Boolean, border: Boolean) {
        val context = context()
        val theme: Theme = ThemePreset.PixelDark
        val scope = DynamicScope()
        val broadcaster = InputBroadcaster()
        val windows = InputWindowManager()
        val status = StatusAreaWindow()
        scope += context.wrapToUniqueComponent()
        scope += theme.wrapToUniqueComponent()
        scope += broadcaster
        scope += windows
        scope += status
        broadcaster.onScopeSetupFinished(scope)
        val surface = FrameLayout(context).apply {
            background = theme.backgroundDrawable(border)
            addView(windows.view, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        layout(surface)
        val unobscuredPixel = renderedPixel(surface)
        try {
            val clipboard = ClipboardUi(context, theme)
            val expanded = ExpandedCandidateLayout(context, theme)
            val editing = TextEditingUi(context, theme, false, border, 4f)
            val expected = if (glass || border) Color.TRANSPARENT else theme.barColor
            val panels = listOf(clipboard.root, status.onCreateView(), expanded, editing.root)
            val keyboard = KeyboardPanel(TextKeyboard(context, theme))
            windows.attachWindow(keyboard)
            panels.forEach { panel ->
                // The wrapper isolates service actions; the production panel view and window
                // manager still own creation, attachment, removal and background drawing.
                windows.attachWindow(SurfacePanel(panel))
                assertSame(panel, windows.view.getChildAt(0))
                assertEquals(1, windows.view.childCount)
                assertEquals(if (panel === editing.root) Color.TRANSPARENT else expected,
                    backgroundPixel(panel))
                if (panel !== editing.root) {
                    layout(surface)
                    assertEquals(if (glass || border) unobscuredPixel else theme.barColor,
                        renderedPixel(surface))
                }
                if (panel === clipboard.root) {
                    for (state in ClipboardStateMachine.State.entries) {
                        clipboard.switchUiByState(state)
                        assertEquals(expected, backgroundPixel(panel))
                    }
                }
                windows.attachWindow(keyboard)
                assertSame(keyboard.root, windows.view.getChildAt(0))
                assertEquals(Color.TRANSPARENT, backgroundPixel(keyboard.root))
            }
        } finally {
            scope.clear()
        }
    }

    private class KeyboardPanel(val root: View) : InputWindow.SimpleInputWindow<KeyboardPanel>() {
        override fun onCreateView() = root
        override fun onAttached() {}
        override fun onDetached() {}
    }

    private class SurfacePanel(val root: View) : InputWindow.SimpleInputWindow<SurfacePanel>() {
        override fun onCreateView() = root
        override fun onAttached() {}
        override fun onDetached() {}
    }

    private fun context() = ContextThemeWrapper(
        instrumentation.targetContext, R.style.Theme_InputViewTheme
    )

    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1080, 720)
    }

    private fun backgroundPixel(view: View): Int {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            view.background?.apply {
                setBounds(0, 0, 8, 8)
                draw(Canvas(bitmap))
            }
            return bitmap.getPixel(4, 4)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawablePixels(drawable: Drawable?, view: View): IntArray {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            drawable?.apply {
                setBounds(0, 0, view.width, view.height)
                draw(Canvas(bitmap))
            }
            return IntArray(view.width * view.height).also {
                bitmap.getPixels(it, 0, view.width, 0, 0, view.width, view.height)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderedPixel(view: View): Int {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            view.draw(Canvas(bitmap))
            // Empty area below panel instructions and between the candidate sidebars.
            return bitmap.getPixel(view.width / 2, view.height - 2)
        } finally {
            bitmap.recycle()
        }
    }

    private fun onMainThread(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                block()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun withSavedPreferences(block: () -> Unit) {
        val prefs = ThemeManager.prefs
        val glass = prefs.enhancedGlass.getValue()
        val border = prefs.keyBorder.getValue()
        val stroke = prefs.keyBorderStroke.getValue()
        val glassBorder = prefs.glassKeyBorder.getValue()
        val keyAlpha = prefs.glassKeyAlpha.getValue()
        val animation = AppPrefs.getInstance().advanced.disableAnimation
        val disableAnimation = animation.getValue()
        try {
            animation.setValue(true)
            block()
        } finally {
            prefs.enhancedGlass.setValue(glass)
            prefs.keyBorder.setValue(border)
            prefs.keyBorderStroke.setValue(stroke)
            prefs.glassKeyBorder.setValue(glassBorder)
            prefs.glassKeyAlpha.setValue(keyAlpha)
            animation.setValue(disableAnimation)
        }
    }
}
