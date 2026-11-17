/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.dialog

import android.content.Context
import android.text.TextUtils
import android.view.ViewGroup
import com.google.android.material.R
import com.google.android.material.radiobutton.MaterialRadioButton
import splitties.dimensions.dp
import splitties.resources.styledDrawable
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityVerticalCenter
import splitties.views.textAppearance

// The adapter owns the checked state; a single radio row is the complete touch target.
class InputMethodEntryUi(override val ctx: Context) : Ui {
    override val root = MaterialRadioButton(ctx).apply {
        gravity = gravityVerticalCenter
        minHeight = dp(56)
        textAppearance = R.style.TextAppearance_Material3_BodyLarge
        setPaddingRelative(
            dp(24), 0,
            dp(24), 0
        )
        compoundDrawablePadding = dp(16)
        ellipsize = TextUtils.TruncateAt.MARQUEE
        background = styledDrawable(android.R.attr.selectableItemBackground)
        isFocusable = true
        isClickable = true
        layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)
    }
}
