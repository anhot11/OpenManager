/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.appmanager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.topjohnwu.superuser.Shell
import java8.nio.file.Paths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.app.clipboardManager
import me.zhanghai.android.files.compat.longVersionCodeCompat
import me.zhanghai.android.files.databinding.ActivityInstalledAppsBinding
import me.zhanghai.android.files.databinding.DialogInstalledAppDetailsBinding
import me.zhanghai.android.files.databinding.InstalledAppItemBinding
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.filelist.ApkInstallHelper
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.util.asMimeType
import me.zhanghai.android.files.util.copyText
import me.zhanghai.android.files.util.createSendStreamIntent
import me.zhanghai.android.files.util.withChooser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class InstalledAppsActivity : AppActivity() {

    private lateinit var binding: ActivityInstalledAppsBinding
    private val allApps = mutableListOf<AppModel>()
    private val displayedApps = mutableListOf<AppModel>()
    private lateinit var adapter: AppsAdapter

    private var showSystemApps = false
    private var searchQuery = ""
    private var currentSortOrder = 0 // 0: Name A-Z

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    data class AppModel(
        val label: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val sourceDir: String,
        val splitSourceDirs: List<String>,
        val dataDir: String,
        val targetSdkVersion: Int,
        val minSdkVersion: Int,
        val sizeBytes: Long,
        val isSystem: Boolean,
        val isEnabled: Boolean,
        val firstInstallTime: Long,
        val lastUpdateTime: Long,
        val icon: Drawable?
    ) {
        val isSplit: Boolean get() = splitSourceDirs.isNotEmpty()
        val splitCount: Int get() = if (splitSourceDirs.isNotEmpty()) splitSourceDirs.size + 1 else 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstalledAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupTabs()
        setupRecyclerView()
        loadApps()
    }

    private fun setupTabs() {
        val tabUser = binding.tabLayout.newTab().setText(R.string.user_apps)
        val tabSystem = binding.tabLayout.newTab().setText(R.string.system_apps)
        binding.tabLayout.addTab(tabUser)
        binding.tabLayout.addTab(tabSystem)

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                showSystemApps = (tab?.position == 1)
                filterApps()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = AppsAdapter(displayedApps) { app ->
            showAppDetails(app)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyText.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = try {
                pm.getInstalledPackages(PackageManager.GET_META_DATA)
            } catch (e: Exception) {
                emptyList<PackageInfo>()
            }

            val list = mutableListOf<AppModel>()

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isEnabled = appInfo.enabled
                val label = try {
                    appInfo.loadLabel(pm).toString()
                } catch (e: Exception) {
                    pkg.packageName
                }
                val icon = try {
                    appInfo.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }

                val splits = appInfo.splitSourceDirs?.toList() ?: emptyList()
                var size = try {
                    val baseFile = File(appInfo.sourceDir)
                    if (baseFile.exists()) baseFile.length() else 0L
                } catch (e: Exception) {
                    0L
                }
                for (splitPath in splits) {
                    try {
                        val splitFile = File(splitPath)
                        if (splitFile.exists()) size += splitFile.length()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }

                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo.minSdkVersion
                } else {
                    0
                }

                list.add(
                    AppModel(
                        label = label,
                        packageName = pkg.packageName,
                        versionName = pkg.versionName ?: "1.0",
                        versionCode = pkg.longVersionCodeCompat,
                        sourceDir = appInfo.sourceDir ?: "",
                        splitSourceDirs = splits,
                        dataDir = appInfo.dataDir ?: "",
                        targetSdkVersion = appInfo.targetSdkVersion,
                        minSdkVersion = minSdk,
                        sizeBytes = size,
                        isSystem = isSystem,
                        isEnabled = isEnabled,
                        firstInstallTime = pkg.firstInstallTime,
                        lastUpdateTime = pkg.lastUpdateTime,
                        icon = icon
                    )
                )
            }

            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(list)
                applySort()

                val userCount = allApps.count { !it.isSystem }
                val systemCount = allApps.count { it.isSystem }
                binding.tabLayout.getTabAt(0)?.text = "${getString(R.string.user_apps)} ($userCount)"
                binding.tabLayout.getTabAt(1)?.text = "${getString(R.string.system_apps)} ($systemCount)"

                binding.progressBar.visibility = View.GONE
                filterApps()
            }
        }
    }

    private fun applySort() {
        when (currentSortOrder) {
            0 -> allApps.sortBy { it.label.lowercase() }
            1 -> allApps.sortByDescending { it.label.lowercase() }
            2 -> allApps.sortBy { it.packageName.lowercase() }
            3 -> allApps.sortByDescending { it.sizeBytes }
            4 -> allApps.sortBy { it.sizeBytes }
            5 -> allApps.sortByDescending { it.lastUpdateTime }
            6 -> allApps.sortByDescending { it.firstInstallTime }
            7 -> allApps.sortByDescending { it.targetSdkVersion }
        }
    }

    private fun filterApps() {
        displayedApps.clear()
        val query = searchQuery.trim().lowercase()

        for (app in allApps) {
            if (app.isSystem != showSystemApps) continue
            if (query.isNotEmpty()) {
                val match = app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query) ||
                    app.versionName.lowercase().contains(query)
                if (!match) continue
            }
            displayedApps.add(app)
        }

        adapter.notifyDataSetChanged()
        binding.emptyText.visibility = if (displayedApps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf(
            "Nombre (A - Z)",
            "Nombre (Z - A)",
            "Nombre de paquete (A - Z)",
            "Tamaño (Mayor a menor)",
            "Tamaño (Menor a mayor)",
            "Fecha de actualización (Recientes primero)",
            "Fecha de instalación",
            "Target SDK (Mayor a menor)"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Ordenar aplicaciones")
            .setSingleChoiceItems(sortOptions, currentSortOrder) { dialog, which ->
                currentSortOrder = which
                applySort()
                filterApps()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAppDetails(app: AppModel) {
        val dialogBinding = DialogInstalledAppDetailsBinding.inflate(layoutInflater)
        if (app.icon != null) {
            dialogBinding.dialogAppIcon.setImageDrawable(app.icon)
        } else {
            dialogBinding.dialogAppIcon.setImageResource(R.drawable.file_apk_icon)
        }
        dialogBinding.dialogAppName.text = app.label
        dialogBinding.dialogAppVersionSize.text = "v${app.versionName} (${app.versionCode}) • ${formatSize(app.sizeBytes)}"
        dialogBinding.dialogAppPackage.text = app.packageName
        val minSdkStr = if (app.minSdkVersion > 0) "${app.minSdkVersion}" else "1"
        dialogBinding.dialogAppSdk.text = "Min SDK: $minSdkStr • Target SDK: ${app.targetSdkVersion}"

        if (app.isSplit) {
            dialogBinding.dialogAppSplits.visibility = View.VISIBLE
            dialogBinding.dialogAppSplits.text = "APKS: ${app.splitCount} archivos divididos"
        } else {
            dialogBinding.dialogAppSplits.visibility = View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        // 1. Extraer APK
        dialogBinding.btnActionExtract.setOnClickListener {
            dialog.dismiss()
            extractApk(app)
        }

        // 2. Localizar APK en el explorador
        dialogBinding.btnActionLocateApk.setOnClickListener {
            dialog.dismiss()
            val apkFile = File(app.sourceDir)
            val parentDir = apkFile.parentFile ?: File("/data/app")
            val viewIntent = FileListActivity.createViewIntent(Paths.get(parentDir.absolutePath))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(viewIntent)
            finish()
        }

        // 3. Localizar Datos (Data)
        dialogBinding.btnActionLocateData.setOnClickListener {
            dialog.dismiss()
            val isRoot = ApkInstallHelper.isRootAvailable()
            val targetDataDir = if (isRoot && File(app.dataDir).exists()) {
                File(app.dataDir)
            } else {
                File("/storage/emulated/0/Android/data/${app.packageName}")
            }
            val viewIntent = FileListActivity.createViewIntent(Paths.get(targetDataDir.absolutePath))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(viewIntent)
            finish()
        }

        // 4. Abrir aplicación
        dialogBinding.btnActionLaunch.setOnClickListener {
            dialog.dismiss()
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Esta aplicación no tiene interfaz ejecutable", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Ajustes de la aplicación
        dialogBinding.btnActionDetails.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
            }
            startActivity(intent)
        }

        // 6. Copiar información
        dialogBinding.btnActionCopyInfo.setOnClickListener {
            dialog.dismiss()
            showCopyInfoDialog(app)
        }

        // 7. Compartir APK
        dialogBinding.btnActionShare.setOnClickListener {
            dialog.dismiss()
            shareApp(app)
        }

        // 8. Google Play Store
        dialogBinding.btnActionPlayStore.setOnClickListener {
            dialog.dismiss()
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")))
            }
        }

        // 9. Desinstalar aplicación
        dialogBinding.btnActionUninstall.setOnClickListener {
            dialog.dismiss()
            uninstallApp(app)
        }

        // 10. Root Actions Container
        val isRoot = ApkInstallHelper.isRootAvailable()
        if (isRoot) {
            dialogBinding.layoutRootActions.visibility = View.VISIBLE
            if (app.isEnabled) {
                dialogBinding.tvToggleEnableTitle.text = "Inhabilitar / Congelar (Root)"
                dialogBinding.tvToggleEnableSubtitle.text = "Desactivar paquete con pm disable"
            } else {
                dialogBinding.tvToggleEnableTitle.text = "Habilitar / Descongelar (Root)"
                dialogBinding.tvToggleEnableSubtitle.text = "Activar paquete con pm enable"
            }

            dialogBinding.btnActionForceStop.setOnClickListener {
                dialog.dismiss()
                lifecycleScope.launch(Dispatchers.IO) {
                    val res = Shell.cmd("am force-stop ${app.packageName}").exec()
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess) {
                            Toast.makeText(this@InstalledAppsActivity, "Proceso detenido: ${app.label}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@InstalledAppsActivity, "Error al forzar detención", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            dialogBinding.btnActionClearData.setOnClickListener {
                dialog.dismiss()
                MaterialAlertDialogBuilder(this)
                    .setTitle("Limpiar datos")
                    .setMessage("¿Seguro que deseas restablecer los datos de ${app.label}? Todos los ajustes y sesiones se eliminarán.")
                    .setPositiveButton("Limpiar") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val res = Shell.cmd("pm clear ${app.packageName}").exec()
                            withContext(Dispatchers.Main) {
                                if (res.isSuccess) {
                                    Toast.makeText(this@InstalledAppsActivity, "Datos restablecidos: ${app.label}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@InstalledAppsActivity, "Error al limpiar datos", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }

            dialogBinding.btnActionToggleEnable.setOnClickListener {
                dialog.dismiss()
                val newEnable = !app.isEnabled
                val cmd = if (newEnable) "pm enable ${app.packageName}" else "pm disable ${app.packageName}"
                lifecycleScope.launch(Dispatchers.IO) {
                    val res = Shell.cmd(cmd).exec()
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess) {
                            val msg = if (newEnable) "Habilitada: ${app.label}" else "Inhabilitada: ${app.label}"
                            Toast.makeText(this@InstalledAppsActivity, msg, Toast.LENGTH_SHORT).show()
                            loadApps()
                        } else {
                            Toast.makeText(this@InstalledAppsActivity, "Error al modificar estado", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } else {
            dialogBinding.layoutRootActions.visibility = View.GONE
        }

        dialog.show()
    }

    private fun showCopyInfoDialog(app: AppModel) {
        val sha256 = getCertificateSha256(this, app.packageName)
        val options = arrayOf(
            "Copiar nombre (${app.label})",
            "Copiar paquete (${app.packageName})",
            "Copiar versión (v${app.versionName})",
            "Copiar ruta de APK (${app.sourceDir})",
            "Copiar ruta de datos (${app.dataDir})",
            "Copiar firma SHA-256",
            "Copiar reporte completo"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Copiar información")
            .setItems(options) { _, which ->
                val textToCopy = when (which) {
                    0 -> app.label
                    1 -> app.packageName
                    2 -> "v${app.versionName} (${app.versionCode})"
                    3 -> app.sourceDir
                    4 -> app.dataDir
                    5 -> sha256
                    6 -> """
                        Aplicación: ${app.label}
                        Paquete: ${app.packageName}
                        Versión: v${app.versionName} (${app.versionCode})
                        Tamaño: ${formatSize(app.sizeBytes)}
                        Target SDK: ${app.targetSdkVersion}
                        Min SDK: ${app.minSdkVersion}
                        Ruta APK: ${app.sourceDir}
                        Ruta Datos: ${app.dataDir}
                        Firma SHA-256: $sha256
                        Fecha de instalación: ${formatDate(app.firstInstallTime)}
                        Fecha de actualización: ${formatDate(app.lastUpdateTime)}
                    """.trimIndent()
                    else -> ""
                }
                clipboardManager.copyText(textToCopy, this)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun shareApp(app: AppModel) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val shareDir = File(cacheDir, "shared_apks").apply { mkdirs() }
            val shareFile = if (app.isSplit) {
                val file = File(shareDir, "${app.packageName}_v${app.versionName}.apks")
                extractSplitApk(app, file)
                file
            } else {
                val file = File(shareDir, "${app.packageName}_v${app.versionName}.apk")
                extractSingleApk(app.sourceDir, file)
                file
            }

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                if (shareFile.exists() && shareFile.length() > 0) {
                    try {
                        val path = Paths.get(shareFile.absolutePath)
                        val uri = path.fileProviderUri
                        val intent = uri.createSendStreamIntent("application/vnd.android.package-archive".asMimeType())
                            .withChooser()
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@InstalledAppsActivity, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@InstalledAppsActivity, "Error al preparar archivo para compartir", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun uninstallApp(app: AppModel) {
        val isRoot = ApkInstallHelper.isRootAvailable()
        if (isRoot) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Desinstalar ${app.label}")
                .setMessage("¿Deseas realizar una desinstalación silenciosa con Root o a través del sistema?")
                .setPositiveButton("Silenciosa (Root)") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val res = Shell.cmd("pm uninstall ${app.packageName}").exec()
                        withContext(Dispatchers.Main) {
                            if (res.isSuccess) {
                                Toast.makeText(this@InstalledAppsActivity, "${app.label} desinstalada con éxito", Toast.LENGTH_SHORT).show()
                                loadApps()
                            } else {
                                Toast.makeText(this@InstalledAppsActivity, "Error al desinstalar por Root", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNeutralButton("Sistema") { _, _ ->
                    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                    startActivity(intent)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            startActivity(intent)
        }
    }

    private fun extractApk(app: AppModel) {
        val baseDir = File("/storage/emulated/0/OpenManager/apks")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val targetDir = if (baseDir.exists() && baseDir.canWrite()) {
            baseDir
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OpenManager")
                .apply { mkdirs() }
        }

        val safeLabel = app.label.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
        val extension = if (app.isSplit) "apks" else "apk"
        val destFile = File(targetDir, "${safeLabel}_v${app.versionName}.$extension")

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            val success = try {
                if (app.isSplit) {
                    extractSplitApk(app, destFile)
                } else {
                    extractSingleApk(app.sourceDir, destFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                if (success && destFile.exists() && destFile.length() > 0) {
                    val snackbar = Snackbar.make(
                        binding.root,
                        "Extraído: ${destFile.name} (${formatSize(destFile.length())})",
                        Snackbar.LENGTH_LONG
                    )
                    snackbar.setAction("Localizar") {
                        val viewIntent = FileListActivity.createViewIntent(Paths.get(destFile.parentFile!!.absolutePath))
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(viewIntent)
                        finish()
                    }
                    snackbar.show()
                } else {
                    Toast.makeText(
                        this@InstalledAppsActivity,
                        "Error al extraer la aplicación. Revisa los permisos.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun extractSingleApk(sourcePath: String, destFile: File): Boolean {
        return try {
            val src = File(sourcePath)
            if (src.canRead()) {
                FileInputStream(src).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else if (ApkInstallHelper.isRootAvailable()) {
                val cmd = Shell.cmd("cp \"$sourcePath\" \"${destFile.absolutePath}\" && chmod 644 \"${destFile.absolutePath}\"").exec()
                cmd.isSuccess
            } else {
                false
            }
        } catch (e: Exception) {
            if (ApkInstallHelper.isRootAvailable()) {
                val cmd = Shell.cmd("cp \"$sourcePath\" \"${destFile.absolutePath}\" && chmod 644 \"${destFile.absolutePath}\"").exec()
                cmd.isSuccess
            } else {
                false
            }
        }
    }

    private fun extractSplitApk(app: AppModel, destFile: File): Boolean {
        return try {
            ZipOutputStream(FileOutputStream(destFile)).use { zos ->
                // 1. base.apk
                val baseFile = File(app.sourceDir)
                if (baseFile.canRead()) {
                    zos.putNextEntry(ZipEntry("base.apk"))
                    baseFile.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                } else if (ApkInstallHelper.isRootAvailable()) {
                    val tmpBase = File(cacheDir, "tmp_base_${System.currentTimeMillis()}.apk")
                    Shell.cmd("cp \"${app.sourceDir}\" \"${tmpBase.absolutePath}\" && chmod 644 \"${tmpBase.absolutePath}\"").exec()
                    if (tmpBase.exists()) {
                        zos.putNextEntry(ZipEntry("base.apk"))
                        tmpBase.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                        tmpBase.delete()
                    }
                }

                // 2. split APKs
                for (splitPath in app.splitSourceDirs) {
                    val splitFile = File(splitPath)
                    val entryName = splitFile.name
                    if (splitFile.canRead()) {
                        zos.putNextEntry(ZipEntry(entryName))
                        splitFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    } else if (ApkInstallHelper.isRootAvailable()) {
                        val tmpSplit = File(cacheDir, "tmp_split_${System.currentTimeMillis()}_$entryName")
                        Shell.cmd("cp \"$splitPath\" \"${tmpSplit.absolutePath}\" && chmod 644 \"${tmpSplit.absolutePath}\"").exec()
                        if (tmpSplit.exists()) {
                            zos.putNextEntry(ZipEntry(entryName))
                            tmpSplit.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                            tmpSplit.delete()
                        }
                    }
                }
            }
            destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            destFile.delete()
            false
        }
    }

    private fun getCertificateSha256(context: Context, packageName: String): String {
        return try {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else null
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            }
            val cert = signatures?.firstOrNull()?.toByteArray() ?: return "No disponible"
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(cert)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "No disponible"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_manager_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.queryHint = getString(R.string.search)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query ?: ""
                filterApps()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText ?: ""
                filterApps()
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_sort -> {
                showSortDialog()
                return true
            }
            R.id.action_refresh -> {
                loadApps()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        return dateFormat.format(Date(timestamp))
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format(Locale.US, "%.2f MB", mb)
    }

    inner class AppsAdapter(
        private val items: List<AppModel>,
        private val onClick: (AppModel) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: InstalledAppItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = InstalledAppItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = items[position]
            with(holder.binding) {
                appName.text = if (app.isEnabled) app.label else "${app.label} [Inhabilitada]"
                appSize.text = formatSize(app.sizeBytes)
                if (app.isSplit) {
                    appSplitBadge.visibility = View.VISIBLE
                    appSplitBadge.text = "APKS"
                } else {
                    appSplitBadge.visibility = View.GONE
                }
                appVersion.text = "v${app.versionName} (${app.versionCode})"
                appPackage.text = app.packageName
                val minSdkStr = if (app.minSdkVersion > 0) "${app.minSdkVersion}" else "1"
                val dateStr = formatDate(app.lastUpdateTime)
                appMetaInfo.text = "SDK: $minSdkStr - ${app.targetSdkVersion}  •  $dateStr"

                if (app.icon != null) {
                    appIcon.setImageDrawable(app.icon)
                } else {
                    appIcon.setImageResource(R.drawable.file_apk_icon)
                }
                root.alpha = if (app.isEnabled) 1.0f else 0.5f
                root.setOnClickListener { onClick(app) }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
