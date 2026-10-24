/*
 * OpenManager - Dual-Pane File Explorer (MT Manager Style)
 * Supports side-by-side Panel A & Panel B navigation with instant synchronization.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.GravityCompat
import androidx.fragment.app.commit
import com.google.android.material.button.MaterialButton
import java8.nio.file.Path
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.util.createIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs

class FileListActivity : AppActivity() {

    private lateinit var fragmentA: FileListFragment
    private lateinit var fragmentB: FileListFragment

    private lateinit var btnPanelA: MaterialButton
    private lateinit var btnPanelB: MaterialButton
    private lateinit var btnDual: MaterialButton
    private lateinit var btnSwap: MaterialButton

    private lateinit var containerA: FrameLayout
    private lateinit var containerB: FrameLayout
    private lateinit var paneDivider: View

    private var isDualPane = true
    private var activePanelIndex = 0 // 0 = Panel A, 1 = Panel B

    val activeFragment: FileListFragment
        get() = if (activePanelIndex == 0) fragmentA else fragmentB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.file_list_activity)

        btnPanelA = findViewById(R.id.btnPanelA)
        btnPanelB = findViewById(R.id.btnPanelB)
        btnDual = findViewById(R.id.btnDual)
        btnSwap = findViewById(R.id.btnSwap)

        containerA = findViewById(R.id.containerPanelA)
        containerB = findViewById(R.id.containerPanelB)
        paneDivider = findViewById(R.id.paneDivider)

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
        applyPaneVisibility()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        observePanelPaths()
    }

    private fun setupDualPaneControls() {
        btnPanelA.setOnClickListener {
            activePanelIndex = 0
            fragmentA.updateBackCallbackState()
            fragmentB.updateBackCallbackState()
            if (!isDualPane) {
                applyPaneVisibility()
            } else {
                updateTabStyles()
            }
        }

        btnPanelB.setOnClickListener {
            activePanelIndex = 1
            fragmentA.updateBackCallbackState()
            fragmentB.updateBackCallbackState()
            if (!isDualPane) {
                applyPaneVisibility()
            } else {
                updateTabStyles()
            }
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

    fun onPanelFocused(fragment: FileListFragment) {
        val newIndex = if (fragment == fragmentA) 0 else 1
        if (activePanelIndex != newIndex) {
            activePanelIndex = newIndex
            updateTabStyles()
            fragmentA.updateBackCallbackState()
            fragmentB.updateBackCallbackState()
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
    }

    private fun updateTabStyles() {
        if (activePanelIndex == 0) {
            btnPanelA.alpha = 1.0f
            btnPanelB.alpha = 0.6f
        } else {
            btnPanelA.alpha = 0.6f
            btnPanelB.alpha = 1.0f
        }
    }

    private fun observePanelPaths() {
        fragmentA.observeCurrentPath(this) { path ->
            val name = path.fileName?.toString() ?: "Raíz"
            btnPanelA.text = "A: $name"
        }
        fragmentB.observeCurrentPath(this) { path ->
            val name = path.fileName?.toString() ?: "Raíz"
            btnPanelB.text = "B: $name"
        }
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
