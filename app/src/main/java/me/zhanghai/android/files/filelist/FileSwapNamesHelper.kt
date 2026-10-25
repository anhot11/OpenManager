/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Context
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
import java.io.IOException

object FileSwapNamesHelper {

    fun swap(context: Context, pathA: Path, pathB: Path, onComplete: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileA = pathA.toFile()
                val fileB = pathB.toFile()

                val tempFile = File(fileA.parentFile, ".swap_tmp_${System.currentTimeMillis()}_${fileA.name}")

                if (!fileA.renameTo(tempFile)) {
                    throw IOException("Could not rename first file to temp")
                }
                if (!fileB.renameTo(fileA)) {
                    tempFile.renameTo(fileA)
                    throw IOException("Could not rename second file to first file")
                }
                if (!tempFile.renameTo(fileB)) {
                    fileA.renameTo(fileB)
                    tempFile.renameTo(fileA)
                    throw IOException("Could not rename temp file to second file")
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.swap_file_names_success, Toast.LENGTH_SHORT).show()
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.swap_file_names_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun showPickSecondFileDialog(
        activity: Activity,
        firstFile: FileItem,
        candidateFiles: List<FileItem>,
        onComplete: () -> Unit
    ) {
        val candidates = candidateFiles.filter { it.path != firstFile.path && !it.isParentDirectoryItem }
        if (candidates.isEmpty()) {
            Toast.makeText(activity, "No other files available in this folder", Toast.LENGTH_SHORT).show()
            return
        }

        val names = candidates.map { it.name }.toTypedArray()
        var selectedIndex = 0

        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.swap_file_names_title))
            .setSingleChoiceItems(names, 0) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.action_swap_file_names) { _, _ ->
                val secondFile = candidates[selectedIndex]
                swap(activity, firstFile.path, secondFile.path, onComplete)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
