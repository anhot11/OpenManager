/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.show

class CreateFileDialogFragment : FileNameDialogFragment() {
    override val listener: Listener
        get() = super.listener as Listener

    @StringRes
    override val titleRes: Int = R.string.file_create_file_title

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        binding.nameLayout.apply {
            setStartIconDrawable(R.drawable.file_icon_white_24dp)
            setStartIconTintList(ColorStateList.valueOf(0xFF42A5F5.toInt()))
            hint = "Nombre del archivo"
            helperText = "Toca una extensión abajo para añadirla rápidamente"
        }
        setupExtensionChips()
        return dialog
    }

    private fun setupExtensionChips() {
        val scroll = binding.extensionsScrollView ?: return
        val layout = binding.extensionsLayout ?: return
        scroll.visibility = View.VISIBLE
        layout.removeAllViews()

        val extensions = listOf(
            ".txt", ".json", ".xml", ".java", ".kt",
            ".smali", ".sh", ".md", ".html", ".c",
            ".cpp", ".py", ".log", ".bin", ".apk"
        )
        val density = resources.displayMetrics.density
        val marginEnd = (6 * density).toInt()

        for (ext in extensions) {
            val chip = TextView(requireContext()).apply {
                text = ext
                textSize = 12.5f
                setTextColor(0xFFE0E0E0.toInt())
                setBackgroundResource(R.drawable.bg_extension_chip)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, marginEnd, 0)
                }
                setOnClickListener {
                    applyExtension(ext)
                }
            }
            layout.addView(chip)
        }
    }

    private fun applyExtension(ext: String) {
        val edit = binding.nameEdit
        val currentText = edit.text?.toString() ?: ""
        if (currentText.isEmpty()) {
            val defaultName = "nuevo$ext"
            edit.setText(defaultName)
            edit.setSelection(0, "nuevo".length)
        } else {
            val dot = currentText.lastIndexOf('.')
            val base = if (dot > 0) currentText.substring(0, dot) else currentText
            val newText = base + ext
            edit.setText(newText)
            edit.setSelection(0, base.length)
        }
    }

    override fun onOk(name: String) {
        listener.createFile(name)
    }

    companion object {
        fun show(fragment: Fragment) {
            CreateFileDialogFragment().show(fragment)
        }
    }

    interface Listener : FileNameDialogFragment.Listener {
        fun createFile(name: String)
    }
}
