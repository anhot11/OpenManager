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
import android.graphics.Color
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
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.ui.ThemedFastScroller
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

    companion object {
        @Volatile
        private var sAppCache: List<AppModel>? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstalledAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Consistent dark status bar
        window.statusBarColor = Color.parseColor("#1F1F1F")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        }

        // 2. Query status bar height directly from framework dimensions
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resId > 0) {
            resources.getDimensionPixelSize(resId)
        } else {
            (28 * resources.displayMetrics.density).toInt()
        }

        // 3. Set top padding immediately so Toolbar is NEVER obscured by notifications/status bar
        binding.appBarLayout.updatePadding(top = statusBarHeight)

        // 4. React dynamically to window insets for display cutouts, notches, and navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val topInset = maxOf(statusBars.top, displayCutout.top, statusBarHeight)
            binding.appBarLayout.updatePadding(top = topInset)

            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            binding.recyclerView.updatePadding(bottom = navBars.bottom + (16 * resources.displayMetrics.density).toInt())
            insets
        }

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
        adapter.setHasStableIds(true)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@InstalledAppsActivity)
            setHasFixedSize(true)
            setItemViewCacheSize(30)
            adapter = this@InstalledAppsActivity.adapter
            ThemedFastScroller.create(this)
        }
    }

    private fun loadApps(forceRefresh: Boolean = false) {
        // 1. In-memory cache (0 ms)
        val memCached = sAppCache
        if (!forceRefresh && memCached != null && memCached.isNotEmpty()) {
            allApps.clear()
            allApps.addAll(memCached)
            applySort()
            updateTabCounts()
            filterApps()
            binding.progressBar.visibility = View.GONE
            return
        }

        // 2. Persistent disk cache (5-10 ms)
        if (!forceRefresh) {
            val diskCached = AppDiskCache.load(this)
            if (diskCached != null && diskCached.isNotEmpty()) {
                allApps.clear()
                allApps.addAll(diskCached)
                sAppCache = diskCached
                applySort()
                updateTabCounts()
                filterApps()
                binding.progressBar.visibility = View.GONE
                // Trigger background refresh silently to catch new installs/updates
                syncAppsInBackground(diskCached)
                return
            }
        }

        if (allApps.isEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
        }
        binding.emptyText.visibility = View.GONE

        // 3. Full scan with progressive emission
        lifecycleScope.launch(Dispatchers.IO) {
            scanAndEmitApps()
        }
    }

    private suspend fun scanAndEmitApps() {
        val pm = packageManager
        val packages = try {
            pm.getInstalledPackages(0)
        } catch (e: Exception) {
            emptyList<PackageInfo>()
        }
        if (packages.isEmpty()) {
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.emptyText.visibility = View.VISIBLE
            }
            return
        }

        val parsedList = mutableListOf<AppModel>()
        var emittedInitial = false

        val chunks = packages.chunked(30)
        for (chunk in chunks) {
            val batch = chunk.mapNotNull { pkg -> parsePackage(pm, pkg) }
            parsedList.addAll(batch)

            // Stream first batch to UI immediately!
            if (!emittedInitial && parsedList.size >= 25) {
                emittedInitial = true
                val snapshot = ArrayList(parsedList)
                withContext(Dispatchers.Main) {
                    allApps.clear()
                    allApps.addAll(snapshot)
                    applySort()
                    updateTabCounts()
                    filterApps()
                    binding.progressBar.visibility = View.GONE
                }
            }
        }

        sAppCache = parsedList
        AppDiskCache.save(this@InstalledAppsActivity, parsedList)

        withContext(Dispatchers.Main) {
            allApps.clear()
            allApps.addAll(parsedList)
            applySort()
            updateTabCounts()
            filterApps()
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun syncAppsInBackground(existing: List<AppModel>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = try {
                pm.getInstalledPackages(0)
            } catch (e: Exception) {
                return@launch
            }
            if (packages.isEmpty()) return@launch

            // Quick check: if count and latest update time match, no need to rescan
            val existingMap = existing.associateBy({ it.packageName }, { it.lastUpdateTime })
            var hasChanges = packages.size != existing.size
            if (!hasChanges) {
                for (pkg in packages) {
                    val cachedTime = existingMap[pkg.packageName]
                    if (cachedTime == null || cachedTime != pkg.lastUpdateTime) {
                        hasChanges = true
                        break
                    }
                }
            }
            if (!hasChanges) return@launch

            // Rescan and update
            scanAndEmitApps()
        }
    }

    private fun parsePackage(pm: PackageManager, pkg: PackageInfo): AppModel? {
        val appInfo = pkg.applicationInfo ?: return null
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isEnabled = appInfo.enabled
        val label = appInfo.nonLocalizedLabel?.toString()
            ?: try {
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                pkg.packageName
            }

        val splits = appInfo.splitSourceDirs?.toList() ?: emptyList()
        var size = 0L
        val src = appInfo.sourceDir
        if (!src.isNullOrEmpty()) {
            val baseFile = File(src)
            size += baseFile.length()
        }
        for (splitPath in splits) {
            val splitFile = File(splitPath)
            size += splitFile.length()
        }

        val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appInfo.minSdkVersion
        } else {
            0
        }

        return AppModel(
            label = label,
            packageName = pkg.packageName,
            versionName = pkg.versionName ?: "1.0",
            versionCode = pkg.longVersionCodeCompat,
            sourceDir = src ?: "",
            splitSourceDirs = splits,
            dataDir = appInfo.dataDir ?: "",
            targetSdkVersion = appInfo.targetSdkVersion,
            minSdkVersion = minSdk,
            sizeBytes = size,
            isSystem = isSystem,
            isEnabled = isEnabled,
            firstInstallTime = pkg.firstInstallTime,
            lastUpdateTime = pkg.lastUpdateTime
        )
    }

    private fun updateTabCounts() {
        var userCount = 0
        var systemCount = 0
        for (app in allApps) {
            if (app.isSystem) systemCount++ else userCount++
        }
        binding.tabLayout.getTabAt(0)?.text = "${getString(R.string.user_apps)} ($userCount)"
        binding.tabLayout.getTabAt(1)?.text = "${getString(R.string.system_apps)} ($systemCount)"
    }

    private fun applySort() {
        when (currentSortOrder) {
            0 -> allApps.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            1 -> allApps.sortWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.label })
            2 -> allApps.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.packageName })
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
        val cachedIcon = NativeAppIconLoader.getCached(app.packageName)
        if (cachedIcon != null) {
            dialogBinding.dialogAppIcon.setImageDrawable(cachedIcon)
        } else {
            dialogBinding.dialogAppIcon.setImageResource(R.drawable.file_apk_icon)
            lifecycleScope.launch(Dispatchers.IO) {
                val icon = NativeAppIconLoader.loadIcon(this@InstalledAppsActivity, app.packageName)
                if (icon != null) {
                    withContext(Dispatchers.Main) {
                        dialogBinding.dialogAppIcon.setImageDrawable(icon)
                    }
                }
            }
        }
        dialogBinding.dialogAppName.text = app.label
        dialogBinding.dialogAppPackage.text = app.packageName
        dialogBinding.dialogAppVersionSize.text = "v${app.versionName} (${app.versionCode}) • ${formatSize(app.sizeBytes)}"
        val minSdkStr = if (app.minSdkVersion > 0) app.minSdkVersion.toString() else "N/A"
        dialogBinding.dialogAppSdk.text = "Min SDK: $minSdkStr • Target SDK: ${app.targetSdkVersion}"

        if (app.isSplit) {
            dialogBinding.dialogAppSplits.visibility = View.VISIBLE
            dialogBinding.dialogAppSplits.text = "APKS: ${app.splitCount} archivos divididos"
        } else {
            dialogBinding.dialogAppSplits.visibility = View.GONE
        }

        val isRoot = ApkInstallHelper.isRootAvailable()
        if (isRoot) {
            dialogBinding.layoutRootActions.visibility = View.VISIBLE
            dialogBinding.tvToggleEnableTitle.text = if (app.isEnabled) "Inhabilitar / Congelar (Root)" else "Habilitar / Descongelar (Root)"
            dialogBinding.tvToggleEnableSubtitle.text = if (app.isEnabled) "Desactivar paquete con pm disable" else "Activar paquete con pm enable"
        } else {
            dialogBinding.layoutRootActions.visibility = View.GONE
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
            if (apkFile.exists()) {
                val intent = FileListActivity.createViewIntent(Paths.get(apkFile.parentFile!!.absolutePath))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "El directorio del APK no es accesible", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Localizar Datos en el explorador
        dialogBinding.btnActionLocateData.setOnClickListener {
            dialog.dismiss()
            val dataPath = if (app.dataDir.isNotEmpty()) app.dataDir else "/storage/emulated/0/Android/data/${app.packageName}"
            val intent = FileListActivity.createViewIntent(Paths.get(dataPath))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
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
            val apkFile = File(app.sourceDir)
            if (apkFile.exists() && apkFile.canRead()) {
                try {
                    val uri = apkFile.fileProviderUri
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = MimeType.APK.value
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Compartir ${app.label}"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al compartir APK: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Se requiere extraer el APK previamente para compartir", Toast.LENGTH_SHORT).show()
            }
        }

        // 8. Google Play Store
        dialogBinding.btnActionPlayStore.setOnClickListener {
            dialog.dismiss()
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}"))
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}"))
                startActivity(intent)
            }
        }

        // 9. Desinstalar
        dialogBinding.btnActionUninstall.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            startActivity(intent)
        }

        // 10. Root: Forzar detención
        dialogBinding.btnActionForceStop.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch(Dispatchers.IO) {
                val result = Shell.cmd("am force-stop ${app.packageName}").exec()
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        Toast.makeText(this@InstalledAppsActivity, "Aplicación detenida (Root)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@InstalledAppsActivity, "Error al forzar detención", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 11. Root: Limpiar datos
        dialogBinding.btnActionClearData.setOnClickListener {
            dialog.dismiss()
            MaterialAlertDialogBuilder(this)
                .setTitle("¿Limpiar datos?")
                .setMessage("Se eliminarán todos los datos y configuraciones de ${app.label}.")
                .setPositiveButton("Limpiar") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = Shell.cmd("pm clear ${app.packageName}").exec()
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                Toast.makeText(this@InstalledAppsActivity, "Datos restablecidos con éxito", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@InstalledAppsActivity, "Error al limpiar datos", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // 12. Root: Alternar Habilitar/Inhabilitar
        dialogBinding.btnActionToggleEnable.setOnClickListener {
            dialog.dismiss()
            val newEnable = !app.isEnabled
            val cmdStr = if (newEnable) "pm enable ${app.packageName}" else "pm disable-user ${app.packageName}"
            lifecycleScope.launch(Dispatchers.IO) {
                val result = Shell.cmd(cmdStr).exec()
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        val msg = if (newEnable) "Aplicación habilitada" else "Aplicación inhabilitada"
                        Toast.makeText(this@InstalledAppsActivity, msg, Toast.LENGTH_SHORT).show()
                        loadApps(forceRefresh = true)
                    } else {
                        Toast.makeText(this@InstalledAppsActivity, "Error al cambiar estado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showCopyInfoDialog(app: AppModel) {
        val certSha256 = getCertificateSha256(this, app.packageName)
        val infoItems = arrayOf(
            "Nombre: ${app.label}",
            "Paquete: ${app.packageName}",
            "Versión: ${app.versionName} (${app.versionCode})",
            "Ruta APK: ${app.sourceDir}",
            "Ruta Datos: ${app.dataDir}",
            "Firma SHA-256: $certSha256",
            "Copiar todo el informe"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Copiar información")
            .setItems(infoItems) { _, which ->
                val textToCopy = when (which) {
                    0 -> app.label
                    1 -> app.packageName
                    2 -> "${app.versionName} (${app.versionCode})"
                    3 -> app.sourceDir
                    4 -> app.dataDir
                    5 -> certSha256
                    6 -> """
                        App: ${app.label}
                        Package: ${app.packageName}
                        Version: ${app.versionName} (${app.versionCode})
                        Target SDK: ${app.targetSdkVersion}
                        Min SDK: ${app.minSdkVersion}
                        Size: ${formatSize(app.sizeBytes)}
                        APK Path: ${app.sourceDir}
                        Data Path: ${app.dataDir}
                        SHA-256: $certSha256
                    """.trimIndent()
                    else -> ""
                }
                copyText(textToCopy)
                Toast.makeText(this, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun extractApk(app: AppModel) {
        val baseDir = File("/storage/emulated/0/OpenManager/apks")
        baseDir.mkdirs()
        val targetDir = when {
            baseDir.exists() && baseDir.canWrite() -> baseDir
            else -> {
                val downloads = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OpenManager")
                downloads.mkdirs()
                if (downloads.exists() && downloads.canWrite()) downloads else (getExternalFilesDir(null) ?: filesDir)
            }
        }

        val safeLabel = app.label.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
        val extension = if (app.isSplit) "apks" else "apk"
        val destFile = File(targetDir, "${safeLabel}_v${app.versionName}.$extension")

        Toast.makeText(this, "Extrayendo ${app.label}...", Toast.LENGTH_SHORT).show()
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
                FileInputStream(src).channel.use { inChannel ->
                    FileOutputStream(destFile).channel.use { outChannel ->
                        val size = inChannel.size()
                        var transferred = 0L
                        while (transferred < size) {
                            transferred += inChannel.transferTo(transferred, size - transferred, outChannel)
                        }
                    }
                }
                destFile.exists() && destFile.length() > 0
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
            java.io.BufferedOutputStream(FileOutputStream(destFile), 128 * 1024).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zos.setLevel(java.util.zip.Deflater.BEST_SPEED)

                    // 1. base.apk
                    val baseFile = File(app.sourceDir)
                    if (baseFile.canRead()) {
                        zos.putNextEntry(ZipEntry("base.apk"))
                        baseFile.inputStream().buffered(64 * 1024).use { it.copyTo(zos, bufferSize = 64 * 1024) }
                        zos.closeEntry()
                    } else if (ApkInstallHelper.isRootAvailable()) {
                        val tmpBase = File(cacheDir, "tmp_base_${System.currentTimeMillis()}.apk")
                        try {
                            Shell.cmd("cp \"${app.sourceDir}\" \"${tmpBase.absolutePath}\" && chmod 644 \"${tmpBase.absolutePath}\"").exec()
                            if (tmpBase.exists()) {
                                zos.putNextEntry(ZipEntry("base.apk"))
                                tmpBase.inputStream().buffered(64 * 1024).use { it.copyTo(zos, bufferSize = 64 * 1024) }
                                zos.closeEntry()
                            }
                        } finally {
                            tmpBase.delete()
                        }
                    }

                    // 2. split APKs
                    for (splitPath in app.splitSourceDirs) {
                        val splitFile = File(splitPath)
                        val entryName = splitFile.name
                        if (splitFile.canRead()) {
                            zos.putNextEntry(ZipEntry(entryName))
                            splitFile.inputStream().buffered(64 * 1024).use { it.copyTo(zos, bufferSize = 64 * 1024) }
                            zos.closeEntry()
                        } else if (ApkInstallHelper.isRootAvailable()) {
                            val tmpSplit = File(cacheDir, "tmp_split_${System.currentTimeMillis()}_$entryName")
                            try {
                                Shell.cmd("cp \"$splitPath\" \"${tmpSplit.absolutePath}\" && chmod 644 \"${tmpSplit.absolutePath}\"").exec()
                                if (tmpSplit.exists()) {
                                    zos.putNextEntry(ZipEntry(entryName))
                                    tmpSplit.inputStream().buffered(64 * 1024).use { it.copyTo(zos, bufferSize = 64 * 1024) }
                                    zos.closeEntry()
                                }
                            } finally {
                                tmpSplit.delete()
                            }
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
                loadApps(forceRefresh = true)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }

    private fun formatDate(timestamp: Long): String {
        return if (timestamp > 0) dateFormat.format(Date(timestamp)) else "Desconocida"
    }

    inner class AppsAdapter(
        private val items: List<AppModel>,
        private val onClick: (AppModel) -> Unit
    ) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: InstalledAppItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun getItemId(position: Int): Long = items[position].packageName.hashCode().toLong()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = InstalledAppItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = items[position]
            with(holder.binding) {
                appName.text = app.label
                appPackage.text = app.packageName
                appVersion.text = "v${app.versionName} (${app.versionCode})"
                appSize.text = formatSize(app.sizeBytes)
                appSplitBadge.visibility = if (app.isSplit) View.VISIBLE else View.GONE

                val minSdkStr = if (app.minSdkVersion > 0) app.minSdkVersion.toString() else "?"
                val dateStr = formatDate(app.lastUpdateTime)
                appMetaInfo.text = "SDK: $minSdkStr - ${app.targetSdkVersion}  •  $dateStr"

                val cached = NativeAppIconLoader.getCached(app.packageName)
                if (cached != null) {
                    appIcon.setImageDrawable(cached)
                } else {
                    appIcon.setImageResource(R.drawable.file_apk_icon)
                    val tag = app.packageName
                    appIcon.tag = tag
                    lifecycleScope.launch(Dispatchers.IO) {
                        val drawable = NativeAppIconLoader.loadIcon(this@InstalledAppsActivity, app.packageName)
                        if (drawable != null) {
                            withContext(Dispatchers.Main) {
                                if (appIcon.tag == tag) {
                                    appIcon.setImageDrawable(drawable)
                                }
                            }
                        }
                    }
                }

                root.setOnClickListener {
                    onClick(app)
                }
            }
        }
    }
}
