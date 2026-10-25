/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

object FileSplitMergeHelper {

    fun showSplitDialog(activity: Activity, fileItem: FileItem, onComplete: () -> Unit) {
        val srcFile = fileItem.path.toFile()
        if (!srcFile.exists() || srcFile.isDirectory) {
            Toast.makeText(activity, "Cannot split directories", Toast.LENGTH_SHORT).show()
            return
        }

        val totalLength = srcFile.length()
        val context = activity

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val infoText = TextView(context).apply {
            text = "${srcFile.name} (${formatSize(totalLength)})"
            textSize = 15f
            setPadding(0, 0, 0, 16)
        }
        container.addView(infoText)

        val radioGroup = RadioGroup(context)
        val sizes = listOf(
            5L * 1024 * 1024 to "5 MB",
            10L * 1024 * 1024 to "10 MB",
            20L * 1024 * 1024 to "20 MB",
            50L * 1024 * 1024 to "50 MB",
            100L * 1024 * 1024 to "100 MB"
        )

        var selectedSize = sizes[1].first // default 10MB
        val rbIdMap = mutableMapOf<Int, Long>()

        for ((sizeBytes, label) in sizes) {
            val viewId = android.view.View.generateViewId()
            rbIdMap[viewId] = sizeBytes
            val rb = RadioButton(context).apply {
                text = label
                id = viewId
                if (sizeBytes == selectedSize) isChecked = true
                setOnClickListener { selectedSize = sizeBytes }
            }
            radioGroup.addView(rb)
        }

        val customId = android.view.View.generateViewId()
        val rbCustom = RadioButton(context).apply {
            text = "Custom (MB)"
            id = customId
        }
        radioGroup.addView(rbCustom)

        val customInput = EditText(context).apply {
            hint = "e.g. 15"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            visibility = android.view.View.GONE
        }
        container.addView(radioGroup)
        container.addView(customInput)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == customId) {
                customInput.visibility = android.view.View.VISIBLE
            } else {
                customInput.visibility = android.view.View.GONE
                rbIdMap[checkedId]?.let { selectedSize = it }
            }
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.split_file_title)
            .setView(container)
            .setPositiveButton(R.string.split_file_btn_split) { _, _ ->
                val finalSize = if (rbCustom.isChecked) {
                    val mb = customInput.text.toString().toLongOrNull() ?: 10L
                    (mb * 1024 * 1024).coerceAtLeast(1024L)
                } else {
                    selectedSize
                }
                executeSplit(activity, srcFile, finalSize, onComplete)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun executeSplit(activity: Activity, srcFile: File, partSizeBytes: Long, onComplete: () -> Unit) {
        val progress = ProgressDialog(activity).apply {
            setMessage(activity.getString(R.string.split_file_progress))
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            var partIndex = 1
            var totalParts = 0
            try {
                FileInputStream(srcFile).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var currentPartBytes = 0L
                    var partFile = File(srcFile.parentFile, "${srcFile.name}.part$partIndex")
                    var output = FileOutputStream(partFile)

                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        currentPartBytes += bytesRead

                        if (currentPartBytes >= partSizeBytes) {
                            output.flush()
                            output.close()
                            partIndex++
                            currentPartBytes = 0L
                            partFile = File(srcFile.parentFile, "${srcFile.name}.part$partIndex")
                            output = FileOutputStream(partFile)
                        }
                    }
                    output.flush()
                    output.close()
                    totalParts = partIndex
                }

                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.split_file_success, totalParts),
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(activity, "Split failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun isSplitPart(fileItem: FileItem): Boolean {
        val name = fileItem.name.lowercase(Locale.ROOT)
        return name.contains(Regex("\\.part\\d+$")) || name.contains(Regex("\\.\\d{3}$"))
    }

    private fun extractPartNumber(name: String): Int {
        val partMatch = Regex("\\.part(\\d+)$", RegexOption.IGNORE_CASE).find(name)
        if (partMatch != null) {
            return partMatch.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
        }
        val numMatch = Regex("\\.(\\d{3})$").find(name)
        if (numMatch != null) {
            return numMatch.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
        }
        return Int.MAX_VALUE
    }

    fun findSplitParts(firstPart: FileItem, allFiles: List<FileItem>): List<FileItem> {
        val name = firstPart.name
        val baseName: String
        val isPartFormat: Boolean

        if (name.contains(Regex("\\.part\\d+$", RegexOption.IGNORE_CASE))) {
            baseName = name.substringBeforeLast(".part")
            isPartFormat = true
        } else if (name.contains(Regex("\\.\\d{3}$"))) {
            baseName = name.substringBeforeLast(".")
            isPartFormat = false
        } else {
            return listOf(firstPart)
        }

        return allFiles.filter {
            val siblingName = it.name
            if (isPartFormat) {
                siblingName.startsWith(baseName + ".part", ignoreCase = true)
            } else {
                siblingName.startsWith(baseName + ".", ignoreCase = true) && siblingName.substringAfterLast(".").matches(Regex("\\d{3}"))
            }
        }.sortedWith(compareBy({ extractPartNumber(it.name) }, { it.name.lowercase(Locale.ROOT) }))
    }

    fun showMergeDialog(activity: Activity, firstPart: FileItem, allFiles: List<FileItem>, onComplete: () -> Unit) {
        val parts = findSplitParts(firstPart, allFiles)
        if (parts.isEmpty()) {
            Toast.makeText(activity, "No parts found to merge", Toast.LENGTH_SHORT).show()
            return
        }

        val firstName = firstPart.name
        val targetName = if (firstName.contains(Regex("\\.part\\d+$", RegexOption.IGNORE_CASE))) {
            firstName.substringBeforeLast(".part")
        } else if (firstName.contains(Regex("\\.\\d{3}$"))) {
            firstName.substringBeforeLast(".")
        } else {
            firstName + ".merged"
        }

        var totalSize = 0L
        for (part in parts) {
            totalSize += part.attributes.size()
        }

        val context = activity
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val infoText = TextView(context).apply {
            text = context.getString(R.string.merge_files_parts_detected, parts.size, formatSize(totalSize))
            textSize = 15f
            setPadding(0, 0, 0, 16)
        }
        container.addView(infoText)

        val nameLabel = TextView(context).apply {
            text = context.getString(R.string.merge_files_target_label)
            textSize = 13f
        }
        container.addView(nameLabel)

        val nameInput = EditText(context).apply {
            setText(targetName)
        }
        container.addView(nameInput)

        val checkDelete = CheckBox(context).apply {
            text = context.getString(R.string.merge_files_delete_parts)
            isChecked = false
            setPadding(0, 16, 0, 0)
        }
        container.addView(checkDelete)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.merge_files_title)
            .setView(container)
            .setPositiveButton(R.string.merge_files_btn_merge) { _, _ ->
                val finalName = nameInput.text.toString().trim().ifEmpty { targetName }
                val destFile = File(firstPart.path.toFile().parentFile, finalName)
                executeMerge(activity, parts, destFile, checkDelete.isChecked, onComplete)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun executeMerge(
        activity: Activity,
        parts: List<FileItem>,
        destFile: File,
        deleteParts: Boolean,
        onComplete: () -> Unit
    ) {
        val progress = ProgressDialog(activity).apply {
            setMessage(activity.getString(R.string.merge_files_progress))
            setCancelable(false)
            show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    for (part in parts) {
                        val partFile = part.path.toFile()
                        FileInputStream(partFile).use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    output.flush()
                }

                if (deleteParts) {
                    for (part in parts) {
                        try {
                            part.path.toFile().delete()
                        } catch (ignored: Exception) {}
                    }
                }

                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.merge_files_success, destFile.name),
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progress.dismiss()
                    Toast.makeText(activity, "Merge failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
