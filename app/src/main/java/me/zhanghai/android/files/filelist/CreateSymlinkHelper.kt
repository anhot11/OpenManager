/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.os.Build
import android.system.Os
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.toFile
import me.zhanghai.android.files.terminal.TerminalExpansionManager
import java.io.File

object CreateSymlinkHelper {

    fun showDialog(activity: Activity, fileItem: FileItem, currentDir: Path, onComplete: () -> Unit) {
        val targetPath = fileItem.path.toFile().absolutePath
        val defaultLinkName = "link_${fileItem.name}"

        val context = activity
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val targetLabel = TextView(context).apply {
            text = context.getString(R.string.create_symlink_target_label)
            textSize = 13f
        }
        container.addView(targetLabel)

        val targetInput = EditText(context).apply {
            setText(targetPath)
        }
        container.addView(targetInput)

        val nameLabel = TextView(context).apply {
            text = context.getString(R.string.create_symlink_name_label)
            textSize = 13f
            setPadding(0, 16, 0, 0)
        }
        container.addView(nameLabel)

        val nameInput = EditText(context).apply {
            setText(defaultLinkName)
        }
        container.addView(nameInput)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.create_symlink_title)
            .setView(container)
            .setPositiveButton(R.string.create_symlink_btn_create) { _, _ ->
                val finalTarget = targetInput.text.toString().trim()
                val finalLinkName = nameInput.text.toString().trim()
                if (finalLinkName.isEmpty()) {
                    Toast.makeText(activity, "Link name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val linkFile = File(currentDir.toFile(), finalLinkName)
                createSymlink(activity, finalTarget, linkFile, onComplete)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createSymlink(activity: Activity, target: String, linkFile: File, onComplete: () -> Unit) {
        try {
            Os.symlink(target, linkFile.absolutePath)
            Toast.makeText(
                activity,
                activity.getString(R.string.create_symlink_success, linkFile.name),
                Toast.LENGTH_SHORT
            ).show()
            onComplete()
        } catch (e: Exception) {
            // Check if root can execute symlink
            if (TerminalExpansionManager.isRootAvailable()) {
                try {
                    val process = Runtime.getRuntime().exec(
                        arrayOf("su", "-c", "ln -s \"$target\" \"${linkFile.absolutePath}\"")
                    )
                    process.waitFor()
                    if (process.exitValue() == 0) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.create_symlink_success, linkFile.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        onComplete()
                        return
                    }
                } catch (ignored: Exception) {}
            }
            Toast.makeText(
                activity,
                activity.getString(R.string.create_symlink_failed, e.message),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
