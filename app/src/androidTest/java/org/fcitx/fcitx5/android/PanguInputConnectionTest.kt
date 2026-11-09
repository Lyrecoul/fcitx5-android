/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.core.FormattedText
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.input.cursor.CursorTracker
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real service entry points and Android replacement semantics, without a remote editor process. */
class PanguInputConnectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private class Editor {
        val view = EditText(InstrumentationRegistry.getInstrumentation().targetContext)
        val connection = object : BaseInputConnection(view, true) {
            override fun getEditable() = view.editableText
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    val text = editable
                    val start = view.selectionStart
                    val end = view.selectionEnd
                    if (start != end) text.delete(minOf(start, end), maxOf(start, end))
                    else if (start > 0) text.delete(Character.offsetByCodePoints(text, start, -1), start)
                }
                return true
            }
        }
        val service = FcitxInputMethodService()
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        private val selection = FcitxInputMethodService::class.java.getDeclaredField("selection").let {
            it.isAccessible = true
            it.get(service) as CursorTracker
        }
        private val event = FcitxInputMethodService::class.java.getDeclaredMethod("handleFcitxEvent", FcitxEvent::class.java).apply {
            isAccessible = true
        }

        init {
            for ((name, value) in listOf("mStartedInputConnection" to connection, "mInputEditorInfo" to info)) {
                InputMethodService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
            }
            // Cursor updates may enqueue lazy engine resets. No service event consumer is started.
            FcitxInputMethodService::class.java.getDeclaredField("fcitx").apply { isAccessible = true }
                .set(service, FcitxDaemon.connect(CONNECTION))
            view.setText("")
            view.setSelection(0)
            selection.resetTo(0)
        }

        fun commit(value: String, cursor: Int = -1) = service.commitText(value, cursor)

        fun preedit(value: String, cursor: Int = value.length) {
            event.invoke(service, FcitxEvent.ClientPreeditEvent(FormattedText(arrayOf(value), intArrayOf(0), cursor)))
        }

        fun existing(value: String) {
            view.setText(value)
            view.setSelection(value.length)
            selection.resetTo(value.length)
        }

        fun key(char: Char, up: Boolean = false, virtual: Boolean = false) {
            event.invoke(service, FcitxEvent.KeyEvent(FcitxEvent.KeyEvent.Data(
                KeySym(char.code), if (virtual) KeyStates.Virtual else KeyStates.Empty, char.code, up, 0
            )))
        }

        fun backspace() {
            event.invoke(service, FcitxEvent.KeyEvent(FcitxEvent.KeyEvent.Data(
                KeySym(0xff08), KeyStates.Virtual, 0, false, 0
            )))
        }

        fun deleteSurrounding() {
            event.invoke(service, FcitxEvent.DeleteSurroundingEvent(FcitxEvent.DeleteSurroundingEvent.Data(1, 0)))
        }

        fun cachedKey(keyCode: Int, down: Boolean) {
            val cache = FcitxInputMethodService::class.java.getDeclaredField("cachedKeyEvents").let {
                it.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                (it.get(service) as android.util.LruCache<Int, KeyEvent>)
            }
            val key = KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode)
            cache.put(1, key)
            event.invoke(service, FcitxEvent.KeyEvent(FcitxEvent.KeyEvent.Data(
                KeySym(key.unicodeChar), KeyStates.Empty, key.unicodeChar, !down, 1
            )))
        }

        fun move(start: Int, end: Int = start) {
            val oldStart = view.selectionStart
            val oldEnd = view.selectionEnd
            view.setSelection(start, end)
            service.onUpdateSelection(oldStart, oldEnd, start, end, -1, -1)
        }

        fun acknowledge() {
            service.onUpdateSelection(0, 0, view.selectionStart, view.selectionEnd, -1, -1)
        }

        fun assertText(expected: String) = assertEquals(expected, view.text.toString())
    }

    private fun withEditor(block: (Editor) -> Unit) = instrumentation.runOnMainSync {
        val pref = AppPrefs.getInstance().advanced.autoPanguSpacing
        val saved = pref.getValue()
        pref.setValue(true)
        try {
            block(Editor())
        } finally {
            pref.setValue(saved)
            FcitxDaemon.disconnect(CONNECTION)
        }
    }

    @Test
    fun candidatesAndBothForwardedEnglishKeyPathsRemainContinuous() = withEditor { editor ->
        editor.preedit("中文")
        editor.commit("中文")
        editor.acknowledge()
        editor.key('a')
        editor.key('a', up = true)
        editor.cachedKey(KeyEvent.KEYCODE_B, true)
        editor.cachedKey(KeyEvent.KEYCODE_B, false)
        editor.preedit("字", cursor = 0)
        editor.commit("字")
        editor.key('1', virtual = true)
        editor.key('1', up = true, virtual = true)
        editor.assertText("中文 ab 字 1")
        assertEquals(9, editor.view.selectionStart)
    }

    @Test
    fun returningToOldTextAndReplacingReverseSelectionPreservesBoundaries() = withEditor { editor ->
        editor.existing("中文abc中文")
        editor.move(2)
        editor.key('x')
        editor.key('x', up = true)
        editor.assertText("中文xabc中文")
        editor.acknowledge()
        editor.move(6, 3)
        editor.commit("中")
        editor.assertText("中文x中中文")
        editor.commit("a")
        editor.assertText("中文x中 a中文")
    }

    @Test
    fun returningToEndAfterMiddleEditsResumesDirectTypingSpacing() = withEditor { editor ->
        editor.commit("中文")
        editor.acknowledge()
        editor.move(1)
        editor.key('x')
        editor.key('x', up = true)
        editor.key('y')
        editor.key('y', up = true)
        editor.acknowledge()
        editor.move(editor.view.length())
        editor.key('a')
        editor.key('a', up = true)
        editor.assertText("中xy文 a")
        editor.commit("字")
        editor.assertText("中xy文 a 字")
    }

    @Test
    fun returningToEndSpacesCandidatesEvenWithInternalPreeditCursor() = withEditor { editor ->
        editor.commit("abc")
        editor.acknowledge()
        editor.move(1)
        editor.commit("中")
        editor.acknowledge()
        editor.move(editor.view.length())
        editor.preedit("zhong", cursor = 2)
        editor.commit("文")
        editor.assertText("a中bc 文")
    }

    @Test
    fun middlePreeditAndSelectionEndingAtDocumentEndPreserveOldBoundaries() = withEditor { editor ->
        editor.existing("abcXYZ")
        editor.move(3)
        editor.preedit("zhong", cursor = 2)
        editor.commit("文")
        editor.assertText("abc文XYZ")
        editor.acknowledge()
        editor.move(editor.view.length(), 4)
        editor.commit("a")
        editor.assertText("abc文a")
    }

    @Test
    fun changedPreeditAndInternalCursorReplaceWholeSpan() = withEditor { editor ->
        editor.commit("abc")
        editor.preedit("zhong", cursor = 2)
        editor.commit("中文A", cursor = 2)
        editor.assertText("abc 中文 A")
        assertEquals(7, editor.view.selectionStart)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(editor.view.text))
    }

    @Test
    fun backspaceRemovesOrphanedAutomaticSpaceButNotManualSpace() = withEditor { editor ->
        editor.commit("中文")
        editor.commit("ab")
        editor.backspace()
        editor.assertText("中文 a")
        editor.acknowledge()
        editor.backspace()
        editor.assertText("中文")
        editor.commit(" a")
        editor.backspace()
        editor.assertText("中文 ")
    }

    @Test
    fun forwardedEnglishBackspacesKeepOwnershipAcrossDownAndUp() = withEditor { editor ->
        editor.commit("中")
        editor.key('a')
        editor.key('a', up = true)
        editor.key('b')
        editor.key('b', up = true)
        repeat(2) {
            editor.cachedKey(KeyEvent.KEYCODE_DEL, true)
            editor.cachedKey(KeyEvent.KEYCODE_DEL, false)
            editor.acknowledge()
        }
        editor.assertText("中")
    }

    @Test
    fun internalSpacesAndChineseCommitsAreCleanedWithoutCrossingManualEdits() = withEditor { editor ->
        editor.commit("a中b")
        editor.backspace()
        editor.assertText("a 中")
        editor.backspace()
        editor.assertText("a")
        editor.commit("中")
        editor.commit("a")
        editor.acknowledge()
        editor.move(0)
        editor.move(editor.view.length())
        editor.backspace()
        editor.assertText("a 中 ")
    }

    @Test
    fun engineDeletionAndSupplementaryCharactersRetainAutomaticSpaceOwnership() = withEditor { editor ->
        editor.commit("中a😀")
        editor.backspace()
        editor.acknowledge()
        editor.assertText("中 a")
        editor.deleteSurrounding()
        editor.assertText("中")
        editor.commit("a")
        editor.commit("𠀀")
        editor.backspace()
        editor.assertText("中 a")
    }

    @Test
    fun automaticSpaceSurvivesWhenRightHandTextStillNeedsIt() = withEditor { editor ->
        editor.existing("b")
        editor.move(0)
        editor.commit("中")
        editor.commit("a")
        editor.backspace()
        editor.assertText("中 b")
    }

    @Test
    fun deletionDoesNotRemoveSpacingWhenDisabledOrSelectingText() = withEditor { editor ->
        editor.commit("中a")
        AppPrefs.getInstance().advanced.autoPanguSpacing.setValue(false)
        editor.backspace()
        editor.assertText("中 ")
        AppPrefs.getInstance().advanced.autoPanguSpacing.setValue(true)
        editor.commit("ab")
        editor.move(2, 4)
        editor.backspace()
        editor.assertText("中 ")
    }

    @Test
    fun sensitiveFieldsAndDisabledPreferenceLeaveCommitsUntouched() = withEditor { editor ->
        for (variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )) {
            editor.info.inputType = InputType.TYPE_CLASS_TEXT or variation
            editor.commit("中a")
        }
        editor.assertText("中a".repeat(6))
        editor.info.inputType = InputType.TYPE_CLASS_TEXT
        AppPrefs.getInstance().advanced.autoPanguSpacing.setValue(false)
        editor.commit("中a")
        editor.assertText("中a".repeat(7))
    }

    private companion object {
        const val CONNECTION = "PanguInputConnectionTest"
    }
}
