/*
 * OpenManager - Script Runner & Root Sandbox Bridge
 * Integrates shguard with the file list to provide safe, audited execution.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import java8.nio.file.Path
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.viewer.text.TextEditorActivity
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

object ScriptRunnerHelper {

    /**
     * Extracts the embedded shguard binary to internal storage and marks it executable.
     */
    fun ensureShguardBinary(context: Context): File? {
        val binFile = File(context.filesDir, "shguard")
        if (!binFile.exists() || binFile.length() == 0L) {
            try {
                context.assets.open("shguard").use { input ->
                    FileOutputStream(binFile).use { output ->
                        input.copyTo(output)
                    }
                }
                binFile.setExecutable(true, false)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        return binFile
    }

    /**
     * Displays the MT-style Action Dialog when clicking a .sh script.
     */
    fun showScriptActionDialog(activity: Activity, fileItem: FileItem) {
        showScriptActionDialog(activity, fileItem.path)
    }

    fun showScriptActionDialog(activity: Activity, path: Path) {
        val scriptPath = path.toFile().absolutePath
        val fileName = path.fileName.toString()

        val options = arrayOf(
            "🛡️ Ejecutar con Root Seguro (Aislamiento shguard)",
            "⚡ Ejecutar con Root Directo (Sin restricciones)",
            "📝 Editar código (Editor Monospace)",
            "📋 Ver registro de auditoría (/data/local/tmp)"
        )

        MaterialAlertDialogBuilder(activity)
            .setTitle(fileName)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> runScriptWithShguard(activity, scriptPath, isolateNetwork = true)
                    1 -> runScriptWithShguard(activity, scriptPath, isolateNetwork = false)
                    2 -> openInEditor(activity, path)
                    3 -> showAuditLog(activity)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openInEditor(activity: Activity, path: Path) {
        val intent = Intent(activity, TextEditorActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            extraPath = path
        }
        activity.startActivity(intent)
    }

    private fun runScriptWithShguard(activity: Activity, scriptPath: String, isolateNetwork: Boolean) {
        val shguardBin = ensureShguardBinary(activity)
        val shguardPath = shguardBin?.absolutePath ?: "/data/local/tmp/shguard"

        val flag = if (isolateNetwork) "-n" else "-a"

        // Mostrar diálogo de salida de terminal
        val outputView = TextView(activity).apply {
            setPadding(32, 24, 32, 24)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            text = "⏳ Ejecutando script bajo supervisión de shguard...\n\n"
        }

        val alertDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Terminal Root Seguro")
            .setView(outputView)
            .setPositiveButton("Cerrar", null)
            .show()

        Thread {
            try {
                val hasSu = File("/system/bin/su").exists() || File("/system/xbin/su").exists() || File("/sbin/su").exists()
                val execArgs = if (hasSu) {
                    arrayOf("su", "-c", "$shguardPath $flag \"$scriptPath\"")
                } else {
                    arrayOf("sh", "-c", "$shguardPath $flag \"$scriptPath\"")
                }
                val process = try {
                    Runtime.getRuntime().exec(execArgs)
                } catch (e: Exception) {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", "$shguardPath $flag \"$scriptPath\""))
                }
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errReader = BufferedReader(InputStreamReader(process.errorStream))

                val output = StringBuilder()
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                    activity.runOnUiThread {
                        outputView.text = output.toString()
                    }
                }
                while (errReader.readLine().also { line = it } != null) {
                    output.append("[LOG] ").append(line).append("\n")
                    activity.runOnUiThread {
                        outputView.text = output.toString()
                    }
                }

                val exitCode = process.waitFor()
                activity.runOnUiThread {
                    output.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                    output.append("✔ Finalizado (Código de salida: $exitCode)\n")
                    outputView.text = output.toString()
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    outputView.append("\n❌ Error al ejecutar: ${e.message}\n")
                }
            }
        }.start()
    }

    private fun showAuditLog(activity: Activity) {
        val logFile = File("/data/local/tmp/openmanager_audit.log")
        val content = if (logFile.exists()) {
            logFile.readText()
        } else {
            "No hay registros de auditoría aún."
        }

        val textView = TextView(activity).apply {
            setPadding(32, 24, 32, 24)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            text = content
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Registro de Auditoría")
            .setView(textView)
            .setPositiveButton("Cerrar", null)
            .show()
    }
}
