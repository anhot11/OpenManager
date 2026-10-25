/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.terminal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import me.zhanghai.android.files.app.application
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object TerminalExpansionManager {
    const val TERMEX_PACKAGE = "bin.mt.termex"

    data class LaunchConfig(
        val executable: String,
        val cwd: String,
        val args: Array<String>,
        val env: Array<String>,
        val isTermex: Boolean,
        val isRoot: Boolean
    )

    fun isTermexInstalled(context: Context = application): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMEX_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getTermexVersion(context: Context = application): String? {
        return try {
            val info = context.packageManager.getPackageInfo(TERMEX_PACKAGE, 0)
            val name = info.versionName ?: "v3.8"
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64"
            "$name $abi"
        } catch (e: Exception) {
            null
        }
    }

    fun isRootAvailable(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/system/sd/xbin/su"
        )
        for (path in paths) {
            val f = File(path)
            if (f.exists() && f.canExecute()) return true
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    fun prepareLaunchConfig(
        context: Context,
        forceRoot: Boolean? = null,
        overrideCwd: String? = null
    ): LaunchConfig {
        val asRoot = forceRoot ?: TerminalSettings.isRootSession(context)
        val targetCwd = overrideCwd ?: TerminalSettings.getStartPath(context)
        val validCwd = if (File(targetCwd).isDirectory) targetCwd else "/storage/emulated/0"

        if (asRoot && isRootAvailable()) {
            val env = arrayOf(
                "TERM=xterm-256color",
                "HOME=$validCwd",
                "PATH=/system/bin:/system/xbin:/sbin:/vendor/bin"
            )
            return LaunchConfig(
                executable = "su",
                cwd = validCwd,
                args = arrayOf("-"),
                env = env,
                isTermex = false,
                isRoot = true
            )
        }

        if (isTermexInstalled(context)) {
            try {
                val pkgInfo = context.packageManager.getPackageInfo(TERMEX_PACKAGE, 0)
                val nativeLibDir = pkgInfo.applicationInfo?.nativeLibraryDir ?: ""
                val apkPath = pkgInfo.applicationInfo?.sourceDir ?: ""

                val termexDir = File(context.filesDir, "termex")
                val libDir = File(termexDir, "lib")
                libDir.mkdirs()

                // Check if auxiliary libraries need extraction
                val marker = File(libDir, "libandroid-support.so")
                if (!marker.exists() || marker.length() == 0L) {
                    extractTermexAuxLibraries(apkPath, libDir, termexDir)
                }

                val bashBin = File(nativeLibDir, "bash")
                if (bashBin.exists()) {
                    val prompt = "$([ $? == 0 ] && echo '\\[\\e[1;32m\\]' || echo '\\[\\e[1;31m\\]')$([ $UID == 0 ] && echo '#' || echo '➜') \\[\\e[1;36m\\]\\W\\[\\e[m\\] "
                    val env = arrayOf(
                        "PREFIX=${termexDir.absolutePath}",
                        "LD_LIBRARY_PATH=${libDir.absolutePath}:$nativeLibDir",
                        "PATH=$nativeLibDir:/system/bin:/system/xbin",
                        "SHELL=${bashBin.absolutePath}",
                        "TERM=xterm-256color",
                        "HOME=$validCwd",
                        "TMPDIR=${context.cacheDir.absolutePath}",
                        "PS1=$prompt"
                    )
                    return LaunchConfig(
                        executable = bashBin.absolutePath,
                        cwd = validCwd,
                        args = arrayOf("--norc", "-i"),
                        env = env,
                        isTermex = true,
                        isRoot = false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Standard Android shell fallback
        val env = arrayOf(
            "PATH=/system/bin:/system/xbin",
            "TERM=xterm-256color",
            "HOME=$validCwd",
            "PS1=: \\w $ "
        )
        return LaunchConfig(
            executable = "/system/bin/sh",
            cwd = validCwd,
            args = arrayOf("-i"),
            env = env,
            isTermex = false,
            isRoot = false
        )
    }

    private fun extractTermexAuxLibraries(apkPath: String, libDir: File, termexDir: File) {
        if (apkPath.isEmpty() || !File(apkPath).exists()) return
        try {
            ZipFile(File(apkPath)).use { zip ->
                val entries = zip.entries()
                val isArm64 = Build.SUPPORTED_ABIS.any { it.contains("arm64") }
                val targetPrefix = if (isArm64) "lib/arm64-v8a/lib/" else "lib/armeabi-v7a/lib/"

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.startsWith(targetPrefix)) {
                        var fileName = entry.name.substring(targetPrefix.length)
                        if (fileName.contains("/")) {
                            fileName = fileName.substringAfterLast("/")
                        }
                        if (fileName.endsWith(".rename")) {
                            fileName = fileName.removeSuffix(".rename")
                        } else if (fileName.endsWith(".link")) {
                            fileName = fileName.removeSuffix(".link")
                        }
                        val outFile = File(libDir, fileName)
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        outFile.setReadable(true, false)
                        outFile.setExecutable(true, false)
                    } else if (entry.name == "assets/etc/bash.bashrc") {
                        val etcDir = File(termexDir, "etc")
                        etcDir.mkdirs()
                        val rcFile = File(etcDir, "bash.bashrc")
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(rcFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun installTermexApk(context: Context): Boolean {
        val candidates = arrayOf(
            File("/sdcard/Download/termex_v3008_arm64.apk"),
            File(context.getExternalFilesDir(null), "termex.apk"),
            File("/storage/emulated/0/Download/termex_v3008_arm64.apk")
        )
        for (apk in candidates) {
            if (apk.exists() && apk.length() > 0) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.file_provider",
                        apk
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return false
    }
}
