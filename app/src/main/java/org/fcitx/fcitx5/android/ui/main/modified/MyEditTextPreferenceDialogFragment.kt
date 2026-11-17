/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.ui.main.modified

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.utils.materialTextInput
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.verticalMargin

class MyEditTextPreferenceDialogFragment : EditTextPreferenceDialogFragmentCompat() {
    override fun onCreateDialogView(context: Context): View = context.verticalLayout {
        add(textView {
            id = android.R.id.message
        }, lParams(matchParent) {
            horizontalMargin = dp(24)
            verticalMargin = dp(8)
        })
        val (inputLayout, input) = context.materialTextInput {
            hint = preference.title
        }
        input.id = android.R.id.edit
        add(inputLayout, lParams(matchParent) {
            horizontalMargin = dp(24)
            verticalMargin = dp(8)
        })
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        val p = preference as EditTextPreference
        builder.setNeutralButton(R.string.default_) { _, _ ->
            p.restore()
        }
        super.onPrepareDialogBuilder(builder)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        fixDialogMargin(view)
    }

    companion object {
        fun newInstance(key: String): MyEditTextPreferenceDialogFragment {
            val fragment = MyEditTextPreferenceDialogFragment()
            val b = Bundle(1)
            b.putString(ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}