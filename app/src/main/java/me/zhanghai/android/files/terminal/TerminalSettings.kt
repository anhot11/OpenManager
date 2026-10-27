/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.terminal

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import androidx.preference.PreferenceManager

object TerminalSettings {
    private const val PREF_START_PATH = "terminal_start_path"
    private const val PREF_FONT_SIZE = "terminal_font_size"
    private const val PREF_KEEP_BLACK_THEME = "terminal_keep_black_theme"
    private const val PREF_ROOT_SESSION = "terminal_root_session"

    @Suppress("DEPRECATION")
    private val DEFAULT_START_PATH: String
        get() = try {
            Environment.getExternalStorageDirectory().absolutePath
        } catch (e: Exception) {
            "/storage/emulated/0"
        }

    fun getStartPath(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getString(PREF_START_PATH, null) ?: DEFAULT_START_PATH
    }

    fun setStartPath(context: Context, path: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(PREF_START_PATH, path)
        }
    }

    fun getFontSize(context: Context): Int {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val size = sp.getInt(PREF_FONT_SIZE, 14)
        return if (size < 13) 14 else size
    }

    fun setFontSize(context: Context, size: Int) {
        val clamped = size.coerceIn(10, 32)
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putInt(PREF_FONT_SIZE, clamped)
        }
    }

    fun isKeepBlackTheme(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_KEEP_BLACK_THEME, true)
    }

    fun setKeepBlackTheme(context: Context, keepBlack: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(PREF_KEEP_BLACK_THEME, keepBlack)
        }
    }

    fun isRootSession(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return sp.getBoolean(PREF_ROOT_SESSION, false)
    }

    fun setRootSession(context: Context, root: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putBoolean(PREF_ROOT_SESSION, root)
        }
    }
}
