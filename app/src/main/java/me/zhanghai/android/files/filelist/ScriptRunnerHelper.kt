/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.LayoutInflater
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.ScriptPreferencesDialogBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.terminal.TerminalActivity
import me.zhanghai.android.files.terminal.TerminalExpansionManager
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.viewer.text.TextEditorActivity
import java.io.File

object ScriptRunnerHelper {
    private const val PREFS_NAME = "script_exec_prefs"
    private const val KEY_EXEC_ENV = "exec_env"
    private const val KEY_EXEC_ROOT = "exec_root"

    /**
     * Checks if a file is an executable binary (ELF), a shell script, or has POSIX executable bits.
     */
    fun isExecutableOrScript(file: FileItem): Boolean {
        val fileName = file.path.fileName.toString()
        if (fileName.endsWith(".sh", ignoreCase = true) ||
            fileName.endsWith(".bash", ignoreCase = true) ||
            fileName.endsWith(".zsh", ignoreCase = true)) {
            return true
        }

        val attributes = file.attributes
        if (attributes is PosixFileAttributes) {
            val mode = attributes.mode()
            if (mode != null && (mode.contains(PosixFileModeBit.OWNER_EXECUTE) ||
                mode.contains(PosixFileModeBit.GROUP_EXECUTE) ||
                mode.contains(PosixFileModeBit.OTHERS_EXECUTE))) {
                return true
            }
        }

        return isElfOrShebang(file.path)
    }

    fun isExecutableOrScript(path: Path): Boolean {
        val fileName = path.fileName.toString()
        if (fileName.endsWith(".sh", ignoreCase = true) ||
            fileName.endsWith(".bash", ignoreCase = true) ||
            fileName.endsWith(".zsh", ignoreCase = true)) {
            return true
        }
        return isElfOrShebang(path)
    }

    fun isElfOrShebang(path: Path): Boolean {
        return try {
            path.newInputStream().use { stream ->
                val b0 = stream.read()
                val b1 = stream.read()
                val b2 = stream.read()
                val b3 = stream.read()
                // ELF magic "\x7FELF"
                if (b0 == 0x7F && b1 == 'E'.code && b2 == 'L'.code && b3 == 'F'.code) {
                    return@use true
                }
                // Shebang "#!"
                if (b0 == '#'.code && b1 == '!'.code) {
                    return@use true
                }
                false
            }
        } catch (e: Exception) {
            try {
                path.toFile().canExecute()
            } catch (e2: Exception) {
                false
            }
        }
    }

    fun isElfBinary(path: Path): Boolean {
        return try {
            path.newInputStream().use { stream ->
                val b0 = stream.read()
                val b1 = stream.read()
                val b2 = stream.read()
                val b3 = stream.read()
                b0 == 0x7F && b1 == 'E'.code && b2 == 'L'.code && b3 == 'F'.code
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Displays the MT Manager style Action Dialog when clicking a script or ELF binary.
     */
    fun showScriptActionDialog(activity: Activity, fileItem: FileItem) {
        showScriptActionDialog(activity, fileItem.path)
    }

    fun showScriptActionDialog(activity: Activity, path: Path) {
        val fileName = path.fileName.toString()

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(fileName)
            .setMessage(R.string.script_action_select_action)
            .setNegativeButton(R.string.script_action_preferences, null)
            .setNeutralButton(R.string.script_action_edit) { _, _ ->
                editScriptOrBinary(activity, path)
            }
            .setPositiveButton(R.string.script_action_execute) { _, _ ->
                executeScriptOrBinary(activity, path)
            }
            .create()

        dialog.setOnShowListener {
            val prefsBtn = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            prefsBtn.setOnClickListener {
                showPreferencesDialog(activity)
            }
        }
        dialog.show()
    }

    fun showPreferencesDialog(activity: Activity) {
        val sp = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultEnv = if (TerminalExpansionManager.isTermexInstalled(activity)) "termex" else "system"
        val savedEnv = sp.getString(KEY_EXEC_ENV, defaultEnv) ?: defaultEnv
        val defaultRoot = TerminalExpansionManager.isRootAvailable()
        val savedRoot = sp.getBoolean(KEY_EXEC_ROOT, defaultRoot)

        val binding = ScriptPreferencesDialogBinding.inflate(LayoutInflater.from(activity))
        if (savedEnv == "termex") {
            binding.radioTermex.isChecked = true
        } else {
            binding.radioSystem.isChecked = true
        }
        binding.checkRoot.isChecked = savedRoot

        MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val chosenEnv = if (binding.radioTermex.isChecked) "termex" else "system"
                val chosenRoot = binding.checkRoot.isChecked
                sp.edit()
                    .putString(KEY_EXEC_ENV, chosenEnv)
                    .putBoolean(KEY_EXEC_ROOT, chosenRoot)
                    .apply()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editScriptOrBinary(activity: Activity, path: Path) {
        if (isElfBinary(path)) {
            if (activity is FragmentActivity) {
                HexViewerDialogFragment.show(path, activity.supportFragmentManager)
            }
        } else {
            val intent = Intent(activity, TextEditorActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                extraPath = path
            }
            activity.startActivity(intent)
        }
    }

    private fun executeScriptOrBinary(activity: Activity, path: Path) {
        val sp = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultEnv = if (TerminalExpansionManager.isTermexInstalled(activity)) "termex" else "system"
        val env = sp.getString(KEY_EXEC_ENV, defaultEnv) ?: defaultEnv
        val withRoot = sp.getBoolean(KEY_EXEC_ROOT, TerminalExpansionManager.isRootAvailable())

        val scriptPath = path.toFile().absolutePath
        val parentDir = path.toFile().parent ?: "/data/local/tmp"

        val execCmd: String
        val isElf = isElfBinary(path)

        if (isElf) {
            if (withRoot) {
                execCmd = "chmod 777 \"$scriptPath\" 2>/dev/null; \"$scriptPath\""
            } else {
                val binDir = File(activity.filesDir, "bin").apply { mkdirs() }
                val targetFile = File(binDir, path.fileName.toString())
                try {
                    path.newInputStream().use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetFile.setReadable(true, false)
                    targetFile.setWritable(true, false)
                    targetFile.setExecutable(true, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                execCmd = "\"${targetFile.absolutePath}\""
            }
        } else {
            val shell = if (env == "termex") "bash" else "sh"
            execCmd = if (withRoot) {
                "chmod 777 \"$scriptPath\" 2>/dev/null; $shell \"$scriptPath\""
            } else {
                "$shell \"$scriptPath\""
            }
        }

        val intent = TerminalActivity.createIntent(
            activity,
            path = parentDir,
            command = execCmd,
            forceRoot = withRoot,
            forceTermex = env == "termex"
        )
        activity.startActivity(intent)
    }
}
