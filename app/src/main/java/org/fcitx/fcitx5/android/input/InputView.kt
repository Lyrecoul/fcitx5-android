/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Outline
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.ImageView
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.view.updateLayoutParams
import androidx.core.view.WindowInsetsCompat
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.launchOnReady
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceProvider
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.isEnhancedGlass
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcaster
import org.fcitx.fcitx5.android.input.broadcast.PreeditEmptyStateComponent
import org.fcitx.fcitx5.android.input.broadcast.PunctuationComponent
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.candidates.horizontal.HorizontalCandidateComponent
import org.fcitx.fcitx5.android.input.keyboard.glassKeyboardBorder
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyboardHeightPercentBase.DisplayMetrics
import org.fcitx.fcitx5.android.input.keyboard.KeyboardHeightPercentBase.RealSize
import org.fcitx.fcitx5.android.input.keyboard.KeyboardWindow
import org.fcitx.fcitx5.android.input.picker.emojiPicker
import org.fcitx.fcitx5.android.input.picker.emoticonPicker
import org.fcitx.fcitx5.android.input.picker.symbolPicker
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.preedit.PreeditComponent
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.utils.unset
import org.fcitx.fcitx5.android.utils.windowManager
import org.mechdancer.dependency.DynamicScope
import org.mechdancer.dependency.manager.wrapToUniqueComponent
import org.mechdancer.dependency.plusAssign
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import timber.log.Timber

