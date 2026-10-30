/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import java.util.function.Consumer

/** Uses the public window blur API; an opaque tint is retained when blur is unavailable. */
@RequiresApi(Build.VERSION_CODES.S)
class GlassWindowEffect(
    private val window: Window,
    private val keyboard: View,
    private val setTranslucent: (Boolean) -> Unit
) {
    private val manager = keyboard.context.getSystemService(WindowManager::class.java)
    private val originalBackground = window.decorView.background
    private var attached = false
    private var lastBounds: List<Int>? = null
    private val location = IntArray(2)
    private val decorLocation = IntArray(2)
    private val blurListener = Consumer<Boolean> {
        if (attached) updateBounds(force = true)
    }
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateBounds()
    }

    fun attach() {
        attached = true
        keyboard.addOnLayoutChangeListener(layoutListener)
        window.decorView.addOnLayoutChangeListener(layoutListener)
        updateBounds()
        manager.addCrossWindowBlurEnabledListener(keyboard.context.mainExecutor, blurListener)
    }

    private fun updateBounds(force: Boolean = false) {
        keyboard.getLocationOnScreen(location)
        window.decorView.getLocationOnScreen(decorLocation)
        val left = (location[0] - decorLocation[0]).coerceAtLeast(0)
        val top = (location[1] - decorLocation[1]).coerceAtLeast(0)
        val bounds = listOf(left, top, keyboard.width, keyboard.height,
            window.decorView.width, window.decorView.height)
        if (!force && bounds == lastBounds) return
        lastBounds = bounds
        // Confine both the tint and the system-created blur layer to the keyboard.
        val shape = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = 20 * keyboard.resources.displayMetrics.density
        }
        window.setBackgroundDrawable(object : InsetDrawable(
            shape, left, top,
            (window.decorView.width - left - keyboard.width).coerceAtLeast(0),
            (window.decorView.height - top - keyboard.height).coerceAtLeast(0)
        ) {
            override fun getPadding(padding: Rect): Boolean {
                padding.setEmpty()
                return false
            }
        })
        val confined = confineBlur()
        setTranslucent(manager.isCrossWindowBlurEnabled && confined)
    }

    private fun confineBlur(): Boolean {
        window.setBackgroundBlurRadius(if (manager.isCrossWindowBlurEnabled) 100 else 0)
        val bounds = lastBounds ?: return false
        fun confine(drawable: Drawable?): Boolean {
            if (drawable !is LayerDrawable) return false
            for (index in 0 until drawable.numberOfLayers) {
                val child = drawable.getDrawable(index)
                if (child.javaClass.simpleName == "BackgroundBlurDrawable") {
                    val right = (bounds[4] - bounds[0] - bounds[2]).coerceAtLeast(0)
                    val bottom = (bounds[5] - bounds[1] - bounds[3]).coerceAtLeast(0)
                    drawable.setLayerInset(index, bounds[0], bounds[1], right, bottom)
                    // setLayerInset alone does not update child bounds until the next layout.
                    val parentBounds = drawable.bounds
                    child.setBounds(parentBounds.left + bounds[0], parentBounds.top + bounds[1],
                        parentBounds.right - right, parentBounds.bottom - bottom)
                    return true
                }
                if (confine(child)) return true
            }
            return false
        }
        return confine(window.decorView.background).also { confined ->
            // OEMs may use a different drawable hierarchy. Never blur the entire app behind the IME.
            if (!confined) window.setBackgroundBlurRadius(0)
        }
    }

    fun detach() {
        attached = false
        manager.removeCrossWindowBlurEnabledListener(blurListener)
        keyboard.removeOnLayoutChangeListener(layoutListener)
        window.decorView.removeOnLayoutChangeListener(layoutListener)
        window.setBackgroundBlurRadius(0)
        window.setBackgroundDrawable(originalBackground)
    }
}
