/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.text.InputType
import org.fcitx.fcitx5.android.utils.PanguFormatter

/** Tracks only the boundary produced by uninterrupted commits, never surrounding old text. */
internal class PanguInputSession {
    private var committedEnd = -1
    private var consumedKeys = IntArray(4)
    private var consumedKeyCount = 0
    private var ownedEnd = -1
    private var automaticSpaces = IntArray(4)
    private var automaticSpaceCount = 0

    fun reset() {
        committedEnd = -1
        ownedEnd = -1
        automaticSpaceCount = 0
    }

    fun canUseBoundary(replacementStart: Int, selected: Boolean): Boolean =
        !selected && replacementStart >= 0 && replacementStart == committedEnd

    fun format(text: String, replacementStart: Int, selected: Boolean, before: String): String =
        PanguFormatter.format(text, if (canUseBoundary(replacementStart, selected)) before else "")

    fun onCommit(start: Int, length: Int, cursor: Int) {
        committedEnd = if (length > 0 && cursor == length) start + length else -1
    }

    fun recordSpaces(start: Int, original: String, formatted: String, cursor: Int) {
        if (start != ownedEnd) automaticSpaceCount = 0
        if (cursor != formatted.length || formatted.isEmpty()) {
            ownedEnd = -1
            automaticSpaceCount = 0
            return
        }
        var source = 0
        for (index in if (original == formatted) IntRange.EMPTY else formatted.indices) {
            if (source < original.length && formatted[index] == original[source]) {
                source++
            } else if (formatted[index] == ' ') {
                if (automaticSpaceCount == automaticSpaces.size) {
                    automaticSpaces = automaticSpaces.copyOf(automaticSpaceCount * 2)
                }
                automaticSpaces[automaticSpaceCount++] = start + index
            }
        }
        ownedEnd = start + formatted.length
    }

    /** Returns UTF-16 units to delete, only when the adjacent space belongs to this run. */
    fun backspace(cursor: Int, before: CharSequence, after: CharSequence): Int {
        committedEnd = -1
        if (cursor != ownedEnd || before.isEmpty()) {
            reset()
            return 0
        }
        val characterSize = Character.charCount(Character.codePointBefore(before, before.length))
        val spaceIndex = before.length - characterSize - 1
        val spacePosition = cursor - characterSize - 1
        val owned = automaticSpaceCount > 0 && automaticSpaces[automaticSpaceCount - 1] == spacePosition
        val obsolete = owned && spaceIndex > 0 && before[spaceIndex] == ' ' &&
            (after.isEmpty() || !PanguFormatter.needsSpace(
                Character.codePointBefore(before, spaceIndex), Character.codePointAt(after, 0)))
        val count = characterSize + if (obsolete) 1 else 0
        ownedEnd = cursor - count
        while (automaticSpaceCount > 0 && automaticSpaces[automaticSpaceCount - 1] >= ownedEnd) {
            automaticSpaceCount--
        }
        return if (obsolete) count else 0
    }

    enum class KeyAction { Forward, Commit, Consumed }

    /** A committed down must consume its up, even if preferences or modifiers changed meanwhile. */
    fun onKeyEvent(
        key: Int,
        codePoint: Int,
        down: Boolean,
        enabled: Boolean,
        modified: Boolean
    ): KeyAction {
        var index = 0
        while (index < consumedKeyCount && consumedKeys[index] != key) index++
        if (index == consumedKeyCount) index = -1
        if (!down) {
            if (index < 0) return KeyAction.Forward
            consumedKeys[index] = consumedKeys[--consumedKeyCount]
            return KeyAction.Consumed
        }
        if (!enabled || modified || !Character.isValidCodePoint(codePoint) ||
            codePoint == 0 || Character.isISOControl(codePoint) ||
            codePoint in 0xD800..0xDFFF
        ) {
            reset()
            return KeyAction.Forward
        }
        if (index < 0) {
            if (consumedKeyCount == consumedKeys.size) consumedKeys = consumedKeys.copyOf(consumedKeyCount * 2)
            consumedKeys[consumedKeyCount++] = key
        }
        return KeyAction.Commit
    }

    companion object {
        fun supportsInputType(inputType: Int): Boolean {
            if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
            return when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> false
                else -> true
            }
        }
    }
}
