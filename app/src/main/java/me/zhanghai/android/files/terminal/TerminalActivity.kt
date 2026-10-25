/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.appbar.MaterialToolbar
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import java.io.File

class TerminalActivity : AppActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var terminalView: TerminalView
    private lateinit var extraKeysView: TerminalExtraKeysView

    private var terminalSession: TerminalSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentPid: Int = -1

    companion object {
        const val EXTRA_PATH = "me.zhanghai.android.files.terminal.extra.PATH"
        const val EXTRA_CWD = "me.zhanghai.android.files.terminal.extra.CWD"
        const val EXTRA_COMMAND = "me.zhanghai.android.files.terminal.extra.COMMAND"
        const val EXTRA_FORCE_ROOT = "me.zhanghai.android.files.terminal.extra.FORCE_ROOT"
        const val EXTRA_FORCE_TERMEX = "me.zhanghai.android.files.terminal.extra.FORCE_TERMEX"

        fun createIntent(
            context: Context,
            path: String? = null,
            command: String? = null,
            forceRoot: Boolean? = null,
            forceTermex: Boolean? = null
        ): Intent {
            return Intent(context, TerminalActivity::class.java).apply {
                if (path != null) {
                    putExtra(EXTRA_PATH, path)
                    putExtra(EXTRA_CWD, path)
                }
                if (command != null) {
                    putExtra(EXTRA_COMMAND, command)
                }
                if (forceRoot != null) {
                    putExtra(EXTRA_FORCE_ROOT, forceRoot)
                }
                if (forceTermex != null) {
                    putExtra(EXTRA_FORCE_TERMEX, forceTermex)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        toolbar = findViewById(R.id.toolbar)
        terminalView = findViewById(R.id.terminalView)
        extraKeysView = findViewById(R.id.extraKeysView)

        setupToolbar()
        setupExtraKeys()
        applyPreferences()
        startTerminalSession()
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupExtraKeys() {
        extraKeysView.onKeyString = { keyStr ->
            if (terminalSession?.isRunning == false) {
                if (keyStr == "\r" || keyStr == "\n") {
                    finish()
                }
            } else {
                terminalSession?.write(keyStr)
            }
        }
        extraKeysView.onOverflowMenuClick = { anchor ->
            showOverflowMenu(anchor)
        }
    }

    private fun applyPreferences() {
        val fontSize = TerminalSettings.getFontSize(this)
        terminalView.setTextSize(fontSize)
        terminalView.setTypeface(Typeface.MONOSPACE)

        if (TerminalSettings.isKeepBlackTheme(this)) {
            terminalView.setBackgroundColor(Color.BLACK)
            window.statusBarColor = Color.BLACK
            window.navigationBarColor = Color.BLACK
        }
    }

    private fun startTerminalSession() {
        val overridePath = intent.getStringExtra(EXTRA_PATH)
            ?: intent.getStringExtra(EXTRA_CWD)
            ?: intent.data?.path

        val initialCommand = intent.getStringExtra(EXTRA_COMMAND)
        val forceRoot = if (intent.hasExtra(EXTRA_FORCE_ROOT)) intent.getBooleanExtra(EXTRA_FORCE_ROOT, false) else null
        val forceTermex = if (intent.hasExtra(EXTRA_FORCE_TERMEX)) intent.getBooleanExtra(EXTRA_FORCE_TERMEX, false) else null

        val config = TerminalExpansionManager.prepareLaunchConfig(
            this,
            forceRoot = forceRoot,
            forceTermex = forceTermex,
            overrideCwd = overridePath,
            initialCommand = initialCommand
        )

        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                terminalView.onScreenUpdated()
            }

            override fun onTitleChanged(changedSession: TerminalSession) {
                runOnUiThread {
                    val title = changedSession.title
                    if (!title.isNullOrEmpty()) {
                        toolbar.subtitle = title
                    }
                }
            }

            override fun onSessionFinished(finishedSession: TerminalSession) {
                runOnUiThread {
                    toolbar.subtitle = "[Terminado (código ${finishedSession.exitStatus})]"
                }
            }

            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("terminal", text))
                Toast.makeText(this@TerminalActivity, R.string.terminal_copy, Toast.LENGTH_SHORT).show()
            }

            override fun onPasteTextFromClipboard(session: TerminalSession?) {
                pasteFromClipboard()
            }

            override fun onBell(session: TerminalSession) {}

            override fun onColorsChanged(session: TerminalSession) {
                terminalView.onScreenUpdated()
            }

            override fun onTerminalCursorStateChange(state: Boolean) {}

            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
                currentPid = pid
            }

            override fun getTerminalCursorStyle(): Int {
                return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
            }

            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }

        val session = TerminalSession(
            config.executable,
            config.cwd,
            config.args,
            config.env,
            5000,
            sessionClient
        )
        terminalSession = session

        val viewClient = object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                if (scale < 0.9f || scale > 1.1f) {
                    val currentSize = TerminalSettings.getFontSize(this@TerminalActivity)
                    val newSize = if (scale > 1.0f) currentSize + 1 else currentSize - 1
                    val clamped = newSize.coerceIn(8, 32)
                    if (clamped != currentSize) {
                        TerminalSettings.setFontSize(this@TerminalActivity, clamped)
                        terminalView.setTextSize(clamped)
                    }
                }
                return 1.0f
            }

            override fun onSingleTapUp(e: MotionEvent?) {
                terminalView.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
            }

            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            override fun shouldEnforceCharBasedInput(): Boolean = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = true

            override fun copyModeChanged(copyMode: Boolean) {}

            override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
                if (session != null && !session.isRunning) {
                    if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        finish()
                        return true
                    }
                }
                return false
            }

            override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
            override fun onLongPress(event: MotionEvent?): Boolean = false

            override fun readControlKey(): Boolean {
                return extraKeysView.consumeCtrl()
            }

            override fun readAltKey(): Boolean {
                return extraKeysView.consumeAlt()
            }

            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false

            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
                return false
            }

            override fun onEmulatorSet() {}

            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }

        terminalView.setTerminalViewClient(viewClient)
        terminalView.attachSession(session)

        // Show start info in subtitle
        val mode = if (config.isRoot) "Root (#)" else if (config.isTermex) "Termex (bash)" else "Sistema (sh)"
        if (initialCommand != null) {
            val cmdClean = initialCommand.trim().removeSurrounding("(", ")").removeSurrounding("\"")
            val fileName = File(cmdClean).name
            toolbar.title = fileName
            toolbar.subtitle = "$mode • Ejecutando..."
        } else {
            toolbar.subtitle = "$mode • ${File(config.cwd).name}"
        }
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.apply {
            val wakeTitle = if (wakeLock?.isHeld == true) {
                "${getString(R.string.terminal_wakelock)} (✓)"
            } else {
                getString(R.string.terminal_wakelock)
            }
            add(Menu.NONE, 1, 1, wakeTitle)
            add(Menu.NONE, 2, 2, R.string.terminal_close)
            add(Menu.NONE, 3, 3, R.string.terminal_reset)
            add(Menu.NONE, 4, 4, R.string.terminal_preferences)
            add(Menu.NONE, 5, 5, R.string.terminal_paste)
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    toggleWakeLock()
                    true
                }
                2 -> {
                    finish()
                    true
                }
                3 -> {
                    terminalSession?.reset()
                    terminalView.onScreenUpdated()
                    Toast.makeText(this, R.string.terminal_reset, Toast.LENGTH_SHORT).show()
                    true
                }
                4 -> {
                    val dialog = TerminalPreferencesDialog()
                    dialog.onSettingsChanged = {
                        applyPreferences()
                    }
                    dialog.show(supportFragmentManager, "TerminalPreferences")
                    true
                }
                5 -> {
                    pasteFromClipboard()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun toggleWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenManager:TerminalWakeLock")
        }

        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
                Toast.makeText(this, R.string.terminal_wakelock_released, Toast.LENGTH_SHORT).show()
            } else {
                wl.acquire(60 * 60 * 1000L) // 1 hour max
                Toast.makeText(this, R.string.terminal_wakelock_acquired, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pasteFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = cm.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            if (!text.isNullOrEmpty()) {
                terminalSession?.write(text)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (terminalSession?.isRunning == false && event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                finish()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        terminalSession?.finishIfRunning()
    }
}
