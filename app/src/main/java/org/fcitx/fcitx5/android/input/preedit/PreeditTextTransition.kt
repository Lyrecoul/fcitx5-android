/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.preedit

import android.text.TextUtils
import android.view.View
import android.widget.TextView
import kotlin.math.max

/** A single pending tick coalesces fast input; new targets never queue behind old preedit. */
internal class PreeditTextTransition(private val view: TextView) : Runnable {
    private var target: CharSequence = ""
    private var pending = false
    private var batchSize = 2

    init {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                cancel()
                view.text = target
            }
        })
    }

    fun update(text: CharSequence) {
        target = text
        if (text.isEmpty() || !view.isAttachedToWindow || TextUtils.equals(view.text, text)) {
            cancel()
            // Also apply formatting/cursor-only changes, even when the characters match.
            view.text = text
            return
        }
        val prefix = commonPrefix(view.text, text)
        batchSize = max(2, (Character.codePointCount(view.text, prefix, view.text.length) +
                Character.codePointCount(text, prefix, text.length) + 2) / 3)
        if (!pending) {
            pending = true
            view.postDelayed(this, TICK_MS)
        }
    }

    override fun run() {
        pending = false
        val current = view.text
        val prefix = commonPrefix(current, target)
        view.text = if (current.length > prefix) {
            val remaining = Character.codePointCount(current, prefix, current.length)
            current.subSequence(0, Character.offsetByCodePoints(current, current.length,
                -minOf(batchSize, remaining)))
        } else {
            val remaining = Character.codePointCount(target, prefix, target.length)
            target.subSequence(0, Character.offsetByCodePoints(target, prefix,
                minOf(batchSize, remaining)))
        }
        if (TextUtils.equals(view.text, target)) {
            view.text = target
        } else {
            pending = true
            view.postDelayed(this, TICK_MS)
        }
    }

    private fun commonPrefix(current: CharSequence, next: CharSequence): Int {
        var prefix = 0
        val limit = minOf(current.length, next.length)
        while (prefix < limit && current[prefix] == next[prefix]) prefix++
        // A changed supplementary character can share its leading surrogate.
        if (prefix > 0 && Character.isHighSurrogate(current[prefix - 1])) prefix--
        return prefix
    }

    private fun cancel() {
        view.removeCallbacks(this)
        pending = false
    }

    private companion object {
        const val TICK_MS = 40L
    }
}
