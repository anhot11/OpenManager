/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.Context
import androidx.collection.LruCache

object AppMetadataCache {
    private val labelCache = LruCache<String, String>(500)
    private val installedCache = LruCache<String, Boolean>(500)

    @Synchronized
    fun getAppLabel(context: Context, packageName: String): String? {
        if (packageName.isEmpty()) return null
        val cached = labelCache.get(packageName)
        if (cached != null) return if (cached.isEmpty()) null else cached
        return try {
            val pm = context.applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(info).toString()
            labelCache.put(packageName, label)
            installedCache.put(packageName, true)
            label
        } catch (e: Exception) {
            labelCache.put(packageName, "")
            installedCache.put(packageName, false)
            null
        }
    }

    @Synchronized
    fun isInstalled(context: Context, packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        val cached = installedCache.get(packageName)
        if (cached != null) return cached
        return try {
            context.applicationContext.packageManager.getPackageInfo(packageName, 0)
            installedCache.put(packageName, true)
            true
        } catch (e: Exception) {
            installedCache.put(packageName, false)
            false
        }
    }

    @Synchronized
    fun clear() {
        labelCache.evictAll()
        installedCache.evictAll()
    }
}
