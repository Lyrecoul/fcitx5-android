package org.fcitx.fcitx5.android.input

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView

/** Short, interruptible fades. Removed text is drawn in an inert overlay, never retained as a hit target. */
internal class TextFadeTransition(private val view: TextView) {
    private var populated = false
    private var animator: ValueAnimator? = null
    private var ghost: BitmapDrawable? = null
    private var host: ViewGroup? = null

    init {
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (populated) fadeIn()
            }
            override fun onViewDetachedFromWindow(v: View) {
                // Exit overlays belong to the window, not the removed holder.
                if (ghost == null) cancel()
                populated = false
            }
        })
    }

    fun update(text: CharSequence) {
        if (!populated && text.isEmpty()) {
            view.text = text
            return
        }
        val wasPopulated = populated
        populated = text.isNotEmpty()
        cancel()
        if (wasPopulated && !populated) fadeOut()
        view.text = text
        if (!wasPopulated && populated && view.isAttachedToWindow) {
            fadeIn()
        }
    }

    private fun fadeIn() {
        view.alpha = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 120
            interpolator = DecelerateInterpolator()
            addUpdateListener { view.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun cancel() {
        animator?.cancel()
        animator = null
        view.alpha = 1f
        ghost?.let { host?.overlay?.remove(it) }
        ghost = null
        host = null
    }

    private fun fadeOut() {
        val root = view.rootView as? ViewGroup ?: return
        if (!view.isShown || view.width <= 0 || view.height <= 0) return
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        // Only the glyphs fade; the original background stays under container control.
        val background = view.background
        try {
            view.background = null
            view.draw(Canvas(bitmap))
        } finally {
            view.background = background
        }
        val location = IntArray(2)
        val origin = IntArray(2)
        view.getLocationInWindow(location)
        root.getLocationInWindow(origin)
        val drawable = BitmapDrawable(view.resources, bitmap).apply {
            setBounds(location[0] - origin[0], location[1] - origin[1],
                location[0] - origin[0] + view.width, location[1] - origin[1] + view.height)
        }
        host = root
        ghost = drawable
        root.overlay.add(drawable)
        animator = ValueAnimator.ofInt(255, 0).apply {
            duration = 80
            addUpdateListener {
                drawable.alpha = it.animatedValue as Int
                if (drawable.alpha == 0) {
                    root.overlay.remove(drawable)
                    ghost = null
                    host = null
                }
            }
            start()
        }
    }
}
