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
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.compat.longVersionCodeCompat
import me.zhanghai.android.files.databinding.ActivityInstalledAppsBinding
import me.zhanghai.android.files.databinding.DialogInstalledAppDetailsBinding
import me.zhanghai.android.files.databinding.InstalledAppItemBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class InstalledAppsActivity : AppActivity() {

    private lateinit var binding: ActivityInstalledAppsBinding
    private val allApps = mutableListOf<AppModel>()
    private val displayedApps = mutableListOf<AppModel>()
    private lateinit var adapter: AppsAdapter

    private var showSystemApps = false
    private var searchQuery = ""

    data class AppModel(
        val label: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val sourceDir: String,
        val dataDir: String,
        val targetSdkVersion: Int,
        val minSdkVersion: Int,
        val sizeBytes: Long,
        val isSystem: Boolean,
        val icon: Drawable?
    )

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
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            val list = mutableListOf<AppModel>()

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
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
                val apkFile = File(appInfo.sourceDir)
                val size = if (apkFile.exists()) apkFile.length() else 0L

                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo.minSdkVersion
                } else 0

                list.add(
                    AppModel(
                        label = label,
                        packageName = pkg.packageName,
                        versionName = pkg.versionName ?: "1.0",
                        versionCode = pkg.longVersionCodeCompat,
                        sourceDir = appInfo.sourceDir,
                        dataDir = appInfo.dataDir ?: "",
                        targetSdkVersion = appInfo.targetSdkVersion,
                        minSdkVersion = minSdk,
                        sizeBytes = size,
                        isSystem = isSystem,
                        icon = icon
                    )
                )
            }

            list.sortBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(list)
                binding.progressBar.visibility = View.GONE
                filterApps()
            }
        }
    }

    private fun filterApps() {
        displayedApps.clear()
        val query = searchQuery.trim().lowercase()

        for (app in allApps) {
            if (app.isSystem != showSystemApps) continue
            if (query.isNotEmpty()) {
                val match = app.label.lowercase().contains(query) || app.packageName.lowercase().contains(query)
                if (!match) continue
            }
            displayedApps.add(app)
        }

        adapter.notifyDataSetChanged()
        binding.emptyText.visibility = if (displayedApps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAppDetails(app: AppModel) {
        val dialogBinding = DialogInstalledAppDetailsBinding.inflate(layoutInflater)
        if (app.icon != null) {
            dialogBinding.dialogAppIcon.setImageDrawable(app.icon)
        } else {
            dialogBinding.dialogAppIcon.setImageResource(R.drawable.file_apk_icon)
        }
        dialogBinding.dialogAppName.text = app.label
        dialogBinding.dialogAppVersion.text = "v${app.versionName} (${app.versionCode})"
        dialogBinding.dialogAppPackage.text = app.packageName
        dialogBinding.dialogAppVersionCode.text = app.versionCode.toString()
        dialogBinding.dialogAppSize.text = formatSize(app.sizeBytes)
        dialogBinding.dialogAppTargetSdk.text = "Android API ${app.targetSdkVersion}"
        dialogBinding.dialogAppMinSdk.text = if (app.minSdkVersion > 0) "Android API ${app.minSdkVersion}" else "-"
        dialogBinding.dialogAppApkPath.text = app.sourceDir
        dialogBinding.dialogAppDataPath.text = app.dataDir

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnAppLaunch.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "No launchable activity found", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.btnAppDetails.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
            }
            startActivity(intent)
            dialog.dismiss()
        }

        dialogBinding.btnAppExtract.setOnClickListener {
            dialog.dismiss()
            extractApk(app)
        }

        dialog.show()
    }

    private fun extractApk(app: AppModel) {
        val destDir = File("/storage/emulated/0")
        if (!destDir.exists()) destDir.mkdirs()

        val cleanName = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val destFile = File(destDir, "${cleanName}_v${app.versionName}.apk")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                FileInputStream(File(app.sourceDir)).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@InstalledAppsActivity,
                        getString(R.string.extract_apk_success, destFile.absolutePath),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@InstalledAppsActivity,
                        getString(R.string.extract_apk_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.file_list, menu)
        // Keep search only
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.itemId != R.id.action_search) {
                item.isVisible = false
            } else {
                item.isVisible = true
                val searchView = item.actionView as? SearchView
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
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format("%.2f MB", mb)
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
                appName.text = app.label
                appVersionSize.text = "v${app.versionName} • ${formatSize(app.sizeBytes)}"
                appPackage.text = app.packageName
                if (app.icon != null) {
                    appIcon.setImageDrawable(app.icon)
                } else {
                    appIcon.setImageResource(R.drawable.file_apk_icon)
                }
                root.setOnClickListener { onClick(app) }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
