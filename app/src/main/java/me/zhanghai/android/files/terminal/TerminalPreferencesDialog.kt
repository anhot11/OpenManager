/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.terminal

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R

class TerminalPreferencesDialog : DialogFragment() {

    var onSettingsChanged: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val builder = MaterialAlertDialogBuilder(ctx)
        builder.setTitle(R.string.terminal_pref_title)

        val container = LayoutInflater.from(ctx).inflate(
            R.layout.terminal_preferences_dialog,
            null,
            false
        )

        val textStartPathValue = container.findViewById<TextView>(R.id.textStartPathValue)
        val textFontSizeValue = container.findViewById<TextView>(R.id.textFontSizeValue)
        val textExpansionValue = container.findViewById<TextView>(R.id.textExpansionValue)
        val switchBlackTheme = container.findViewById<SwitchCompat>(R.id.switchBlackTheme)
        val switchRootSession = container.findViewById<SwitchCompat>(R.id.switchRootSession)

        // Init values
        textStartPathValue.text = TerminalSettings.getStartPath(ctx)
        textFontSizeValue.text = "${TerminalSettings.getFontSize(ctx)}sp"
        switchBlackTheme.isChecked = TerminalSettings.isKeepBlackTheme(ctx)
        switchRootSession.isChecked = TerminalSettings.isRootSession(ctx)

        val isInstalled = TerminalExpansionManager.isTermexInstalled(ctx)
        if (isInstalled) {
            val ver = TerminalExpansionManager.getTermexVersion(ctx) ?: "v3.8 arm64"
            textExpansionValue.text = getString(R.string.terminal_pref_expansion_installed) + " ($ver)"
            textExpansionValue.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            textExpansionValue.text = getString(R.string.terminal_pref_expansion_not_installed)
            textExpansionValue.setTextColor(Color.parseColor("#FF9800"))
        }

        // Start path click
        container.findViewById<View>(R.id.layoutStartPath).setOnClickListener {
            val edit = EditText(ctx).apply {
                setText(TerminalSettings.getStartPath(ctx))
                setSelection(text.length)
            }
            val frame = FrameLayout(ctx).apply {
                val pad = (16 * resources.displayMetrics.density).toInt()
                setPadding(pad, 0, pad, 0)
                addView(edit)
            }
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.terminal_pref_start_path)
                .setView(frame)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val path = edit.text.toString().trim()
                    if (path.isNotEmpty()) {
                        TerminalSettings.setStartPath(ctx, path)
                        textStartPathValue.text = path
                        onSettingsChanged?.invoke()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Font size click
        container.findViewById<View>(R.id.layoutFontSize).setOnClickListener {
            val sizes = arrayOf("11sp", "12sp", "13sp", "14sp", "15sp", "16sp", "18sp", "20sp", "24sp")
            val current = TerminalSettings.getFontSize(ctx)
            var selectedIdx = sizes.indexOfFirst { it.startsWith(current.toString()) }
            if (selectedIdx < 0) selectedIdx = 3

            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.terminal_pref_font_size)
                .setSingleChoiceItems(sizes, selectedIdx) { dialog, which ->
                    val sizeVal = sizes[which].replace("sp", "").toIntOrNull() ?: 14
                    TerminalSettings.setFontSize(ctx, sizeVal)
                    textFontSizeValue.text = "${sizeVal}sp"
                    onSettingsChanged?.invoke()
                    dialog.dismiss()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Black theme toggle
        switchBlackTheme.setOnCheckedChangeListener { _, isChecked ->
            TerminalSettings.setKeepBlackTheme(ctx, isChecked)
            onSettingsChanged?.invoke()
        }

        // Root session toggle
        switchRootSession.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                if (!TerminalExpansionManager.isRootAvailable()) {
                    buttonView.isChecked = false
                    Toast.makeText(ctx, R.string.terminal_pref_root_not_found, Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
            }
            TerminalSettings.setRootSession(ctx, isChecked)
            onSettingsChanged?.invoke()
        }

        // Expansion pack click
        container.findViewById<View>(R.id.layoutExpansion).setOnClickListener {
            if (!TerminalExpansionManager.isTermexInstalled(ctx)) {
                val ok = TerminalExpansionManager.installTermexApk(ctx)
                if (!ok) {
                    Toast.makeText(
                        ctx,
                        "Por favor coloque termex_v3008_arm64.apk en /sdcard/Download/",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(ctx, "Paquete de expansión ya instalado y activo", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setView(container)
        builder.setPositiveButton(android.R.string.ok, null)
        return builder.create()
    }
}
