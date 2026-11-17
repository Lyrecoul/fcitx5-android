/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.CandidatesView
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.InputView
import org.fcitx.fcitx5.android.input.candidates.floating.FloatingCandidatesMode
import org.fcitx.fcitx5.android.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Run on an emulator/device with an available QWERTY hardware keyboard. */
class HardwareKeyboardPanelTest {
    @get:Rule
    val activity = ActivityTestRule(MainActivity::class.java, false, false)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun hardwareKeyboardKeepsLargePanelHiddenAcrossEditorAndConfigurationChanges() {
        val context = instrumentation.targetContext
        assertEquals(Configuration.KEYBOARD_QWERTY, context.resources.configuration.keyboard)
        assertEquals(Configuration.HARDKEYBOARDHIDDEN_NO, context.resources.configuration.hardKeyboardHidden)
        val previousIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val previousShowIme = Settings.Secure.getString(context.contentResolver, "show_ime_with_hard_keyboard")
        val mode = AppPrefs.getInstance().candidates.mode
        val previousMode = mode.getValue()
        lateinit var editor: EditText
        var service: FcitxInputMethodService? = null
        try {
            // InputDevice mode must work even when Android allows showing the soft keyboard.
            shell("settings put secure show_ime_with_hard_keyboard 1")
            onMain { mode.setValue(FloatingCandidatesMode.InputDevice) }
            shell("ime set ${context.packageName}/org.fcitx.fcitx5.android.input.FcitxInputMethodService")
            activity.launchActivity(null)
            onMain {
                activity.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                editor = EditText(activity.activity).apply { setSingleLine() }
                activity.activity.setContentView(editor)
                editor.requestFocus()
            }
            instrumentation.waitForIdleSync()
            onMain {
                activity.activity.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
            for (attempt in 0 until 100) {
                onMain {
                    currentService()?.let {
                        if (it.isInputViewShown && field(it, "inputView") != null) service = it
                    }
                }
                if (service != null) break
                Thread.sleep(50)
            }
            assertTrue("IME window must be started", service != null)
            onMain {
                val ime = service!!
                fun input() = field(ime, "inputView") as InputView
                fun assertHardwarePanel() {
                    val input = input()
                    val candidates = field(ime, "candidatesView") as CandidatesView
                    assertEquals("Hardware typing must hide the large keyboard panel", View.GONE, input.visibility)
                    assertFalse(input.handleEvents)
                    assertTrue("Floating candidates must still receive engine events", candidates.handleEvents)
                    val insets = InputMethodService.Insets().also { ime.onComputeInsets(it) }
                    val decor = ime.window.window!!.decorView
                    val navigation = decor.findViewById<View>(android.R.id.navigationBarBackground)?.height ?: 0
                    assertEquals("Hardware keyboard must not reserve keyboard height", decor.height - navigation, insets.contentTopInsets)
                    assertEquals(insets.contentTopInsets, insets.visibleTopInsets)
                }
                assertHardwarePanel()
                @Suppress("DEPRECATION")
                ime.onViewClicked(false)
                assertHardwarePanel()
                if (Build.VERSION.SDK_INT >= 34) {
                    ime.onUpdateEditorToolType(MotionEvent.TOOL_TYPE_FINGER)
                    assertHardwarePanel()
                    ime.onUpdateEditorToolType(MotionEvent.TOOL_TYPE_STYLUS)
                    assertHardwarePanel()
                }
                ime.onStartInputView(ime.currentInputEditorInfo, true)
                assertHardwarePanel()

                // A keyboard being disconnected or folded away restores the software panel.
                val original = Configuration(ime.resources.configuration)
                for (hidden in listOf(false, true)) {
                    val unavailable = Configuration(original).apply {
                        keyboard = if (hidden) Configuration.KEYBOARD_QWERTY else Configuration.KEYBOARD_NOKEYS
                        hardKeyboardHidden = if (hidden) Configuration.HARDKEYBOARDHIDDEN_YES else Configuration.HARDKEYBOARDHIDDEN_NO
                    }
                    ime.onConfigurationChanged(unavailable)
                    assertEquals("Unavailable hardware must restore the software keyboard", View.VISIBLE, input().visibility)
                    ime.onConfigurationChanged(original)
                    assertHardwarePanel()
                }
                mode.setValue(FloatingCandidatesMode.Disabled)
                ime.onStartInputView(ime.currentInputEditorInfo, true)
                assertEquals("Explicitly disabling floating candidates retains the software panel", View.VISIBLE, input().visibility)
                mode.setValue(FloatingCandidatesMode.SystemDefault)
                ime.onStartInputView(ime.currentInputEditorInfo, true)
                assertEquals("SystemDefault follows Android's show-with-hardware setting", View.VISIBLE, input().visibility)
                mode.setValue(FloatingCandidatesMode.InputDevice)
                ime.onStartInputView(ime.currentInputEditorInfo, true)
                assertHardwarePanel()
            }
            instrumentation.waitForIdleSync()
            // Capture the actual IME surface for visual inspection alongside the assertions.
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.getExternalFilesDir(null), "hardware-keyboard-panel.png")
                    .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            onMain { mode.setValue(previousMode) }
            if (previousShowIme == null) shell("settings delete secure show_ime_with_hard_keyboard")
            else shell("settings put secure show_ime_with_hard_keyboard $previousShowIme")
            if (previousIme != null) shell("ime set $previousIme")
        }
    }

    private fun currentService(): FcitxInputMethodService? {
        val threadClass = Class.forName("android.app.ActivityThread")
        val thread = threadClass.getDeclaredMethod("currentActivityThread").invoke(null)
        val services = threadClass.getDeclaredField("mServices").apply { isAccessible = true }.get(thread) as Map<*, *>
        return services.values.filterIsInstance<FcitxInputMethodService>().firstOrNull()
    }

    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
    }

    private fun onMain(block: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
}
