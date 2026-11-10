/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.ViewPropertyAnimator
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.isEnhancedGlass
import org.fcitx.fcitx5.android.data.theme.panelBackgroundColor
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView
import org.fcitx.fcitx5.android.utils.borderlessRippleDrawable
import org.fcitx.fcitx5.android.utils.circlePressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageResource
import splitties.views.padding

class ToolButton(context: Context) : CustomGestureView(context) {

    companion object {
        val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation
    }

    private var iconColor = Color.WHITE
    private var iconOutlineColor: Int? = null
    private val image = imageView {
        isClickable = false
        isFocusable = false
        padding = dp(10)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    var iconRotation: Float
        get() = image.rotation
        set(value) {
            image.rotation = value
        }

    constructor(context: Context, @DrawableRes icon: Int, theme: Theme) : this(context) {
        applyThemeColors(theme)
        setIcon(icon)
        setPressHighlightColor(theme.keyPressHighlightColor)
        add(image, lParams(wrapContent, wrapContent, gravityCenter))
    }

    private fun applyThemeColors(theme: Theme) {
        val panel = theme.panelBackgroundColor
        val hasImage = theme is Theme.Custom && theme.backgroundImage != null
        val base = if (ThemeManager.prefs.keyBorder.getValue()) theme.backgroundColor
            else theme.keyboardColor
        val background = when {
            Color.alpha(panel) == 255 -> panel
            !theme.isEnhancedGlass && !hasImage && Color.alpha(base) == 255 ->
                ColorUtils.compositeColors(panel, base)
            else -> null
        }
        iconColor = if (background != null) {
            contrastingColor(theme, background)
        } else {
            // Keep the glass surface clear. Only the glyph gets a thin contrasting edge.
            iconOutlineColor = if (theme.isDark) Color.BLACK else Color.WHITE
            if (theme.isDark) Color.WHITE else Color.BLACK
        }
    }

    private fun contrastingColor(theme: Theme, background: Int): Int {
        fun readable(color: Int) = ColorUtils.calculateContrast(color, background) >= 4.5
        if (readable(theme.altKeyTextColor)) return theme.altKeyTextColor
        if (readable(theme.keyTextColor)) return theme.keyTextColor
        return if (readable(Color.BLACK)) Color.BLACK else Color.WHITE
    }

    fun iconAnimate(): ViewPropertyAnimator = image.animate()

    fun setIcon(@DrawableRes icon: Int) {
        image.imageResource = icon
        val glyph = image.drawable.mutate()
        glyph.setTint(iconColor)
        image.setImageDrawable(iconOutlineColor?.let { outlineColor ->
            val outline = requireNotNull(context.getDrawable(icon)).mutate().apply {
                setTint(outlineColor)
            }
            OutlinedToolIcon(glyph, outline, dp(0.75f))
        } ?: glyph)
    }

    fun setPressHighlightColor(@ColorInt color: Int) {
        background = if (disableAnimation) {
            circlePressHighlightDrawable(color)
        } else {
            borderlessRippleDrawable(color, dp(20))
        }
    }
}

/** Draws only around the glyph; never fills its view or padding. */
private class OutlinedToolIcon(
    private val glyph: Drawable,
    private val outline: Drawable,
    private val radius: Float
) : Drawable() {
    override fun getIntrinsicWidth() = glyph.intrinsicWidth
    override fun getIntrinsicHeight() = glyph.intrinsicHeight

    override fun onBoundsChange(bounds: Rect) {
        glyph.bounds = bounds
        outline.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        for (x in -1..1) {
            for (y in -1..1) {
                if (x == 0 && y == 0) continue
                val checkpoint = canvas.save()
                canvas.translate(x * radius, y * radius)
                outline.draw(canvas)
                canvas.restoreToCount(checkpoint)
            }
        }
        glyph.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        glyph.alpha = alpha
        outline.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        glyph.colorFilter = colorFilter
        outline.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
