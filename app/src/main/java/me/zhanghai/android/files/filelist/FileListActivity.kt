/*
 * OpenManager - Dual-Pane File Explorer (MT Manager Style)
 * Supports side-by-side Panel A & Panel B navigation with instant synchronization,
 * unified top toolbar, MT-style bottom action bar, and cross-panel 1-tap operations.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.commit
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.putArgs

class FileListActivity : AppActivity() {

    private lateinit var fragmentA: FileListFragment
    private lateinit var fragmentB: FileListFragment

    private lateinit var mainToolbar: Toolbar
    private lateinit var tvPathTitle: TextView
    private lateinit var tvStatsSubtitle: TextView

    private lateinit var btnPanelA: MaterialButton
    private lateinit var btnPanelB: MaterialButton
    private lateinit var btnDual: MaterialButton
    private lateinit var btnSwap: MaterialButton

    private lateinit var containerA: FrameLayout
    private lateinit var containerB: FrameLayout
    private lateinit var paneDivider: View

    private lateinit var bottomBarNormal: LinearLayout
    private lateinit var bottomBarSelection: LinearLayout

    private lateinit var btnBottomBack: ImageButton
    private lateinit var btnBottomSwap: ImageButton
    private lateinit var btnBottomAdd: ImageButton
    private lateinit var btnBottomSearch: ImageButton
    private lateinit var btnBottomUp: ImageButton

    private lateinit var btnSelectAll: MaterialButton
    private lateinit var btnCopyOpposite: MaterialButton
    private lateinit var btnMoveOpposite: MaterialButton
    private lateinit var btnDeleteSelection: MaterialButton
    private lateinit var btnMoreSelection: MaterialButton

    private var isDualPane = true
    private var activePanelIndex = 0 // 0 = Panel A, 1 = Panel B

    val activeFragment: FileListFragment
        get() = if (activePanelIndex == 0) fragmentA else fragmentB

    fun getOppositeFragment(fragment: FileListFragment): FileListFragment =
        if (fragment == fragmentA) fragmentB else fragmentA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.file_list_activity)

        mainToolbar = findViewById(R.id.mainToolbar)
        setSupportActionBar(mainToolbar)
        mainToolbar.setNavigationOnClickListener {
            activeFragment.openDrawer()
        }

        tvPathTitle = findViewById(R.id.tvPathTitle)
        tvStatsSubtitle = findViewById(R.id.tvStatsSubtitle)

        btnPanelA = findViewById(R.id.btnPanelA)
        btnPanelB = findViewById(R.id.btnPanelB)
        btnDual = findViewById(R.id.btnDual)
        btnSwap = findViewById(R.id.btnSwap)

        containerA = findViewById(R.id.containerPanelA)
        containerB = findViewById(R.id.containerPanelB)
        paneDivider = findViewById(R.id.paneDivider)

        bottomBarNormal = findViewById(R.id.bottomBarNormal)
        bottomBarSelection = findViewById(R.id.bottomBarSelection)

        btnBottomBack = findViewById(R.id.btnBottomBack)
        btnBottomSwap = findViewById(R.id.btnBottomSwap)
        btnBottomAdd = findViewById(R.id.btnBottomAdd)
        btnBottomSearch = findViewById(R.id.btnBottomSearch)
        btnBottomUp = findViewById(R.id.btnBottomUp)

        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnCopyOpposite = findViewById(R.id.btnCopyOpposite)
        btnMoveOpposite = findViewById(R.id.btnMoveOpposite)
        btnDeleteSelection = findViewById(R.id.btnDeleteSelection)
        btnMoreSelection = findViewById(R.id.btnMoreSelection)

        if (savedInstanceState == null) {
            fragmentA = FileListFragment().putArgs(FileListFragment.Args(intent))
            val intentB = Intent(intent)
            fragmentB = FileListFragment().putArgs(FileListFragment.Args(intentB))

            supportFragmentManager.commit {
                add(R.id.containerPanelA, fragmentA, TAG_PANEL_A)
                add(R.id.containerPanelB, fragmentB, TAG_PANEL_B)
            }
        } else {
            fragmentA = supportFragmentManager.findFragmentByTag(TAG_PANEL_A) as FileListFragment
            fragmentB = supportFragmentManager.findFragmentByTag(TAG_PANEL_B) as FileListFragment
            isDualPane = savedInstanceState.getBoolean(KEY_IS_DUAL_PANE, true)
            activePanelIndex = savedInstanceState.getInt(KEY_ACTIVE_PANEL, 0)
        }

        setupDualPaneControls()
        setupBottomBarControls()
        setupBackPressedHandler()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applyPaneVisibility()
        observePanelPathsAndStats()
    }

    private fun setupDualPaneControls() {
        btnPanelA.setOnClickListener {
            onPanelFocused(fragmentA)
            applyPaneVisibility()
        }

        btnPanelB.setOnClickListener {
            onPanelFocused(fragmentB)
            applyPaneVisibility()
        }

        btnDual.setOnClickListener {
            isDualPane = !isDualPane
            applyPaneVisibility()
        }

        btnSwap.setOnClickListener {
            try {
                val pathA = fragmentA.currentPath
                val pathB = fragmentB.currentPath
                fragmentA.navigateTo(pathB)
                fragmentB.navigateTo(pathA)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupBottomBarControls() {
        // Normal Mode Buttons
        btnBottomBack.setOnClickListener {
            if (activeFragment.canNavigateUp) {
                activeFragment.navigateUp()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        btnBottomSwap.setOnClickListener {
            btnSwap.performClick()
        }

        btnBottomAdd.setOnClickListener {
            showCreateDialog()
        }

        btnBottomSearch.setOnClickListener {
            showSearchDialog()
        }

        btnBottomUp.setOnClickListener {
            activeFragment.navigateUp()
        }

        // Selection Mode Buttons
        btnSelectAll.setOnClickListener {
            activeFragment.selectAllFiles()
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

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeFragment.isDrawerOpen()) {
                    activeFragment.closeDrawer()
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
            updateTabStyles()
            updateTopBarInfo()
            syncBottomBarSelectionState()
            fragmentA.updateBackCallbackState()
            fragmentB.updateBackCallbackState()
            invalidateOptionsMenu()
        }
    }

    private fun applyPaneVisibility() {
        if (isDualPane) {
            containerA.visibility = View.VISIBLE
            containerB.visibility = View.VISIBLE
            paneDivider.visibility = View.VISIBLE
            btnDual.text = "◫ Dual (A|B)"
            btnDual.alpha = 1.0f
        } else {
            paneDivider.visibility = View.GONE
            btnDual.text = "◫ Dividir"
            btnDual.alpha = 0.7f
            if (activePanelIndex == 0) {
                containerA.visibility = View.VISIBLE
                containerB.visibility = View.GONE
            } else {
                containerA.visibility = View.GONE
                containerB.visibility = View.VISIBLE
            }
        }
        updateTabStyles()
        updateTopBarInfo()
        syncBottomBarSelectionState()
    }

    private fun updateTabStyles() {
        val primaryColor = getColorByAttr(androidx.appcompat.R.attr.colorPrimary)
        val onPrimaryColor = getColorByAttr(com.google.android.material.R.attr.colorOnPrimary)
        val textSecondaryColor = getColorByAttr(android.R.attr.textColorSecondary)

        if (activePanelIndex == 0) {
            btnPanelA.backgroundTintList = ColorStateList.valueOf(primaryColor)
            btnPanelA.setTextColor(onPrimaryColor)
            btnPanelA.iconTint = ColorStateList.valueOf(onPrimaryColor)
            btnPanelA.alpha = 1.0f

            btnPanelB.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            btnPanelB.setTextColor(textSecondaryColor)
            btnPanelB.iconTint = ColorStateList.valueOf(textSecondaryColor)
            btnPanelB.alpha = 0.7f
        } else {
            btnPanelA.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            btnPanelA.setTextColor(textSecondaryColor)
            btnPanelA.iconTint = ColorStateList.valueOf(textSecondaryColor)
            btnPanelA.alpha = 0.7f

            btnPanelB.backgroundTintList = ColorStateList.valueOf(primaryColor)
            btnPanelB.setTextColor(onPrimaryColor)
            btnPanelB.iconTint = ColorStateList.valueOf(onPrimaryColor)
            btnPanelB.alpha = 1.0f
        }
    }

    private fun updateTopBarInfo() {
        if (!::fragmentA.isInitialized || !::fragmentB.isInitialized) return
        if (!activeFragment.isAdded) return
        try {
            val path = activeFragment.currentPath
            tvPathTitle.text = path.toString()
            val stats = activeFragment.currentStatsSubtitle
            tvStatsSubtitle.text = if (stats.isNotEmpty()) stats else "Listo"
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            val name = path.fileName?.toString() ?: "Raíz"
            btnPanelA.text = "A: $name"
            if (activePanelIndex == 0) {
                updateTopBarInfo()
            }
        }
        fragmentB.observeCurrentPath(this) { path ->
            val name = path.fileName?.toString() ?: "Raíz"
            btnPanelB.text = "B: $name"
            if (activePanelIndex == 1) {
                updateTopBarInfo()
            }
        }

        fragmentA.onPathOrStatsChanged = { path, stats ->
            if (activePanelIndex == 0) {
                tvPathTitle.text = path.toString()
                tvStatsSubtitle.text = stats
            }
        }
        fragmentB.onPathOrStatsChanged = { path, stats ->
            if (activePanelIndex == 1) {
                tvPathTitle.text = path.toString()
                tvStatsSubtitle.text = stats
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
        val items = arrayOf("Crear nueva carpeta", "Crear nuevo archivo")
        MaterialAlertDialogBuilder(this)
            .setTitle("Nuevo elemento")
            .setItems(items) { _, which ->
                if (which == 0) {
                    activeFragment.showCreateDirectoryDialog()
                } else {
                    activeFragment.showCreateFileDialog()
                }
            }
            .show()
    }

    private fun showSearchDialog() {
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
            add(0, 1, 0, "Desmarcar todo")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    activeFragment.clearSelectedFiles()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_DUAL_PANE, isDualPane)
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
        private const val KEY_IS_DUAL_PANE = "is_dual_pane"
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
