/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android

import android.text.InputType
import org.fcitx.fcitx5.android.input.PanguInputSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanguInputSessionTest {
    @Test
    fun onlyUninterruptedCommitsOwnTheirLeftBoundary() {
        val session = PanguInputSession()
        assertEquals("a", session.format("a", 2, false, "中"))
        session.onCommit(0, 2, 2)
        assertEquals(" a", session.format("a", 2, false, "中"))
        assertEquals("a", session.format("a", 2, true, "中"))
        assertEquals("a", session.format("a", 1, false, "中"))
        session.reset()
        assertEquals("a", session.format("a", 2, false, "中"))
        session.onCommit(2, 1, 1)
        assertEquals(" 字", session.format("字", 3, false, "a"))
    }

    @Test
    fun emptyAndInternalCursorCommitsEndTheTypingRun() {
        val session = PanguInputSession()
        session.onCommit(0, 4, 2)
        assertFalse(session.canUseBoundary(2, false))
        session.onCommit(0, 4, 4)
        assertTrue(session.canUseBoundary(4, false))
        session.onCommit(4, 0, 0)
        assertFalse(session.canUseBoundary(4, false))
    }

    @Test
    fun controlsShortcutsAndUnpairedReleasesAreForwarded() {
        val session = PanguInputSession()
        assertEquals(PanguInputSession.KeyAction.Forward, session.onKeyEvent(67, 0, true, true, false))
        assertEquals(PanguInputSession.KeyAction.Forward, session.onKeyEvent(66, '\n'.code, true, true, false))
        assertEquals(PanguInputSession.KeyAction.Forward, session.onKeyEvent(29, 'a'.code, true, true, true))
        assertEquals(PanguInputSession.KeyAction.Forward, session.onKeyEvent(29, 'a'.code, false, true, false))
        assertEquals(PanguInputSession.KeyAction.Commit, session.onKeyEvent(29, 'A'.code, true, true, false))
        assertEquals(PanguInputSession.KeyAction.Consumed, session.onKeyEvent(29, 0, false, false, false))
        assertEquals(PanguInputSession.KeyAction.Forward, session.onKeyEvent(30, 'b'.code, true, false, false))
        assertEquals(PanguInputSession.KeyAction.Forward, session.onKeyEvent(31, Int.MIN_VALUE or 'c'.code, true, true, false))
    }

    @Test
    fun overlappingAndRepeatedDownsConsumeExactlyTheirOwnUps() {
        val session = PanguInputSession()
        repeat(2) { assertEquals(PanguInputSession.KeyAction.Commit, session.onKeyEvent(29, 'a'.code, true, true, false)) }
        assertEquals(PanguInputSession.KeyAction.Commit, session.onKeyEvent(30, 'b'.code, true, true, false))
        assertEquals(PanguInputSession.KeyAction.Consumed, session.onKeyEvent(29, 0, false, true, false))
        assertEquals(PanguInputSession.KeyAction.Consumed, session.onKeyEvent(30, 0, false, true, false))
        assertEquals(PanguInputSession.KeyAction.Forward, session.onKeyEvent(29, 0, false, true, false))
    }

    @Test
    fun filtersSensitiveAndNonProseFields() {
        assertTrue(PanguInputSession.supportsInputType(InputType.TYPE_CLASS_TEXT))
        for (variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )) {
            assertFalse(PanguInputSession.supportsInputType(InputType.TYPE_CLASS_TEXT or variation))
        }
        assertFalse(PanguInputSession.supportsInputType(InputType.TYPE_CLASS_NUMBER))
        assertFalse(PanguInputSession.supportsInputType(InputType.TYPE_NULL))
    }
}
