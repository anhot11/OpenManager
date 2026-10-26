/*
 * OpenManager - Dual-Pane File Explorer (MT Manager Style)
 * Supports side-by-side Panel A & Panel B navigation with instant synchronization,
 * unified top toolbar, MT-style bottom action bar, MT-style navigation drawer, and 3-dots overflow popup.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings as AndroidSettings
import android.text.format.Formatter
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.commit
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.about.AboutActivity
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.appmanager.InstalledAppsActivity
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.ftpserver.FtpServerActivity
import me.zhanghai.android.files.ftpserver.FtpServerService
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.settings.SettingsActivity
import me.zhanghai.android.files.storage.AddDocumentTreeActivity
import me.zhanghai.android.files.storage.AddStorageDialogActivity
import me.zhanghai.android.files.storage.FileSystemRoot
import me.zhanghai.android.files.storage.PrimaryStorageVolume
import me.zhanghai.android.files.storage.Storage
import me.zhanghai.android.files.storage.Storages
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.viewer.text.TextEditorActivity

class FileListActivity : AppActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var fragmentA: FileListFragment
    private lateinit var fragmentB: FileListFragment

    private lateinit var mainToolbar: Toolbar
    private lateinit var tvPathTitle: TextView
    private lateinit var tvStatsSubtitle: TextView
    private lateinit var btnMainMore: ImageButton

    private lateinit var containerA: FrameLayout
    private lateinit var containerB: FrameLayout
    private lateinit var paneDivider: View

    private lateinit var bottomBarNormal: LinearLayout
    private lateinit var bottomBarSelection: LinearLayout

    private lateinit var btnBottomBack: ImageButton
    private lateinit var btnBottomForward: ImageButton
    private lateinit var btnBottomAdd: ImageButton
    private lateinit var btnBottomSwap: ImageButton
    private lateinit var btnBottomUp: ImageButton

    private lateinit var btnSelectAll: MaterialButton
    private lateinit var btnInvertSelection: MaterialButton
    private lateinit var btnCopyOpposite: MaterialButton
    private lateinit var btnMoveOpposite: MaterialButton
    private lateinit var btnDeleteSelection: MaterialButton
    private lateinit var btnMoreSelection: MaterialButton

    private lateinit var progressRootStorage: ProgressBar
    private lateinit var tvRootStorageStats: TextView
    private lateinit var progressInternalStorage: ProgressBar
    private lateinit var tvInternalStorageStats: TextView
    private lateinit var layoutCustomStorages: LinearLayout
    private var hasRequestedPermissionThisSession = false

    private var activePanelIndex = 0 // 0 = Panel A, 1 = Panel B

    val activeFragment: FileListFragment
        get() = if (activePanelIndex == 0) fragmentA else fragmentB

    fun getOppositeFragment(fragment: FileListFragment): FileListFragment =
        if (fragment == fragmentA) fragmentB else fragmentA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.file_list_activity)

        drawerLayout = findViewById(R.id.drawerLayout)
        mainToolbar = findViewById(R.id.mainToolbar)
        mainToolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        btnMainMore = findViewById(R.id.btnMainMore)
        btnMainMore.setOnClickListener {
            showMTMoreMenu(it)
        }

        tvPathTitle = findViewById(R.id.tvPathTitle)
        tvStatsSubtitle = findViewById(R.id.tvStatsSubtitle)
        findViewById<View>(R.id.layoutPathTitle)?.let { layout ->
            layout.setOnClickListener {
                showJumpToPathDialog()
            }
            layout.setOnLongClickListener {
                try {
                    val fullPath = activeFragment.currentPath.toDisplayPath(trailingSlash = true)
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("path", fullPath))
                    android.widget.Toast.makeText(this, "Ruta copiada: $fullPath", android.widget.Toast.LENGTH_SHORT).show()
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        containerA = findViewById(R.id.containerPanelA)
        containerB = findViewById(R.id.containerPanelB)
        paneDivider = findViewById(R.id.paneDivider)

        containerA.setOnClickListener { onPanelFocused(fragmentA) }
        containerB.setOnClickListener { onPanelFocused(fragmentB) }

        bottomBarNormal = findViewById(R.id.bottomBarNormal)
        bottomBarSelection = findViewById(R.id.bottomBarSelection)

        btnBottomBack = findViewById(R.id.btnBottomBack)
        btnBottomForward = findViewById(R.id.btnBottomForward)
        btnBottomAdd = findViewById(R.id.btnBottomAdd)
        btnBottomSwap = findViewById(R.id.btnBottomSwap)
        btnBottomUp = findViewById(R.id.btnBottomUp)

        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnInvertSelection = findViewById(R.id.btnInvertSelection)
        btnCopyOpposite = findViewById(R.id.btnCopyOpposite)
        btnMoveOpposite = findViewById(R.id.btnMoveOpposite)
        btnDeleteSelection = findViewById(R.id.btnDeleteSelection)
        btnMoreSelection = findViewById(R.id.btnMoreSelection)

        if (savedInstanceState == null) {
            val intentA = Intent(intent)
            if (intentA.extraPath == null) {
                intentA.extraPath = Settings.FILE_LIST_DEFAULT_DIRECTORY.valueCompat
            }
            fragmentA = FileListFragment().putArgs(FileListFragment.Args(intentA))

            val intentB = Intent(intent)
            if (intentB.extraPath == null) {
                intentB.extraPath = Settings.FILE_LIST_DEFAULT_DIRECTORY_RIGHT.valueCompat
            }
            fragmentB = FileListFragment().putArgs(FileListFragment.Args(intentB))

            supportFragmentManager.commit {
                add(R.id.containerPanelA, fragmentA, TAG_PANEL_A)
                add(R.id.containerPanelB, fragmentB, TAG_PANEL_B)
            }
        } else {
            fragmentA = supportFragmentManager.findFragmentByTag(TAG_PANEL_A) as FileListFragment
            fragmentB = supportFragmentManager.findFragmentByTag(TAG_PANEL_B) as FileListFragment
            activePanelIndex = savedInstanceState.getInt(KEY_ACTIVE_PANEL, 0)
        }

        if (Settings.AUTO_START_SERVICES.valueCompat == "1") {
            try {
                FtpServerService.start(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (Settings.REQUEST_ROOT_PERMISSION.valueCompat) {
            kotlin.concurrent.thread {
                try {
                    com.topjohnwu.superuser.Shell.getShell()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        setupDrawerControls()
        setupBottomBarControls()
        setupBackPressedHandler()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        syncPanelColors()
        updateTopBarInfo()
        syncBottomBarSelectionState()
        observePanelPathsAndStats()
        updateDrawerStats()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestStoragePermissions()
        updateDrawerStats()
    }

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                if (!hasRequestedPermissionThisSession) {
                    hasRequestedPermissionThisSession = true
                    showAllFilesPermissionDialog()
                }
            } else {
                if (::fragmentA.isInitialized && ::fragmentB.isInitialized) {
                    fragmentA.reload()
                    fragmentB.reload()
                }
            }
        } else {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                if (!hasRequestedPermissionThisSession) {
                    hasRequestedPermissionThisSession = true
                    requestPermissions(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        1001
                    )
                }
            } else {
                if (::fragmentA.isInitialized && ::fragmentB.isInitialized) {
                    fragmentA.reload()
                    fragmentB.reload()
                }
            }
        }
    }

    private fun showAllFilesPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permiso de almacenamiento")
            .setMessage("OpenManager necesita acceso total a todos los archivos para poder gestionar, copiar, mover, crear y editar archivos en tu dispositivo.")
            .setPositiveButton("Conceder permiso") { _, _ ->
                try {
                    val intent = Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (e2: Exception) {
                        showToast("No se pudo abrir la pantalla de ajustes de archivos")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                if (::fragmentA.isInitialized && ::fragmentB.isInitialized) {
                    fragmentA.reload()
                    fragmentB.reload()
                }
            }
        }
    }

    private fun setupDrawerControls() {
        findViewById<View>(R.id.btnDrawerSettings).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.drawerItemRoot).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            activeFragment.navigateTo(Paths.get("/"))
        }

        findViewById<View>(R.id.drawerItemInternal).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            activeFragment.navigateTo(Paths.get("/storage/emulated/0"))
        }

        findViewById<View>(R.id.drawerItemDownloads)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            activeFragment.navigateTo(Paths.get("/storage/emulated/0/Download"))
        }

        findViewById<View>(R.id.drawerItemAndroidData)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            val resolved = PathNavigator.resolve("/storage/emulated/0/Android/data", activeFragment.currentPath)
            if (resolved != null) {
                activeFragment.navigateTo(resolved)
            } else {
                activeFragment.navigateTo(Paths.get("/storage/emulated/0/Android/data"))
            }
        }

        layoutCustomStorages = findViewById(R.id.layoutCustomStorages)
        findViewById<View>(R.id.drawerItemAddStorage).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(AddStorageDialogActivity::class.createIntent())
        }
        observeCustomStorages()

        findViewById<View>(R.id.drawerItemApktool)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            ApktoolHelper.showApktoolHubDialog(this, activeFragment.currentPath)
        }

        findViewById<View>(R.id.drawerItemInstalledApps)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, InstalledAppsActivity::class.java))
        }

        findViewById<View>(R.id.drawerItemTextEditor).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, TextEditorActivity::class.java))
        }

        findViewById<View>(R.id.drawerItemFtp).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, FtpServerActivity::class.java))
        }

        findViewById<View>(R.id.drawerItemTerminal)?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            activeFragment.openTerminal()
        }

        val drawerItemRecycleBin = findViewById<View>(R.id.drawerItemRecycleBin)
        drawerItemRecycleBin?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            val recyclePath = Settings.recycleBinDirectory
            val recycleDir = java.io.File(recyclePath.toString())
            if (!recycleDir.exists()) {
                recycleDir.mkdirs()
            }
            activeFragment.navigateTo(recyclePath)
        }
        Settings.ENABLE_RECYCLE_BIN.observe(this) { enabled ->
            drawerItemRecycleBin?.isVisible = enabled
        }

        findViewById<View>(R.id.drawerItemSettings).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.drawerItemAbout).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, AboutActivity::class.java))
        }

        progressRootStorage = findViewById(R.id.progressRootStorage)
        tvRootStorageStats = findViewById(R.id.tvRootStorageStats)
        progressInternalStorage = findViewById(R.id.progressInternalStorage)
        tvInternalStorageStats = findViewById(R.id.tvInternalStorageStats)
    }

    private fun observeCustomStorages() {
        Settings.STORAGES.observe(this) { storages ->
            layoutCustomStorages.removeAllViews()
            for (storage in storages) {
                if (storage is FileSystemRoot || storage is PrimaryStorageVolume) {
                    continue
                }
                val itemView = layoutInflater.inflate(R.layout.drawer_custom_storage_item, layoutCustomStorages, false)
                val iconView = itemView.findViewById<ImageView>(R.id.ivStorageIcon)
                val titleView = itemView.findViewById<TextView>(R.id.tvStorageTitle)
                val subtitleView = itemView.findViewById<TextView>(R.id.tvStorageSubtitle)

                iconView.setImageResource(storage.iconRes)
                titleView.text = storage.getName(this)
                subtitleView.text = storage.description

                itemView.setOnClickListener {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    storage.path?.let { path -> activeFragment.navigateTo(path) }
                }
                itemView.setOnLongClickListener {
                    showCustomStorageOptions(storage)
                    true
                }
                layoutCustomStorages.addView(itemView)
            }
        }
    }

    private fun showCustomStorageOptions(storage: Storage) {
        val options = arrayOf("Editar", "Eliminar")
        MaterialAlertDialogBuilder(this)
            .setTitle(storage.getName(this))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(storage.createEditIntent())
                    1 -> Storages.remove(storage)
                }
            }
            .show()
    }

    private fun updateDrawerStats() {
        try {
            val rootStat = StatFs("/")
            val rootTotal = rootStat.totalBytes
            val rootAvail = rootStat.availableBytes
            val rootUsed = rootTotal - rootAvail
            val rootPercent = if (rootTotal > 0) ((rootUsed * 100) / rootTotal).toInt() else 100
            progressRootStorage.progress = rootPercent
            val rootUsedStr = Formatter.formatFileSize(this, rootUsed)
            val rootAvailStr = Formatter.formatFileSize(this, rootAvail)
            tvRootStorageStats.text = "$rootUsedStr usado, $rootAvailStr disponible ($rootPercent%)"
        } catch (e: Exception) {
            tvRootStorageStats.text = "/ (Raíz del sistema)"
        }

        try {
            val emulatedStat = StatFs("/storage/emulated/0")
            val emulatedTotal = emulatedStat.totalBytes
            val emulatedAvail = emulatedStat.availableBytes
            val emulatedUsed = emulatedTotal - emulatedAvail
            val emulatedPercent = if (emulatedTotal > 0) ((emulatedUsed * 100) / emulatedTotal).toInt() else 80
            progressInternalStorage.progress = emulatedPercent
            val emulatedUsedStr = Formatter.formatFileSize(this, emulatedUsed)
            val emulatedAvailStr = Formatter.formatFileSize(this, emulatedAvail)
            tvInternalStorageStats.text = "$emulatedUsedStr usado, $emulatedAvailStr disponible ($emulatedPercent%)"
        } catch (e: Exception) {
            tvInternalStorageStats.text = "/storage/emulated/0"
        }
    }

    private fun showMTMoreMenu(anchor: View) {
        val popup = PopupMenu(this, anchor, GravityCompat.END)
        popup.inflate(R.menu.mt_overflow_menu)
        try {
            val field = popup.javaClass.getDeclaredField("mPopup")
            field.isAccessible = true
            val menuPopupHelper = field.get(popup) as? MenuPopupHelper
            menuPopupHelper?.setForceShowIcon(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            (popup.menu as? androidx.appcompat.view.menu.MenuBuilder)?.setOptionalIconsVisible(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.mt_menu_refresh -> {
                    activeFragment.reload()
                    true
                }
                R.id.mt_menu_search -> {
                    showSearchDialog()
                    true
                }
                R.id.mt_menu_select_all -> {
                    activeFragment.selectAllFiles()
                    true
                }
                R.id.mt_menu_filter -> {
                    showSearchDialog()
                    true
                }
                R.id.mt_menu_sort -> {
                    activeFragment.showSortOptionsDialog()
                    true
                }
                R.id.mt_menu_terminal -> {
                    activeFragment.openTerminal()
                    true
                }
                R.id.mt_menu_hidden_files -> {
                    val current = Settings.FILE_LIST_SHOW_HIDDEN_FILES.valueCompat
                    Settings.FILE_LIST_SHOW_HIDDEN_FILES.putValue(!current)
                    true
                }
                R.id.mt_menu_add_bookmark -> {
                    activeFragment.addBookmarkCurrentPath()
                    true
                }
                R.id.mt_menu_set_as_home -> {
                    showToast("Ruta fijada como inicio")
                    true
                }
                R.id.mt_menu_swap_windows -> {
                    btnBottomSwap.performClick()
                    true
                }
                R.id.mt_menu_export_file_list -> {
                    val allFiles = activeFragment.viewModel.fileListStateful.value ?: emptyList()
                    ExportFileListHelper.showDialog(this, activeFragment.currentPath, allFiles, activeFragment.selectedFiles.toList()) {
                        activeFragment.clearSelectedFiles()
                    }
                    true
                }
                R.id.mt_menu_preferences -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.mt_menu_exit -> {
                    finishAffinity()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun setupBottomBarControls() {
        updateBottomBarMargin()
        Settings.ADD_BOTTOM_MARGIN.observe(this) { updateBottomBarMargin() }
        Settings.BOTTOM_MARGIN_SIZE.observe(this) { updateBottomBarMargin() }

        // Normal Mode Buttons: [<] [>] [+] [⇆] [↑]
        btnBottomBack.setOnClickListener {
            if (activeFragment.canNavigateUp) {
                activeFragment.navigateUp()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        btnBottomForward.setOnClickListener {
            // Forward navigation placeholder / no-op
        }

        btnBottomAdd.setOnClickListener {
            showCreateDialog()
        }

        btnBottomSwap.setOnClickListener {
            try {
                val pathA = fragmentA.currentPath
                val pathB = fragmentB.currentPath
                fragmentA.navigateTo(pathB)
                fragmentB.navigateTo(pathA)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        btnBottomUp.setOnClickListener {
            activeFragment.navigateUp()
        }

        // Selection Mode Buttons
        btnSelectAll.setOnClickListener {
            activeFragment.selectAllFiles()
        }

        btnInvertSelection.setOnClickListener {
            activeFragment.invertSelection()
        }

        btnCopyOpposite.setOnClickListener {
            val selected = activeFragment.selectedFiles
            if (selected.isNotEmpty()) {
                val opposite = getOppositeFragment(activeFragment)
                activeFragment.copyFilesTo(selected, opposite.currentPath)
            }
        }

        btnMoveOpposite.setOnClickListener {
            val selected = activeFragment.selectedFiles
            if (selected.isNotEmpty()) {
                val opposite = getOppositeFragment(activeFragment)
                activeFragment.moveFilesTo(selected, opposite.currentPath)
            }
        }

        btnDeleteSelection.setOnClickListener {
            activeFragment.deleteSelectedFiles()
        }

        btnMoreSelection.setOnClickListener { view ->
            showMoreSelectionMenu(view)
        }
    }

    private fun updateBottomBarMargin() {
        val bottomBar = findViewById<View>(R.id.bottomBar) ?: return
        val addMargin = Settings.ADD_BOTTOM_MARGIN.valueCompat
        val marginDp = if (addMargin) {
            Settings.BOTTOM_MARGIN_SIZE.valueCompat.toIntOrNull() ?: 12
        } else {
            0
        }
        val density = resources.displayMetrics.density
        val marginPx = (marginDp * density).toInt()
        val baseHeightPx = (48 * density).toInt()

        val lp = bottomBar.layoutParams
        lp.height = baseHeightPx + marginPx
        bottomBar.layoutParams = lp
        bottomBar.setPadding(0, 0, 0, marginPx)
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }
                if (activeFragment.selectedFiles.isNotEmpty()) {
                    activeFragment.clearSelectedFiles()
                    return
                }
                if (activeFragment.canNavigateUp) {
                    activeFragment.navigateUp()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    fun onPanelFocused(fragment: FileListFragment) {
        val newIndex = if (fragment == fragmentA) 0 else 1
        if (activePanelIndex != newIndex) {
            activePanelIndex = newIndex
            syncPanelColors()
            updateTopBarInfo()
            syncBottomBarSelectionState()
            fragmentA.updateBackCallbackState()
            fragmentB.updateBackCallbackState()
            invalidateOptionsMenu()
        }
    }

    private fun syncPanelColors() {
        // Sampled from MT Manager: Active focused panel = #363636, Inactive panel = #242424
        if (activePanelIndex == 0) {
            containerA.setBackgroundColor(0xFF363636.toInt())
            containerB.setBackgroundColor(0xFF242424.toInt())
        } else {
            containerA.setBackgroundColor(0xFF242424.toInt())
            containerB.setBackgroundColor(0xFF363636.toInt())
        }
    }

    private fun getDiskStats(): String {
        return try {
            val stat = StatFs(android.os.Environment.getExternalStorageDirectory().path)
            val available = Formatter.formatFileSize(this, stat.availableBytes)
            val total = Formatter.formatFileSize(this, stat.totalBytes)
            "Disk: $available/$total"
        } catch (e: Exception) {
            ""
        }
    }

    private fun updateTopBarInfo() {
        if (!::fragmentA.isInitialized || !::fragmentB.isInitialized) return
        if (!activeFragment.isAdded) return
        try {
            val path = activeFragment.currentPath
            val pathStr = path.toDisplayPath(trailingSlash = true)
            tvPathTitle.text = pathStr
            val stats = activeFragment.currentStatsSubtitle
            val disk = getDiskStats()
            tvStatsSubtitle.text = when {
                stats.isNotEmpty() && disk.isNotEmpty() -> "$stats  $disk"
                stats.isNotEmpty() -> stats
                disk.isNotEmpty() -> disk
                else -> "Listo"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showJumpToPathDialog() {
        if (!::fragmentA.isInitialized || !::fragmentB.isInitialized) return
        val currentDisplayPath = activeFragment.currentPath.toDisplayPath(trailingSlash = true)
        val context = this
        val editText = androidx.appcompat.widget.AppCompatEditText(context).apply {
            setText(currentDisplayPath)
            setSingleLine(true)
            selectAll()
            setTextColor(android.graphics.Color.WHITE)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            supportBackgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3"))
            val paddingPx = (16 * resources.displayMetrics.density).toInt()
            setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx / 2)
        }

        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(editText)

            val buttonBar = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
            }

            fun createChip(text: String, onClick: () -> Unit): android.widget.TextView {
                return android.widget.TextView(context).apply {
                    this.text = text
                    setTextColor(android.graphics.Color.parseColor("#90CAF9"))
                    textSize = 12f
                    val padH = (10 * resources.displayMetrics.density).toInt()
                    val padV = (5 * resources.displayMetrics.density).toInt()
                    setPadding(padH, padV, padH, padV)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(android.graphics.Color.parseColor("#262626"))
                        cornerRadius = 8 * resources.displayMetrics.density
                        setStroke((1 * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#444444"))
                    }
                    val params = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() }
                    layoutParams = params
                    setOnClickListener { onClick() }
                }
            }

            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            buttonBar.addView(createChip("Copiar") {
                val text = editText.text?.toString() ?: ""
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("path", text))
                android.widget.Toast.makeText(context, "Ruta copiada", android.widget.Toast.LENGTH_SHORT).show()
            })
            buttonBar.addView(createChip("Pegar") {
                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                if (!clipText.isNullOrEmpty()) {
                    editText.setText(clipText)
                    editText.setSelection(clipText.length)
                }
            })
            buttonBar.addView(createChip("Interno") {
                editText.setText("/storage/emulated/0/")
                editText.setSelection(editText.text?.length ?: 0)
            })
            buttonBar.addView(createChip("Descargas") {
                editText.setText("/storage/emulated/0/Download/")
                editText.setSelection(editText.text?.length ?: 0)
            })
            buttonBar.addView(createChip("Android/data") {
                editText.setText("/storage/emulated/0/Android/data/")
                editText.setSelection(editText.text?.length ?: 0)
            })

            val hsv = android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(buttonBar)
            }
            addView(hsv)
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(R.string.jump_to_path)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val target = editText.text?.toString()?.trim() ?: ""
                if (target.isNotEmpty()) {
                    try {
                        val resolved = PathNavigator.resolve(target, activeFragment.currentPath)
                        activeFragment.navigateTo(resolved)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .create()

        dialog.setOnShowListener {
            editText.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        dialog.show()
    }

    private fun syncBottomBarSelectionState() {
        if (!::fragmentA.isInitialized || !::fragmentB.isInitialized) return
        if (!activeFragment.isAdded) return
        val selected = activeFragment.selectedFiles
        if (selected.isNotEmpty()) {
            bottomBarNormal.visibility = View.GONE
            bottomBarSelection.visibility = View.VISIBLE
            btnSelectAll.text = "Todo (${selected.size})"
        } else {
            bottomBarNormal.visibility = View.VISIBLE
            bottomBarSelection.visibility = View.GONE
        }
    }

    private fun observePanelPathsAndStats() {
        fragmentA.observeCurrentPath(this) { path ->
            if (activePanelIndex == 0) {
                updateTopBarInfo()
            }
        }
        fragmentB.observeCurrentPath(this) { path ->
            if (activePanelIndex == 1) {
                updateTopBarInfo()
            }
        }

        fragmentA.onPathOrStatsChanged = { path, stats ->
            if (activePanelIndex == 0) {
                val pathStr = path.toDisplayPath(trailingSlash = true)
                tvPathTitle.text = pathStr
                val disk = getDiskStats()
                tvStatsSubtitle.text = if (disk.isNotEmpty()) "$stats  $disk" else stats
            }
        }
        fragmentB.onPathOrStatsChanged = { path, stats ->
            if (activePanelIndex == 1) {
                val pathStr = path.toDisplayPath(trailingSlash = true)
                tvPathTitle.text = pathStr
                val disk = getDiskStats()
                tvStatsSubtitle.text = if (disk.isNotEmpty()) "$stats  $disk" else stats
            }
        }

        val selectionObserver: (FileListFragment, Boolean, Int) -> Unit = { frag, hasSelection, count ->
            if (frag == activeFragment) {
                if (hasSelection) {
                    bottomBarNormal.visibility = View.GONE
                    bottomBarSelection.visibility = View.VISIBLE
                    btnSelectAll.text = "Todo ($count)"
                } else {
                    bottomBarNormal.visibility = View.VISIBLE
                    bottomBarSelection.visibility = View.GONE
                }
            }
        }
        fragmentA.onSelectionChanged = selectionObserver
        fragmentB.onSelectionChanged = selectionObserver
    }

    private fun showCreateDialog() {
        val items = arrayOf("Nueva carpeta", "Nuevo archivo")
        MaterialAlertDialogBuilder(this)
            .setTitle("Nuevo")
            .setItems(items) { _, which ->
                if (which == 0) {
                    activeFragment.showCreateDirectoryDialog()
                } else {
                    activeFragment.showCreateFileDialog()
                }
            }
            .show()
    }

    fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Nombre del archivo o extensión"
            setSingleLine()
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Buscar en panel actual")
            .setView(input)
            .setPositiveButton("Buscar") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    activeFragment.searchFiles(query)
                }
            }
            .setNegativeButton("Restablecer") { _, _ ->
                activeFragment.stopSearching()
            }
            .show()
    }

    private fun showMoreSelectionMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.apply {
            add(0, 4, 0, "Invertir selección")
            add(0, 1, 0, "Desmarcar todo")
            add(0, 2, 0, getString(R.string.action_export_file_list))
            if (activeFragment.selectedFiles.size == 2) {
                add(0, 3, 0, getString(R.string.action_swap_file_names))
            }
            if (activeFragment.selectedFiles.size == 1) {
                add(0, 5, 0, getString(R.string.file_item_action_properties))
            }
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    activeFragment.clearSelectedFiles()
                    true
                }
                2 -> {
                    val allFiles = activeFragment.viewModel.fileListStateful.value ?: emptyList()
                    ExportFileListHelper.showDialog(this, activeFragment.currentPath, allFiles, activeFragment.selectedFiles.toList()) {
                        activeFragment.clearSelectedFiles()
                    }
                    true
                }
                3 -> {
                    val files = activeFragment.selectedFiles.toList()
                    if (files.size == 2) {
                        FileSwapNamesHelper.swap(this, files[0].path, files[1].path) {
                            activeFragment.clearSelectedFiles()
                            activeFragment.reload()
                        }
                    }
                    true
                }
                4 -> {
                    activeFragment.invertSelection()
                    true
                }
                5 -> {
                    val file = activeFragment.selectedFiles.firstOrNull()
                    if (file != null) {
                        activeFragment.showPropertiesDialog(file)
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_PANEL, activePanelIndex)
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent): Boolean {
        if (activeFragment.onKeyShortcut(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        private const val TAG_PANEL_A = "panel_a"
        private const val TAG_PANEL_B = "panel_b"
        private const val KEY_ACTIVE_PANEL = "active_panel"

        fun createViewIntent(path: Path): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_VIEW)
                .apply { extraPath = path }
    }

    class OpenFileContract : ActivityResultContract<List<MimeType>, Path?>() {
        override fun createIntent(context: Context, input: List<MimeType>): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT)
                .setType(MimeType.ANY.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, input.map { it.value }.toTypedArray())

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class CreateFileContract : ActivityResultContract<Triple<MimeType, String?, Path?>, Path?>() {
        override fun createIntent(
            context: Context,
            input: Triple<MimeType, String?, Path?>
        ): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_CREATE_DOCUMENT)
                .setType(input.first.value)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .apply {
                    input.second?.let { putExtra(Intent.EXTRA_TITLE, it) }
                    input.third?.let { extraPath = it }
                }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }

    class OpenDirectoryContract : ActivityResultContract<Path?, Path?>() {
        override fun createIntent(context: Context, input: Path?): Intent =
            FileListActivity::class.createIntent()
                .setAction(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .apply { input?.let { extraPath = it } }

        override fun parseResult(resultCode: Int, intent: Intent?): Path? =
            if (resultCode == RESULT_OK) intent?.extraPath else null
    }
}
