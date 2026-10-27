/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.appmanager

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.collection.LruCache

object NativeAppIconLoader {
    private val memoryCache = LruCache<String, Drawable>(300)

    fun getCached(packageName: String): Drawable? = memoryCache.get(packageName)

    fun put(packageName: String, drawable: Drawable) {
        memoryCache.put(packageName, drawable)
    }

    fun loadIcon(context: Context, packageName: String): Drawable? {
        memoryCache.get(packageName)?.let { return it }
        val pm = context.packageManager
        val drawable = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationIcon(appInfo)
        } catch (e: Exception) {
            try {
                pm.getApplicationIcon(packageName)
            } catch (e2: Exception) {
                null
            }
        }
        if (drawable != null) {
            memoryCache.put(packageName, drawable)
        }
        return drawable
    }

    fun clear() {
        memoryCache.evictAll()
    }
}
