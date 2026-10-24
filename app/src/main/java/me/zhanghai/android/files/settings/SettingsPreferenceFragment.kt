/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Build
import android.os.Bundle
import me.zhanghai.android.files.R
import me.zhanghai.android.files.theme.custom.CustomThemeHelper
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.theme.night.NightModeHelper
import me.zhanghai.android.files.ui.PreferenceFragmentCompat
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.startActivitySafe

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var localePreference: LocalePreference

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        localePreference = preferenceScreen.findPreference(getString(R.string.pref_key_locale))!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            localePreference.setApplicationLocalesPre33 = { locales ->
                val activity = requireActivity() as SettingsActivity
                activity.setApplicationLocalesPre33(locales)
            }
        }

        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_install_shizuku))?.setOnPreferenceClickListener {
            requireContext().startActivitySafe(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/")))
            true
        }

        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_check_for_update))?.setOnPreferenceClickListener {
            requireContext().startActivitySafe(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/anhot11/OpenManager")))
            true
        }

        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_about_settings))?.setOnPreferenceClickListener {
            requireContext().startActivitySafe(me.zhanghai.android.files.about.AboutActivity::class.createIntent())
            true
        }

        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_acknowledgement))?.setOnPreferenceClickListener {
            requireContext().startActivitySafe(me.zhanghai.android.files.about.AboutActivity::class.createIntent())
            true
        }

        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_user_agreement))?.setOnPreferenceClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_user_agreement_title)
                .setMessage("OpenManager\n\n100% Free & Open Source Software (GPLv3).\n\nOpenManager does not collect, transmit, or share any personal data, analytics, or telemetry. All file operations remain entirely local to your device.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }

        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_cloud_backup))?.setOnPreferenceClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_cloud_backup_title)
                .setMessage(R.string.settings_cloud_backup_summary)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }

        findPreference<androidx.preference.Preference>(getString(R.string.pref_key_cloud_restore))?.setOnPreferenceClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_cloud_restore_title)
                .setMessage(R.string.settings_cloud_restore_summary)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val viewLifecycleOwner = viewLifecycleOwner
        // The following may end up passing the same lambda instance to the observer because it has
        // no capture, and result in an IllegalArgumentException "Cannot add the same observer with
        // different lifecycles" if activity is finished and instantly started again. To work around
        // this, always use an instance method reference.
        // https://stackoverflow.com/a/27524543
        //Settings.THEME_COLOR.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.NIGHT_MODE.observe(viewLifecycleOwner) { NightModeHelper.sync() }
        //Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        Settings.THEME_COLOR.observe(viewLifecycleOwner, this::onThemeColorChanged)
        Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner, this::onMaterialDesign3Changed)
        Settings.NIGHT_MODE.observe(viewLifecycleOwner, this::onNightModeChanged)
        Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner, this::onBlackNightModeChanged)
    }

    private fun onThemeColorChanged(themeColor: ThemeColor) {
        CustomThemeHelper.sync()
    }

    private fun onMaterialDesign3Changed(isMaterialDesign3: Boolean) {
        CustomThemeHelper.sync()
    }

    private fun onNightModeChanged(nightMode: NightMode) {
        NightModeHelper.sync()
    }

    private fun onBlackNightModeChanged(blackNightMode: Boolean) {
        CustomThemeHelper.sync()
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Refresh locale preference summary because we aren't notified for an external change
            // between system default and the locale that's the current system default.
            localePreference.notifyChanged()
        }
    }
}
