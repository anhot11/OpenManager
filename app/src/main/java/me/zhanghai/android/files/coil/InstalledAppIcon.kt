/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.coil

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import coil.key.Keyer
import coil.request.Options
import me.zhanghai.android.files.compat.PackageManagerCompat
import java.io.Closeable

data class InstalledAppIcon(val packageName: String, val lastUpdateTime: Long = 0)

class InstalledAppIconKeyer : Keyer<InstalledAppIcon> {
    override fun key(data: InstalledAppIcon, options: Options): String =
        "app_icon_hd_${data.packageName}_${data.lastUpdateTime}"
}

class InstalledAppIconFetcherFactory(
    private val context: Context
) : AppIconFetcher.Factory<InstalledAppIcon>(
    // Full resolution launcher icon size (e.g. 192px on xxxhdpi, 144px on xxhdpi, 96px on xhdpi)
    ((context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.launcherLargeIconSize
        ?: (64 * context.resources.displayMetrics.density).toInt()).coerceAtLeast(144),
    context,
    false
) {
    override fun getApplicationInfo(data: InstalledAppIcon): Pair<ApplicationInfo, Closeable?> {
        return context.packageManager.getApplicationInfo(
            data.packageName, PackageManagerCompat.MATCH_UNINSTALLED_PACKAGES
        ) to null
    }
}
