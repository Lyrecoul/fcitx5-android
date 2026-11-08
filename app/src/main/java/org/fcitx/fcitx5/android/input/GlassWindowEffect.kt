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
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import java.util.function.Consumer

/** Bounds the system blur to the keyboard. Tint and alpha follow the original module. */
@RequiresApi(Build.VERSION_CODES.S)
class GlassWindowEffect(
    private val window: Window,
    private val keyboard: View
) {
    private val manager = keyboard.context.getSystemService(WindowManager::class.java)
    private val originalBackground = window.decorView.background
    private val originalClipToOutline = window.decorView.clipToOutline
    private var attached = false
    private var lastBounds: List<Int>? = null
    private val location = IntArray(2)
    private val decorLocation = IntArray(2)
    private val blurListener = Consumer<Boolean> {
        if (attached) updateBounds(force = true)
    }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        confineBlur()
        true
    }
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateBounds()
    }

    fun attach() {
        attached = true
        keyboard.addOnLayoutChangeListener(layoutListener)
        window.decorView.addOnLayoutChangeListener(layoutListener)
        window.decorView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
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
            cornerRadius = ThemeManager.prefs.glassCornerRadius.getValue() * keyboard.resources.displayMetrics.density
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
        // The background outline describes only the blur, not the fullscreen IME.
        // Clipping DecorView to it also cuts off preedit and key popups above the keyboard.
        window.decorView.clipToOutline = false
        // The platform creates its blur layer asynchronously. Confine it before drawing,
        // rather than cancelling the request while that layer is still being created.
        window.setBackgroundBlurRadius(if (manager.isCrossWindowBlurEnabled)
            ThemeManager.prefs.glassBlurRadius.getValue() else 0)
    }

    private fun confineBlur(): Boolean {
        val bounds = lastBounds ?: return false
        fun confine(drawable: Drawable?): Boolean {
            if (drawable !is LayerDrawable) return false
            for (index in 0 until drawable.numberOfLayers) {
                val child = drawable.getDrawable(index)
                if (child.javaClass.simpleName == "BackgroundBlurDrawable") {
                    val right = (bounds[4] - bounds[0] - bounds[2]).coerceAtLeast(0)
                    val bottom = (bounds[5] - bounds[1] - bounds[3]).coerceAtLeast(0)
                    if (drawable.getLayerInsetLeft(index) != bounds[0] ||
                        drawable.getLayerInsetTop(index) != bounds[1] ||
                        drawable.getLayerInsetRight(index) != right ||
                        drawable.getLayerInsetBottom(index) != bottom) {
                        drawable.setLayerInset(index, bounds[0], bounds[1], right, bottom)
                    }
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
        // DecorView.getBackground() returns the original drawable, hiding the platform's
        // blur LayerDrawable. Its public callback chain leads to the layer that draws it.
        var current: Drawable? = window.decorView.background
        var result = false
        repeat(4) {
            if (!result) result = confine(current)
            current = current?.callback as? Drawable
        }
        return result
    }

    fun detach() {
        attached = false
        manager.removeCrossWindowBlurEnabledListener(blurListener)
        keyboard.removeOnLayoutChangeListener(layoutListener)
        window.decorView.removeOnLayoutChangeListener(layoutListener)
        window.decorView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        window.setBackgroundBlurRadius(0)
        window.setBackgroundDrawable(originalBackground)
        window.decorView.clipToOutline = originalClipToOutline
    }
}