@SuppressLint("ViewConstructor")
class InputView(
    service: FcitxInputMethodService,
    fcitx: FcitxConnection,
    theme: Theme
) : BaseInputView(service, fcitx, theme) {

    private val keyBorder by ThemeManager.prefs.keyBorder

    private val customBackground = imageView {
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private var glassEffect: GlassWindowEffect? = null

    private val placeholderOnClickListener = OnClickListener { }

    // use clickable view as padding, so MotionEvent can be split to padding view and keyboard view
    private val leftPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val rightPaddingSpace = view(::View) {
        setOnClickListener(placeholderOnClickListener)
    }
    private val bottomPaddingSpace = view(::View) {
        // height as keyboardBottomPadding
        // bottomMargin as WindowInsets (Navigation Bar) offset
        setOnClickListener(placeholderOnClickListener)
    }

    private val scope = DynamicScope()
    private val broadcaster = InputBroadcaster()
    private val popup = PopupComponent()
    private val punctuation = PunctuationComponent()
    private val returnKeyDrawable = ReturnKeyDrawableComponent()
    private val preeditEmptyState = PreeditEmptyStateComponent()
    private val preedit = PreeditComponent()
    private val commonKeyActionListener = CommonKeyActionListener()
    private val windowManager = InputWindowManager()
    private val kawaiiBar = KawaiiBarComponent()
    private val horizontalCandidate = HorizontalCandidateComponent()
    private val keyboardWindow = KeyboardWindow()
    private val symbolPicker = symbolPicker()
    private val emojiPicker = emojiPicker()
    private val emoticonPicker = emoticonPicker()

    private fun setupScope() {
        scope += this@InputView.wrapToUniqueComponent()
        scope += service.wrapToUniqueComponent()
        scope += fcitx.wrapToUniqueComponent()
        scope += theme.wrapToUniqueComponent()
        scope += themedContext.wrapToUniqueComponent()
        scope += broadcaster
        scope += popup
        scope += punctuation
        scope += returnKeyDrawable
        scope += preeditEmptyState
        scope += preedit
        scope += commonKeyActionListener
        scope += windowManager
        scope += kawaiiBar
        scope += horizontalCandidate
        broadcaster.onScopeSetupFinished(scope)
    }

    private val keyboardPrefs = AppPrefs.getInstance().keyboard
    private val internalPrefs = AppPrefs.getInstance().internal
    private var compactPositionX = internalPrefs.compactKeyboardPositionX.getValue()
    private var compactPositionY = internalPrefs.compactKeyboardPositionY.getValue()
    private var dragPointerId = MotionEvent.INVALID_POINTER_ID
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragKeyboardLeft = 0
    private var dragKeyboardTop = 0
    private var dragging = false
    private val dragTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val dragHandle = view(::View) {
        val bar = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(2f)
            setColor(theme.keyTextColor)
        }
        addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val sideInset = ((right - left - dp(56)) / 2).coerceAtLeast(0)
            background = InsetDrawable(bar, sideInset, dp(10), sideInset, dp(10))
        }
        contentDescription = context.getString(R.string.compact_keyboard_drag_handle)
        isClickable = true
        setOnTouchListener { view, event -> onDragHandleTouch(view, event) }
    }

    private val focusChangeResetKeyboard by keyboardPrefs.focusChangeResetKeyboard

    private val keyboardHeightPercent = keyboardPrefs.keyboardHeightPercent
    private val keyboardHeightPercentLandscape = keyboardPrefs.keyboardHeightPercentLandscape
    private val keyboardSidePadding = keyboardPrefs.keyboardSidePadding
    private val keyboardSidePaddingLandscape = keyboardPrefs.keyboardSidePaddingLandscape
    private val keyboardBottomPadding = keyboardPrefs.keyboardBottomPadding
    private val keyboardBottomPaddingLandscape = keyboardPrefs.keyboardBottomPaddingLandscape

    private val advancedPrefs = AppPrefs.getInstance().advanced
    private val keyboardHeightPercentBase = advancedPrefs.keyboardHeightPercentBase

    private val keyboardSizePrefs = listOf(
        keyboardHeightPercent,
        keyboardHeightPercentLandscape,
        keyboardSidePadding,
        keyboardSidePaddingLandscape,
        keyboardBottomPadding,
        keyboardBottomPaddingLandscape,
        keyboardHeightPercentBase,
        keyboardPrefs.compactKeyboardLandscape,
        keyboardPrefs.compactKeyboardWidth,
        keyboardPrefs.compactKeyboardHeight,
        keyboardPrefs.compactKeyboardOnLeft,
    )

    val isCompactKeyboard: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            keyboardPrefs.compactKeyboardLandscape.getValue()

    private val keyboardHeightPx: Int
        get() {
            val baseType = keyboardHeightPercentBase.getValue()
            val base = when (baseType) {
                DisplayMetrics -> resources.displayMetrics.heightPixels
                RealSize -> Point().also {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        context.display
                    } else {
                        context.windowManager.defaultDisplay
                    }.getRealSize(it)
                }.y
            }
            val percent = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardHeightPercentLandscape
                else -> keyboardHeightPercent
            }.getValue()
            Timber.d("keyboardHeightPx get(): baseType=${baseType}, base=${base}, percent=${percent}")
            return base * percent / 100
        }

    private val keyboardSidePaddingPx: Int
        get() {
            if (isCompactKeyboard) return 0
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardSidePaddingLandscape
                else -> keyboardSidePadding
            }.getValue()
            return dp(value)
        }

    private val keyboardBottomPaddingPx: Int
        get() {
            if (isCompactKeyboard) return 0
            val value = when (resources.configuration.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> keyboardBottomPaddingLandscape
                else -> keyboardBottomPadding
            }.getValue()
            return dp(value)
        }

    @Keep
    private val onKeyboardSizeChangeListener = ManagedPreferenceProvider.OnChangeListener { key ->
        if (key == keyboardPrefs.compactKeyboardOnLeft.key) {
            compactPositionX = -1
            compactPositionY = 1000
            saveCompactPosition()
        }
        if (keyboardSizePrefs.any { it.key == key }) {
            updateKeyboardSize()
        }
    }

    val keyboardView: View

    init {
        // MUST call before any operation
        setupScope()

        // restore punctuation mapping in case of InputView recreation
        fcitx.launchOnReady {
            punctuation.updatePunctuationMapping(it.statusAreaActionsCached)
        }

        // make sure KeyboardWindow's view has been created before it receives any broadcast
        windowManager.addEssentialWindow(keyboardWindow, createView = true)
        windowManager.addEssentialWindow(symbolPicker)
        windowManager.addEssentialWindow(emojiPicker)
        windowManager.addEssentialWindow(emoticonPicker)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        broadcaster.onImeUpdate(fcitx.runImmediately { inputMethodEntryCached })

        customBackground.imageDrawable = theme.backgroundDrawable(keyBorder)
        if (theme.isEnhancedGlass && theme is Theme.Custom && theme.backgroundImage != null) {
            val alpha = ThemeManager.prefs.glassBackgroundAlpha.getValue()
            customBackground.imageAlpha = (255 - alpha).coerceAtLeast(10)
            customBackground.foreground = android.graphics.drawable.ColorDrawable(
                android.graphics.Color.argb(alpha / 2, 0, 0, 0))
        }

        keyboardView = constraintLayout {
            // allow MotionEvent to be delivered to keyboard while pressing on padding views.
            // although it should be default for apps targeting Honeycomb (3.0, API 11) and higher,
            // but it's not the case on some devices ... just set it here
            isMotionEventSplittingEnabled = true
            add(customBackground, lParams {
                centerVertically()
                centerHorizontally()
            })
            add(dragHandle, lParams(matchParent, dp(24)) {
                topOfParent()
                centerHorizontally()
            })
            add(kawaiiBar.view, lParams(matchParent, dp(KawaiiBarComponent.HEIGHT)) {
                below(dragHandle)
                centerHorizontally()
            })
            add(leftPaddingSpace, lParams {
                below(kawaiiBar.view)
                startOfParent()
                bottomOfParent()
            })
            add(rightPaddingSpace, lParams {
                below(kawaiiBar.view)
                endOfParent()
                bottomOfParent()
            })
            add(windowManager.view, lParams {
                below(kawaiiBar.view)
                above(bottomPaddingSpace)
                /**
                 * set start and end constrain in [updateKeyboardSize]
                 */
            })
            add(bottomPaddingSpace, lParams {
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
                bottomOfParent()
            })
        }

        if (theme.isEnhancedGlass) {
            val glassRadius = dp(ThemeManager.prefs.glassCornerRadius.getValue().toFloat())
            keyboardView.foreground = glassKeyboardBorder(theme.isDark, glassRadius,
                resources.displayMetrics.density)
            keyboardView.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, glassRadius)
                }
            }
            keyboardView.clipToOutline = true
        }

        add(preedit.ui.root, lParams(if (preedit.roundedBackground) wrapContent else 0, wrapContent) {
            above(keyboardView)
            startToStart = keyboardView.id
            endToEnd = keyboardView.id
            if (preedit.roundedBackground) {
                horizontalBias = 0f
                constrainedWidth = true
                marginStart = keyboardSidePaddingPx + dp(12)
                marginEnd = keyboardSidePaddingPx + dp(12)
                bottomMargin = dp(3)
            }
        })
        add(keyboardView, lParams(matchParent, wrapContent) {
            leftToLeft = LayoutParams.PARENT_ID
            rightToRight = LayoutParams.PARENT_ID
            topOfParent()
            bottomOfParent()
        })
        updateKeyboardSize()
        add(popup.root, lParams(matchParent, matchParent) {
            centerVertically()
            centerHorizontally()
        })

        keyboardPrefs.registerOnChangeListener(onKeyboardSizeChangeListener)
        advancedPrefs.registerOnChangeListener(onKeyboardSizeChangeListener)
    }

    private var compactInsetLeft = 0
    private var compactInsetRight = 0
    private var compactInsetBottom = 0
    private var compactInsetTop = 0
    private var navigationBottomInset = 0

    private fun compactHorizontalBias(): Float = if (compactPositionX < 0) {
        if (keyboardPrefs.compactKeyboardOnLeft.getValue()) 0f else 1f
    } else compactPositionX.coerceIn(0, 1000) / 1000f

    private fun saveCompactPosition() {
        internalPrefs.compactKeyboardPositionX.setValue(compactPositionX)
        internalPrefs.compactKeyboardPositionY.setValue(compactPositionY)
    }

    private fun onDragHandleTouch(handle: View, event: MotionEvent): Boolean {
        if (!isCompactKeyboard) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragPointerId = event.getPointerId(0)
                dragStartX = event.rawX
                dragStartY = event.rawY
                dragKeyboardLeft = keyboardView.left
                dragKeyboardTop = keyboardView.top
                dragging = false
                popup.dismissAll()
                handle.parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(dragPointerId)
                if (index < 0) return true
                // Convert the tracked pointer to screen coordinates on API 23+.
                val dx = event.getX(index) + event.rawX - event.x - dragStartX
                val dy = event.getY(index) + event.rawY - event.y - dragStartY
                if (!dragging && kotlin.math.abs(dx) < dragTouchSlop &&
                    kotlin.math.abs(dy) < dragTouchSlop) return true
                dragging = true
                keyboardView.updateLayoutParams<LayoutParams> {
                    val rangeX = (this@InputView.width - leftMargin - rightMargin - keyboardView.width).coerceAtLeast(0)
                    val rangeY = (this@InputView.height - topMargin - bottomMargin - keyboardView.height).coerceAtLeast(0)
                    horizontalBias = if (rangeX == 0) 0f else
                        ((dragKeyboardLeft + dx - leftMargin) / rangeX).coerceIn(0f, 1f)
                    verticalBias = if (rangeY == 0) 1f else
                        ((dragKeyboardTop + dy - topMargin) / rangeY).coerceIn(0f, 1f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finishDragging(event.actionMasked == MotionEvent.ACTION_UP)
                if (!dragging && event.actionMasked == MotionEvent.ACTION_UP) handle.performClick()
                handle.parent.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == dragPointerId) finishDragging(true)
            }
        }
        return true
    }

    private fun finishDragging(save: Boolean) {
        if (dragPointerId == MotionEvent.INVALID_POINTER_ID) return
        dragPointerId = MotionEvent.INVALID_POINTER_ID
        if (save && dragging) {
            val params = keyboardView.layoutParams as LayoutParams
            compactPositionX = (params.horizontalBias * 1000).toInt()
            compactPositionY = (params.verticalBias * 1000).toInt()
            saveCompactPosition()
        } else if (!save) {
            updateKeyboardSize()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) updateKeyboardSize()
    }

    private fun updateKeyboardSize() {
        popup.dismissAll()
        val compact = isCompactKeyboard
        val availableWidth = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val availableHeight = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        keyboardView.updateLayoutParams<LayoutParams> {
            topMargin = if (compact) compactInsetTop + dp(48) else 0
            leftMargin = if (compact) compactInsetLeft + dp(8) else 0
            rightMargin = if (compact) compactInsetRight + dp(8) else 0
            bottomMargin = if (compact) compactInsetBottom + dp(8) else 0
            width = if (compact) {
                dp(keyboardPrefs.compactKeyboardWidth.getValue())
                    .coerceAtMost((availableWidth - leftMargin - rightMargin).coerceAtLeast(0))
            } else matchParent
            horizontalBias = if (compact) compactHorizontalBias() else 0f
            verticalBias = if (compact) compactPositionY.coerceIn(0, 1000) / 1000f else 1f
        }
        dragHandle.visibility = if (compact) VISIBLE else GONE
        windowManager.view.updateLayoutParams {
            height = if (compact) {
                val total = (availableHeight * keyboardPrefs.compactKeyboardHeight.getValue() / 100)
                    .coerceAtLeast(dp(200))
                    .coerceAtMost((availableHeight - compactInsetTop - compactInsetBottom - dp(64)).coerceAtLeast(0))
                (total - dp(KawaiiBarComponent.HEIGHT) - dp(24)).coerceAtLeast(0)
            } else keyboardHeightPx
        }
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            height = keyboardBottomPaddingPx
            bottomMargin = if (compact) 0 else navigationBottomInset
        }
        val sidePadding = keyboardSidePaddingPx
        if (sidePadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = GONE
            rightPaddingSpace.visibility = GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = VISIBLE
            rightPaddingSpace.visibility = VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        if (preedit.roundedBackground) {
            preedit.ui.root.updateLayoutParams<LayoutParams> {
                marginStart = sidePadding + dp(12)
                marginEnd = sidePadding + dp(12)
            }
        } else {
            preedit.ui.root.setPadding(sidePadding, 0, sidePadding, 0)
        }
        kawaiiBar.view.setPadding(sidePadding, 0, sidePadding, 0)
        kawaiiBar.updateCompactKeyboardState(compact)
        // Layout changes trigger the framework's insets computation as well.
        requestLayout()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val safeInsets = WindowInsetsCompat.toWindowInsetsCompat(insets).getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        compactInsetLeft = safeInsets.left
        compactInsetRight = safeInsets.right
        compactInsetTop = safeInsets.top
        compactInsetBottom = safeInsets.bottom
        navigationBottomInset = getNavBarBottomInset(insets)
        updateKeyboardSize()
        return insets
    }

    /**
     * called when [InputView] is about to show, or restart
     */
    fun startInput(info: EditorInfo, capFlags: CapabilityFlags, restarting: Boolean = false) {
        broadcaster.onStartInput(info, capFlags)
        returnKeyDrawable.updateDrawableOnEditorInfo(info)
        if (focusChangeResetKeyboard || !restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    override fun onStartHandleFcitxEvent() {
        val inputPanelData = fcitx.runImmediately { inputPanelCached }
        val inputMethodEntry = fcitx.runImmediately { inputMethodEntryCached }
        val statusAreaActions = fcitx.runImmediately { statusAreaActionsCached }
        arrayOf(
            FcitxEvent.InputPanelEvent(inputPanelData),
            FcitxEvent.IMChangeEvent(inputMethodEntry),
            FcitxEvent.StatusAreaEvent(
                FcitxEvent.StatusAreaEvent.Data(statusAreaActions, inputMethodEntry)
            )
        ).forEach { handleFcitxEvent(it) }
    }

    override fun handleFcitxEvent(it: FcitxEvent<*>) {
        when (it) {
            is FcitxEvent.CandidateListEvent -> {
                broadcaster.onCandidateUpdate(it.data)
            }
            is FcitxEvent.ClientPreeditEvent -> {
                preeditEmptyState.updatePreeditEmptyState(clientPreedit = it.data)
                broadcaster.onClientPreeditUpdate(it.data)
            }
            is FcitxEvent.InputPanelEvent -> {
                preeditEmptyState.updatePreeditEmptyState(preedit = it.data.preedit)
                broadcaster.onInputPanelUpdate(it.data)
            }
            is FcitxEvent.IMChangeEvent -> {
                broadcaster.onImeUpdate(it.data)
            }
            is FcitxEvent.StatusAreaEvent -> {
                punctuation.updatePunctuationMapping(it.data.actions)
                broadcaster.onStatusAreaUpdate(it.data.actions)
            }
            else -> {}
        }
    }

    fun updateSelection(start: Int, end: Int) {
        broadcaster.onSelectionUpdate(start, end)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean {
        return kawaiiBar.handleInlineSuggestions(response)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (theme.isEnhancedGlass && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.window.window?.let { window ->
                glassEffect = GlassWindowEffect(window, keyboardView).also { it.attach() }
            }
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            glassEffect?.detach()
            glassEffect = null
        }
        advancedPrefs.unregisterOnChangeListener(onKeyboardSizeChangeListener)
        keyboardPrefs.unregisterOnChangeListener(onKeyboardSizeChangeListener)
        // clear DynamicScope, implies that InputView should not be attached again after detached.
        scope.clear()
        super.onDetachedFromWindow()
    }

}
