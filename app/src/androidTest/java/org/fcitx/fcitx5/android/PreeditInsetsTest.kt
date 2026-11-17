/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.InputView
import org.fcitx.fcitx5.android.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercise animated preedit inside the real IME window, with a bottom-aligned editor. */
class PreeditInsetsTest {
    @get:Rule
    val activity = ActivityTestRule(MainActivity::class.java, false, false)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun animatedPreeditDoesNotChangeApplicationInsetsOrEditorBounds() {
        val context = instrumentation.targetContext
        val previousIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val prefs = ThemeManager.prefs
        val enhanced = prefs.enhancedGlass.getValue()
        val radius = prefs.glassCornerRadius.getValue()
        lateinit var editor: EditText
        var input: InputView? = null
        var listener: ViewTreeObserver.OnPreDrawListener? = null
        var eventsEnabled = false
        try {
            shell("ime set ${context.packageName}/org.fcitx.fcitx5.android.input.FcitxInputMethodService")
            activity.launchActivity(null)
            onMain {
                prefs.enhancedGlass.setValue(true)
                prefs.glassCornerRadius.setValue(20)
                activity.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                editor = EditText(activity.activity).apply { setSingleLine(); setText("Editor stays still") }
                activity.activity.setContentView(FrameLayout(activity.activity).apply {
                    addView(editor, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
                    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                        val bottom = insets.getInsets(WindowInsetsCompat.Type.ime() or
                            WindowInsetsCompat.Type.systemBars()).bottom
                        view.setPadding(0, 0, 0, bottom)
                        insets
                    }
                })
                editor.requestFocus()
            }
            instrumentation.waitForIdleSync()
            onMain {
                activity.activity.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
            lateinit var service: FcitxInputMethodService
            // Binding the selected IME and its initial window layout are asynchronous.
            for (attempt in 0 until 100) {
                onMain {
                    currentService()?.let { candidate ->
                        val view = field(candidate, "inputView") as? InputView
                        if (view?.isShown == true && view.keyboardView.height > 0) {
                            service = candidate
                            input = view
                        }
                    }
                }
                if (input != null) break
                Thread.sleep(50)
            }
            assertTrue("Selected IME must be visible", input != null)
            val view = input!!
            val update = InputView::class.java.getDeclaredMethod("handleFcitxEvent", FcitxEvent::class.java)
                .apply { isAccessible = true }
            fun panel(text: String, auxiliary: String = "") = FcitxEvent.InputPanelEvent(
                FcitxEvent.InputPanelEvent.Data(
                    FormattedText(arrayOf(text), intArrayOf(0), text.length), FormattedText.Empty,
                    FormattedText(arrayOf(auxiliary), intArrayOf(0), -1), emptyArray()
                )
            )
            onMain {
                eventsEnabled = view.handleEvents
                view.handleEvents = false
                update.invoke(view, panel(""))
            }
            Thread.sleep(700)
            val samples = mutableListOf<Pair<Int, Int>>()
            val editorBounds = Rect()
            val observedBounds = mutableListOf<Rect>()
            var maxPreeditHeight = 0
            val preedit = (field(view, "preedit") as org.fcitx.fcitx5.android.input.preedit.PreeditComponent).ui.root
            lateinit var baseline: InputMethodService.Insets
            onMain {
                baseline = InputMethodService.Insets().also { service.onComputeInsets(it) }
                editor.getGlobalVisibleRect(editorBounds)
                assertTrue("Editor must be visible above the keyboard", !editorBounds.isEmpty)
                listener = ViewTreeObserver.OnPreDrawListener {
                    val insets = InputMethodService.Insets().also { service.onComputeInsets(it) }
                    samples += insets.contentTopInsets to insets.visibleTopInsets
                    observedBounds += Rect().also { editor.getGlobalVisibleRect(it) }
                    maxPreeditHeight = maxOf(maxPreeditHeight, preedit.height)
                    true
                }.also { view.viewTreeObserver.addOnPreDrawListener(it) }
            }
            for ((text, auxiliary) in listOf("ni hao" to "", "ni hao shi jie ".repeat(15) to "auxiliary line", "ni" to "", "" to "")) {
                onMain { update.invoke(view, panel(text, auxiliary)) }
                Thread.sleep(900)
            }
            onMain {
                assertTrue("Observe intermediate animation frames", samples.size > 10)
                assertTrue("Preedit must actually appear", maxPreeditHeight > 0)
                for ((content, visible) in samples) {
                    assertEquals("Preedit must not resize the application", baseline.contentTopInsets, content)
                    assertEquals("Preedit must not pan the application", baseline.visibleTopInsets, visible)
                }
                observedBounds.forEach { assertEquals("Editor must stay still", editorBounds, it) }
            }
        } finally {
            onMain {
                input?.let { view ->
                    listener?.let { view.viewTreeObserver.removeOnPreDrawListener(it) }
                    view.handleEvents = eventsEnabled
                }
                prefs.enhancedGlass.setValue(enhanced)
                prefs.glassCornerRadius.setValue(radius)
            }
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
