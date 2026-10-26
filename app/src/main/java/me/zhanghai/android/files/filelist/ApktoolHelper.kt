/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java8.nio.file.Files
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.appmanager.InstalledAppsActivity
import me.zhanghai.android.files.file.AxmlDecoder
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.newOutputStream

object ApktoolHelper {

    fun isApktoolProject(dirPath: Path): Boolean {
        return try {
            Files.isDirectory(dirPath) && (
                Files.exists(dirPath.resolve("apktool.yml")) ||
                Files.exists(dirPath.resolve("AndroidManifest.xml"))
            )
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Decompiles an APK into a project folder:
     * - Decodes AndroidManifest.xml and res XML files to readable text
     * - Preserves binaries for lossless recompilation
     * - Extracts dex, assets, and generates apktool.yml
     */
    fun decompileApk(
        activity: Activity,
        apkPath: Path,
        onComplete: ((Path?) -> Unit)? = null
    ) {
        val fileName = apkPath.fileName.toString()
        val baseName = if (fileName.endsWith(".apk", ignoreCase = true)) {
            fileName.substringBeforeLast(".apk")
        } else {
            fileName
        }

        val parentDir = apkPath.parent ?: Paths.get("/storage/emulated/0/Download")
        var projectDir = parentDir.resolve("${baseName}_src")
        var counter = 1
        while (Files.exists(projectDir)) {
            projectDir = parentDir.resolve("${baseName}_src_$counter")
            counter++
        }

        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Descompilando APK (Apktool)")
            .setMessage("Extrayendo recursos y decodificando AndroidManifest.xml...\nPor favor espera.")
            .setCancelable(false)
            .show()

        kotlin.concurrent.thread {
            var success = false
            var errorMsg: String? = null

            try {
                Files.createDirectories(projectDir)
                val inStream = apkPath.newInputStream()
                val zipIn = ZipInputStream(inStream)
                var entry = zipIn.nextEntry
                val buffer = ByteArray(65536)

                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && !name.startsWith("META-INF/")) {
                        val targetFile = projectDir.resolve(name)
                        val parent = targetFile.parent
                        if (parent != null && !Files.exists(parent)) {
                            Files.createDirectories(parent)
                        }

                        val baos = ByteArrayOutputStream()
                        var bytesRead: Int
                        while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                            baos.write(buffer, 0, bytesRead)
                        }
                        val entryBytes = baos.toByteArray()

                        if (name == "AndroidManifest.xml" && AxmlDecoder.isAxml(entryBytes)) {
                            val decoded = AxmlDecoder.decode(entryBytes)
                            targetFile.newOutputStream().use { it.write(decoded.toByteArray(Charsets.UTF_8)) }
                            val origFile = projectDir.resolve("AndroidManifest.xml.orig")
                            origFile.newOutputStream().use { it.write(entryBytes) }
                        } else if (name.startsWith("res/") && name.endsWith(".xml") && AxmlDecoder.isAxml(entryBytes)) {
                            val decoded = AxmlDecoder.decode(entryBytes)
                            targetFile.newOutputStream().use { it.write(decoded.toByteArray(Charsets.UTF_8)) }
                            val origFile = projectDir.resolve("$name.orig")
                            origFile.newOutputStream().use { it.write(entryBytes) }
                        } else {
                            targetFile.newOutputStream().use { it.write(entryBytes) }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                zipIn.close()

                // Generate apktool.yml metadata
                val yml = """
                    version: 2.9.3
                    apkFileName: $fileName
                    isFrameworkApk: false
                    packageInfo:
                      forcedPackageId: '127'
                    versionInfo:
                      versionCode: '1'
                      versionName: '1.0'
                    compressionType: false
                    doNotCompress:
                    - arsc
                    - png
                    - webp
                    - jpg
                    - ogg
                    - mp3
                """.trimIndent()
                val ymlFile = projectDir.resolve("apktool.yml")
                ymlFile.newOutputStream().use { it.write(yml.toByteArray(Charsets.UTF_8)) }

                success = true
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.localizedMessage ?: e.javaClass.simpleName
            }

            activity.runOnUiThread {
                progressDialog.dismiss()
                if (success) {
                    Toast.makeText(
                        activity,
                        "APK descompilado exitosamente:\n${projectDir.fileName}",
                        Toast.LENGTH_LONG
                    ).show()

                    // Navigate current pane to decompiled directory
                    (activity as? FileListActivity)?.activeFragment?.navigateTo(projectDir)
                    onComplete?.invoke(projectDir)
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Error al descompilar APK")
                        .setMessage(errorMsg ?: "Error desconocido durante la descompilación")
                        .setPositiveButton("Aceptar", null)
                        .show()
                    onComplete?.invoke(null)
                }
            }
        }
    }

    /**
     * Recompiles an Apktool project folder back into an installable APK:
     * - Packages entries (preserving binary AXML if untouched)
     * - Launches the 3-scheme signature dialog
     */
    fun recompileApk(
        activity: Activity,
        projectDir: Path,
        onComplete: ((Path?) -> Unit)? = null
    ) {
        val dirName = projectDir.fileName.toString()
        val baseName = if (dirName.endsWith("_src", ignoreCase = true)) {
            dirName.substringBeforeLast("_src")
        } else {
            dirName
        }
        val rawApkName = "${baseName}_recompiled_unsigned.apk"
        val parentDir = projectDir.parent ?: Paths.get("/storage/emulated/0/Download")
        val rawApkPath = parentDir.resolve(rawApkName)

        val progressDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Recompilando APK (Apktool)")
            .setMessage("Empaquetando recursos y ensamblando APK...\nPor favor espera.")
            .setCancelable(false)
            .show()

        kotlin.concurrent.thread {
            var success = false
            var errorMsg: String? = null

            try {
                val outStream = rawApkPath.newOutputStream()
                val zipOut = ZipOutputStream(outStream)

                fun addDirectoryToZip(currentDir: Path, basePath: Path) {
                    Files.newDirectoryStream(currentDir).use { stream ->
                        for (path in stream) {
                            val relPath = basePath.relativize(path).toString().replace('\\', '/')
                            if (Files.isDirectory(path)) {
                                addDirectoryToZip(path, basePath)
                            } else {
                                // Skip metadata files
                                if (relPath == "apktool.yml" || relPath.endsWith(".orig")) {
                                    continue
                                }

                                var dataBytes: ByteArray
                                // Check if there is an original binary for this xml
                                val origFile = currentDir.resolve("${path.fileName}.orig")
                                if (Files.exists(origFile)) {
                                    val currentText = path.newInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
                                    val origBytes = origFile.newInputStream().use { it.readBytes() }
                                    val origText = if (AxmlDecoder.isAxml(origBytes)) {
                                        AxmlDecoder.decode(origBytes)
                                    } else {
                                        origBytes.toString(Charsets.UTF_8)
                                    }
                                    dataBytes = if (currentText == origText) {
                                        origBytes
                                    } else {
                                        path.newInputStream().use { it.readBytes() }
                                    }
                                } else {
                                    dataBytes = path.newInputStream().use { it.readBytes() }
                                }

                                val entry = ZipEntry(relPath)
                                val lower = relPath.lowercase()
                                val isStored = lower.endsWith(".arsc") || lower.endsWith(".png") ||
                                    lower.endsWith(".jpg") || lower.endsWith(".webp") ||
                                    lower.endsWith(".ogg") || lower.endsWith(".mp3")

                                if (isStored) {
                                    entry.method = ZipEntry.STORED
                                    entry.size = dataBytes.size.toLong()
                                    entry.compressedSize = dataBytes.size.toLong()
                                    val crc = CRC32()
                                    crc.update(dataBytes)
                                    entry.crc = crc.value
                                } else {
                                    entry.method = ZipEntry.DEFLATED
                                }

                                zipOut.putNextEntry(entry)
                                zipOut.write(dataBytes)
                                zipOut.closeEntry()
                            }
                        }
                    }
                }

                addDirectoryToZip(projectDir, projectDir)
                zipOut.finish()
                zipOut.close()

                success = true
            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.localizedMessage ?: e.javaClass.simpleName
            }

            activity.runOnUiThread {
                progressDialog.dismiss()
                if (success) {
                    // Prompt user to sign with the 3 signature schemes (V1, V2, V1+V2)
                    Toast.makeText(
                        activity,
                        "APK reempaquetado exitosamente. Ahora selecciona el esquema de firma.",
                        Toast.LENGTH_SHORT
                    ).show()

                    ApkSignerHelper.showSignDialog(activity, rawApkPath) { finalSignedPath ->
                        // Clean up temporary unsigned recompiled APK
                        try {
                            Files.deleteIfExists(rawApkPath)
                        } catch (e: Exception) {
                            // Ignored
                        }
                        (activity as? FileListActivity)?.activeFragment?.reload()
                        onComplete?.invoke(finalSignedPath)
                    }
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle("Error al recompilar APK")
                        .setMessage(errorMsg ?: "Error desconocido durante la recompilación")
                        .setPositiveButton("Aceptar", null)
                        .show()
                    onComplete?.invoke(null)
                }
            }
        }
    }

    /**
     * Displays the Apktool Management Hub dialog
     */
    fun showApktoolHubDialog(activity: Activity, currentPath: Path) {
        val context = activity
        val dialog = AppCompatDialog(context)
        dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_mt_apktool_hub, null)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val displayMetrics = context.resources.displayMetrics
            val targetWidth = (displayMetrics.widthPixels * 0.92).toInt().coerceAtMost(
                (450 * displayMetrics.density).toInt()
            )
            setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        view.findViewById<Button>(R.id.btnCloseApktoolHub).setOnClickListener {
            dialog.dismiss()
        }

        // 1. Descompilar APK
        view.findViewById<LinearLayout>(R.id.itemDecompileApk).setOnClickListener {
            dialog.dismiss()
            pickApkInDirectory(activity, currentPath, "Selecciona el APK a descompilar") { selectedApk ->
                decompileApk(activity, selectedApk)
            }
        }

        // 2. Recompilar APK
        view.findViewById<LinearLayout>(R.id.itemRecompileApk).setOnClickListener {
            dialog.dismiss()
            if (isApktoolProject(currentPath)) {
                recompileApk(activity, currentPath)
            } else {
                pickProjectInDirectory(activity, currentPath, "Selecciona la carpeta de proyecto a recompilar") { selectedProject ->
                    recompileApk(activity, selectedProject)
                }
            }
        }

        // 3. Firmar APK
        view.findViewById<LinearLayout>(R.id.itemSignApk).setOnClickListener {
            dialog.dismiss()
            pickApkInDirectory(activity, currentPath, "Selecciona el APK a firmar") { selectedApk ->
                ApkSignerHelper.showSignDialog(activity, selectedApk) {
                    (activity as? FileListActivity)?.activeFragment?.reload()
                }
            }
        }

        // 4. Verificar firma
        view.findViewById<LinearLayout>(R.id.itemVerifySignature).setOnClickListener {
            dialog.dismiss()
            pickApkInDirectory(activity, currentPath, "Selecciona el APK para verificar firma") { selectedApk ->
                ApkSignerHelper.showVerifySignatureDialog(activity, selectedApk)
            }
        }

        // 5. Extraer aplicaciones
        view.findViewById<LinearLayout>(R.id.itemExtractApps).setOnClickListener {
            dialog.dismiss()
            activity.startActivity(Intent(activity, InstalledAppsActivity::class.java))
        }

        dialog.show()
    }

    private fun pickApkInDirectory(
        activity: Activity,
        directory: Path,
        title: String,
        onPicked: (Path) -> Unit
    ) {
        val apkList = mutableListOf<Path>()
        try {
            if (Files.isDirectory(directory)) {
                Files.newDirectoryStream(directory).use { stream ->
                    for (p in stream) {
                        if (p.fileName.toString().endsWith(".apk", ignoreCase = true)) {
                            apkList.add(p)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (apkList.isEmpty()) {
            Toast.makeText(activity, "No se encontraron archivos .apk en este directorio:\n${directory.fileName}", Toast.LENGTH_LONG).show()
            return
        }

        val names = apkList.map { it.fileName.toString() }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setItems(names) { _, which ->
                onPicked(apkList[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickProjectInDirectory(
        activity: Activity,
        directory: Path,
        title: String,
        onPicked: (Path) -> Unit
    ) {
        val projList = mutableListOf<Path>()
        try {
            if (Files.isDirectory(directory)) {
                Files.newDirectoryStream(directory).use { stream ->
                    for (p in stream) {
                        if (Files.isDirectory(p) && isApktoolProject(p)) {
                            projList.add(p)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (projList.isEmpty()) {
            Toast.makeText(activity, "No se encontraron carpetas de proyecto Apktool en:\n${directory.fileName}", Toast.LENGTH_LONG).show()
            return
        }

        val names = projList.map { it.fileName.toString() }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setItems(names) { _, which ->
                onPicked(projList[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
