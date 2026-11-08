/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.utils

/**
 * Adds the spaces normally expected between CJK text and Latin text, numbers,
 * and a small set of punctuation characters. This is deliberately a pure
 * function so that the rules can be used and tested without an InputConnection.
 */
object PanguFormatter {

    fun format(text: String, before: String = "", after: String = ""): String {
        if (text.isEmpty()) return text

        val codePoints = buildList {
            text.toCodePointSequence().forEach { add(it) }
        }
        if (codePoints.isEmpty()) return text

        val result = StringBuilder(text.length + 2)
        if (before.lastCodePoint()?.let { needsSpace(it, codePoints.first()) } == true &&
            !before.lastCodePoint()!!.isWhitespaceCodePoint() &&
            !codePoints.first().isWhitespaceCodePoint()
        ) {
            result.append(' ')
        }

        codePoints.forEachIndexed { index, codePoint ->
            if (index > 0 && needsSpace(codePoints[index - 1], codePoint) &&
                !codePoints[index - 1].isWhitespaceCodePoint() &&
                !codePoint.isWhitespaceCodePoint()
            ) {
                result.append(' ')
            }
            result.appendCodePoint(codePoint)
        }

        if (after.firstCodePoint()?.let { needsSpace(codePoints.last(), it) } == true &&
            !codePoints.last().isWhitespaceCodePoint() &&
            !after.firstCodePoint()!!.isWhitespaceCodePoint()
        ) {
            result.append(' ')
        }
        return result.toString()
    }

    private fun needsSpace(left: Int, right: Int): Boolean {
        if (left.isCjk() && right.isLatinOrDigit()) return true
        if (left.isLatinOrDigit() && right.isCjk()) return true
        if (left.isCjk() && right.isOpeningBracket()) return true
        if (left.isClosingBracket() && right.isCjk()) return true
        if (left.isCurrencyOrPercent() && right.isCjk()) return true
        if (left.isCjk() && right.isCurrencyOrPercent()) return true
        return false
    }

    private fun Int.isCjk(): Boolean =
        this in 0x2E80..0x2FFF ||
            this in 0x3040..0x30FF ||
            this in 0x3400..0x4DBF ||
            this in 0x4E00..0x9FFF ||
            this in 0xF900..0xFAFF ||
            this in 0x20000..0x2FA1F

    private fun Int.isLatinOrDigit(): Boolean =
        this in 'A'.code..'Z'.code || this in 'a'.code..'z'.code || this in '0'.code..'9'.code

    private fun Int.isOpeningBracket(): Boolean = when (this) {
        '('.code, '['.code, '{'.code, '<'.code -> true
        else -> false
    }

    private fun Int.isClosingBracket(): Boolean = when (this) {
        ')'.code, ']'.code, '}'.code, '>'.code -> true
        else -> false
    }

    private fun Int.isCurrencyOrPercent(): Boolean = when (this) {
        '$'.code, '€'.code, '£'.code, '¥'.code, '%'.code -> true
        else -> false
    }

    private fun Int.isWhitespaceCodePoint() = Character.isWhitespace(this)

    private fun String.lastCodePoint(): Int? = if (isEmpty()) null else codePointBefore(length)

    private fun String.firstCodePoint(): Int? = if (isEmpty()) null else codePointAt(0)
}

private fun String.toCodePointSequence(): Sequence<Int> = sequence {
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        yield(codePoint)
        index += Character.charCount(codePoint)
    }
}
