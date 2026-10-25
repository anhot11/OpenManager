/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.toFile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportFileListHelper {

    fun showDialog(
        activity: Activity,
        currentDir: Path,
        allFiles: List<FileItem>,
        selectedFiles: List<FileItem>?,
        onComplete: () -> Unit
    ) {
        val filesToExport = if (!selectedFiles.isNullOrEmpty()) {
            selectedFiles.filter { !it.isParentDirectoryItem }
        } else {
            allFiles.filter { !it.isParentDirectoryItem }
        }

        if (filesToExport.isEmpty()) {
            Toast.makeText(activity, "No files to export", Toast.LENGTH_SHORT).show()
            return
        }

        val context = activity
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val scopeInfo = TextView(context).apply {
            text = if (!selectedFiles.isNullOrEmpty()) {
                context.getString(R.string.export_file_list_selected, filesToExport.size)
            } else {
                context.getString(R.string.export_file_list_all, filesToExport.size)
            }
            textSize = 15f
            setPadding(0, 0, 0, 16)
        }
        container.addView(scopeInfo)

        val formatLabel = TextView(context).apply {
            text = context.getString(R.string.export_file_list_format)
            textSize = 13f
        }
        container.addView(formatLabel)

        val radioGroup = RadioGroup(context)
        val formats = listOf(
            0 to context.getString(R.string.export_file_list_names_only),
            1 to context.getString(R.string.export_file_list_names_sizes),
            2 to context.getString(R.string.export_file_list_full_paths),
            3 to context.getString(R.string.export_file_list_full_details)
        )

        var selectedFormat = 0
        for ((id, label) in formats) {
            val rb = RadioButton(context).apply {
                text = label
                this.id = id
                if (id == 0) isChecked = true
            }
            radioGroup.addView(rb)
        }
        container.addView(radioGroup)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedFormat = checkedId
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.export_file_list_title)
            .setView(container)
            .setPositiveButton(R.string.export_file_list_copy_clipboard) { _, _ ->
                val text = generateExportText(filesToExport, selectedFormat)
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("File List", text))
                Toast.makeText(
                    activity,
                    context.getString(R.string.export_file_list_copied, filesToExport.size),
                    Toast.LENGTH_SHORT
                ).show()
                onComplete()
            }
            .setNeutralButton(R.string.export_file_list_save_file) { _, _ ->
                val text = generateExportText(filesToExport, selectedFormat)
                val destFile = File(currentDir.toFile(), "file_list.txt")
                try {
                    FileOutputStream(destFile).use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(
                        activity,
                        context.getString(R.string.export_file_list_saved, destFile.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    onComplete()
                } catch (e: Exception) {
                    Toast.makeText(activity, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun generateExportText(files: List<FileItem>, format: Int): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()

        for (file in files) {
            val name = file.name
            val size = formatSize(file.attributes.size)
            val path = file.path.toFile().absolutePath
            val date = dateFormat.format(Date(file.attributes.lastModifiedTime.toMillis()))

            when (format) {
                0 -> sb.appendLine(name)
                1 -> sb.appendLine("$name\t$size")
                2 -> sb.appendLine(path)
                3 -> sb.appendLine("$name\t$size\t$date\t$path")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes.toDouble() / 1024
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024
        return String.format("%.2f GB", gb)
    }
}
