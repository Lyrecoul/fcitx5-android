/**
 * Copyright (C) 2021-2023 Fcitx 5 for Android Contributors
 * SPDX-License-Identifier: LGPL-2.1-or-later
 */

package org.fcitx.fcitx5.android.lib.plugin_base

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.License
import org.xmlpull.v1.XmlPullParser
import kotlin.math.max

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_plugin_about)
        val root = findViewById<View>(R.id.plugin_about_root)
        val toolbar = findViewById<MaterialToolbar>(R.id.plugin_about_toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = bars.left, right = bars.right)
            toolbar.updatePadding(top = bars.top)
            insets
        }
        root.doOnAttach { ViewCompat.requestApplyInsets(it) }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.plugin_about_content, AboutContentFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(R.string.app_info).setOnMenuItemClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                setData(Uri.fromParts("package", packageName, null))
            })
            true
        }
        menu.add(R.string.uninstall).setOnMenuItemClickListener {
            startActivity(Intent(Intent.ACTION_DELETE).apply {
                setData(Uri.fromParts("package", packageName, null))
            })
            true
        }
        return super.onCreateOptionsMenu(menu)
    }

    class AboutContentFragment : PreferenceFragmentCompat() {

        private val copyPreferenceSummaryListener = OnPreferenceClickListener {
            it.context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("", it.summary))
            Toast.makeText(it.context, R.string.copied, Toast.LENGTH_SHORT).show()
            true
        }

        private fun PreferenceScreen.addCategory(
            title: Int,
            init: PreferenceCategory.() -> Unit
        ) {
            PreferenceCategory(context).also {
                it.title = getString(title)
                it.isIconSpaceReserved = false
                addPreference(it)
                init.invoke(it)
            }
        }

        private fun PreferenceCategory.addPreference(
            title: String,
            summary: String,
            onClick: OnPreferenceClickListener? = null
        ) {
            addPreference(Preference(context).apply {
                setTitle(title)
                setSummary(summary)
                isSingleLineTitle = false
                isIconSpaceReserved = false
                isPersistent = false
                onPreferenceClickListener = onClick ?: copyPreferenceSummaryListener
            })
        }

        private fun PreferenceCategory.addPreference(
            title: Int,
            summary: String,
            onClick: OnPreferenceClickListener? = null
        ) {
            addPreference(getString(title), summary, onClick)
        }

        @SuppressLint("DiscouragedApi")
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            // The debug About activity is also available before the user unlocks the device.
            preferenceManager.setStorageDeviceProtected()
            val context = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(context)
            preferenceScreen = screen
            val pkg = context.packageName
            val pluginXmlRes = resources.getIdentifier("plugin", "xml", pkg)
            if (pluginXmlRes == 0) return
            var gettextDomain = ""
            var apiVersion = ""
            var description = ""
            var hasService = false
            resources.getXml(pluginXmlRes).use { parser ->
                var eventType = parser.eventType
                var text = ""
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.TEXT -> text = parser.text
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "apiVersion" -> apiVersion = text
                            "domain" -> gettextDomain = text
                            "description" -> description = text.let {
                                if (it.startsWith("@string/")) {
                                    val resName = it.removePrefix("@string/")
                                    val resId = resources.getIdentifier(resName, "string", pkg)
                                    if (resId != 0) getString(resId) else it
                                } else it
                            }
                            "hasService" -> hasService = text.lowercase() == "true"
                        }
                    }
                    eventType = parser.next()
                }
            }
            screen.addCategory(R.string.plugin_info) {
                addPreference(R.string.pkg_name, pkg)
                addPreference(R.string.api_version, apiVersion)
                if (gettextDomain.isNotBlank()) {
                    addPreference(R.string.gettext_domain, gettextDomain)
                }
                addPreference(R.string.plugin_description, description)
                addPreference(R.string.has_service, hasService.toString())
            }
            resources.getIdentifier("aboutlibraries", "raw", pkg).also { resId ->
                if (resId == 0) return@also
                val jsonString = resources.openRawResource(resId)
                    .bufferedReader()
                    .use { reader -> reader.readText() }
                val libraries = Libs.Builder()
                    .withJson(jsonString)
                    .build()
                    .libraries
                    .sortedBy {
                        if (it.tag == "native") it.uniqueId.uppercase() else it.uniqueId.lowercase()
                    }
                screen.addCategory(R.string.licenses) {
                    libraries.forEach {
                        addPreference(
                            title = "${it.uniqueId}:${it.artifactVersion}",
                            summary = it.licenses.joinToString { l -> l.spdxId ?: l.name }
                        ) { _ ->
                            showLicenseDialog(it.uniqueId, it.licenses)
                        }
                    }
                }
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            val list = listView
            val bottomPadding = list.paddingBottom
            list.clipToPadding = false
            ViewCompat.setOnApplyWindowInsetsListener(list) { recyclerView, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val gestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
                recyclerView.updatePadding(bottom = bottomPadding + max(bars.bottom, gestures.bottom))
                insets
            }
            list.doOnAttach { ViewCompat.requestApplyInsets(it) }
        }

        private fun showLicenseDialog(uniqueId: String, licenses: Set<License>): Boolean {
            when (licenses.size) {
                0 -> {}
                1 -> showLicenseContent(licenses.first())
                else -> {
                    val licenseArray = licenses.toTypedArray()
                    val licenseNames = licenseArray.map { it.spdxId ?: it.name }.toTypedArray()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(uniqueId)
                        .setItems(licenseNames) { _, idx ->
                            showLicenseContent(licenseArray[idx])
                        }
                        .setPositiveButton(android.R.string.cancel, null)
                        .show()
                }
            }
            return true
        }

        private fun showLicenseContent(license: License) {
            if (license.url?.isNotBlank() == true) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(license.url)))
            }
        }
    }

}
