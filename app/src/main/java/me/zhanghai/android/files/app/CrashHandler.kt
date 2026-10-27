/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.app.Application
import android.os.Build
import android.os.Environment
import android.os.Process
import android.util.Log
import me.zhanghai.android.files.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "CrashHandler"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var app: Application

    fun install(application: Application) {
        app = application
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            writeCrashLog(thread, throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write crash log", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable) ?: run {
                Process.killProcess(Process.myPid())
                System.exit(10)
            }
        }
    }

    fun writeCrashLog(thread: Thread, throwable: Throwable): File? {
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(now))
        val readableTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(now))

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val runtime = Runtime.getRuntime()
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)

        val report = buildString {
            appendLine("==================================================")
            appendLine("           OPENMANAGER CRASH REPORT               ")
            appendLine("==================================================")
            appendLine("Timestamp:     $readableTime")
            appendLine("App Version:   ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package:       ${app.packageName}")
            appendLine("Thread:        ${thread.name} (id: ${thread.id})")
            appendLine("--------------------------------------------------")
            appendLine("DEVICE INFORMATION")
            appendLine("Manufacturer:  ${Build.MANUFACTURER}")
            appendLine("Model:         ${Build.MODEL}")
            appendLine("Brand:         ${Build.BRAND}")
            appendLine("Device:        ${Build.DEVICE}")
            appendLine("Product:       ${Build.PRODUCT}")
            appendLine("Android SDK:   ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
            appendLine("ABIs:          ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("--------------------------------------------------")
            appendLine("MEMORY STATUS")
            appendLine("Free Memory:   ${freeMem}MB")
            appendLine("Total Memory:  ${totalMem}MB")
            appendLine("Max Memory:    ${maxMem}MB")
            appendLine("--------------------------------------------------")
            appendLine("STACK TRACE")
            appendLine(stackTrace)
            appendLine("==================================================")
        }

        // Try primary external storage
        val candidates = listOf(
            File("/storage/emulated/0/OpenManager/crash_logs"),
            File(Environment.getExternalStorageDirectory(), "OpenManager/crash_logs"),
            app.getExternalFilesDir("crash_logs"),
            File(app.filesDir, "crash_logs")
        )

        for (dir in candidates) {
            if (dir == null) continue
            try {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                if (dir.exists() && dir.canWrite()) {
                    val logFile = File(dir, "crash_${timeStr}.txt")
                    FileWriter(logFile).use { writer ->
                        writer.write(report)
                        writer.flush()
                    }
                    Log.i(TAG, "Crash log successfully saved to: ${logFile.absolutePath}")
                    return logFile
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot write to candidate dir: ${dir.absolutePath}", e)
            }
        }

        return null
    }
}
