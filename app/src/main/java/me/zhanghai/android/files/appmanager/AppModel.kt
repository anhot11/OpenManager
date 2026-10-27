/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.appmanager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
    val lastUpdateTime: Long
) {
    val isSplit: Boolean get() = splitSourceDirs.isNotEmpty()
    val splitCount: Int get() = if (splitSourceDirs.isNotEmpty()) splitSourceDirs.size + 1 else 1

    fun toJson(): JSONObject = JSONObject().apply {
        put("label", label)
        put("pkg", packageName)
        put("verName", versionName)
        put("verCode", versionCode)
        put("srcDir", sourceDir)
        put("splits", JSONArray(splitSourceDirs))
        put("dataDir", dataDir)
        put("targetSdk", targetSdkVersion)
        put("minSdk", minSdkVersion)
        put("size", sizeBytes)
        put("isSys", isSystem)
        put("isEnabled", isEnabled)
        put("firstInstall", firstInstallTime)
        put("lastUpdate", lastUpdateTime)
    }

    companion object {
        fun fromJson(json: JSONObject): AppModel {
            val splitsArray = json.optJSONArray("splits")
            val splits = mutableListOf<String>()
            if (splitsArray != null) {
                for (i in 0 until splitsArray.length()) {
                    splits.add(splitsArray.getString(i))
                }
            }
            return AppModel(
                label = json.optString("label"),
                packageName = json.optString("pkg"),
                versionName = json.optString("verName", "1.0"),
                versionCode = json.optLong("verCode", 1L),
                sourceDir = json.optString("srcDir"),
                splitSourceDirs = splits,
                dataDir = json.optString("dataDir"),
                targetSdkVersion = json.optInt("targetSdk", 0),
                minSdkVersion = json.optInt("minSdk", 0),
                sizeBytes = json.optLong("size", 0L),
                isSystem = json.optBoolean("isSys", false),
                isEnabled = json.optBoolean("isEnabled", true),
                firstInstallTime = json.optLong("firstInstall", 0L),
                lastUpdateTime = json.optLong("lastUpdate", 0L)
            )
        }
    }
}

object AppDiskCache {
    private const val FILE_NAME = "installed_apps_v2.json"

    fun load(context: Context): List<AppModel>? {
        return try {
            val file = File(context.cacheDir, FILE_NAME)
            if (!file.exists() || file.length() == 0L) return null
            val text = file.readText()
            val array = JSONArray(text)
            val list = ArrayList<AppModel>(array.length())
            for (i in 0 until array.length()) {
                list.add(AppModel.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            null
        }
    }

    fun save(context: Context, apps: List<AppModel>) {
        try {
            val array = JSONArray()
            for (app in apps) {
                array.put(app.toJson())
            }
            val file = File(context.cacheDir, FILE_NAME)
            file.writeText(array.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
