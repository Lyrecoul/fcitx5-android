/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android

import org.fcitx.fcitx5.android.utils.PanguFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class PanguFormatterTest {

    @Test
    fun addsSpacesBetweenCjkAndLatinOrNumbers() {
        assertEquals(" world", PanguFormatter.format("world", "你好"))
        assertEquals(" 123", PanguFormatter.format("123", "你好"))
        assertEquals("hello ", PanguFormatter.format("hello", "", "世界"))
    }

    @Test
    fun addsSpacesAroundSelectedSymbols() {
        assertEquals(" (test)", PanguFormatter.format("(test)", "你好"))
        assertEquals("test) ", PanguFormatter.format("test)", "", "世界"))
        assertEquals("$ ", PanguFormatter.format("$", "", "中文"))
    }

    @Test
    fun doesNotDuplicateExistingWhitespaceOrSplitLatinWords() {
        assertEquals(" world", PanguFormatter.format(" world", "你好"))
        assertEquals("hello123", PanguFormatter.format("hello123"))
        assertEquals("你好 world", PanguFormatter.format("你好 world"))
    }

    @Test
    fun preservesSupplementaryCodePoints() {
        assertEquals("😀world", PanguFormatter.format("😀world", "你好"))
    }

    @Test
    fun mapsUtf16CursorAcrossInsertedSpaces() {
        assertEquals(4, PanguFormatter.cursorOffset("中文A", 2, leadingSpace = true))
        assertEquals(5, PanguFormatter.cursorOffset("中文A", 3, leadingSpace = true))
        assertEquals(2, PanguFormatter.cursorOffset("😀中文", 2))
        assertEquals(7, PanguFormatter.cursorOffset("𠀀ab文", 5))
    }
}
