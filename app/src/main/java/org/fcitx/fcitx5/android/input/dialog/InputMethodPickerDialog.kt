/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.dialog

import android.content.Context
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxAPI
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.utils.AppUtil
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.verticalLayoutManager
import splitties.views.topPadding

object InputMethodPickerDialog {
    suspend fun build(
        fcitx: FcitxAPI,
        service: FcitxInputMethodService,
        context: Context
    ): AlertDialog {
        val entries = InputMethodData.resolve(fcitx, service)
        val enabledIM = fcitx.inputMethodEntryCached.uniqueName
        val enabledIndex = entries.indexOfFirst { it.uniqueName == enabledIM }
        val dividerIndex = entries.indexOfFirst { it.ime }
        lateinit var dialog: AlertDialog
        val builder = MaterialAlertDialogBuilder(context.inputMethodDialogContext())
        dialog = builder
            .setTitle(R.string.choose_input_method)
            .setView(builder.context.recyclerView {
                layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)
                topPadding = dp(8)
                layoutManager = verticalLayoutManager()
                adapter = InputMethodListAdapter(entries, enabledIndex) {
                    val (uniqueName, _, ime) = it
                    if (ime) service.switchInputMethod(uniqueName)
                    else service.lifecycleScope.launch { fcitx.activateIme(uniqueName) }
                    dialog.dismiss()
                }
                drawable(R.drawable.input_method_divider)?.let {
                    addItemDecoration(SingleDividerDecoration(it, dividerIndex))
                }
            })
            .setNeutralButton(R.string.input_methods) { _, _ ->
                AppUtil.launchMainToInputMethodList(context)
            }
            .create()
        return dialog
    }
}
