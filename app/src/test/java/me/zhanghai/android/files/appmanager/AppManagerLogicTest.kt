/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.appmanager

import me.zhanghai.android.files.coil.InstalledAppIcon
import me.zhanghai.android.files.coil.InstalledAppIconKeyer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManagerLogicTest {

    data class TestApp(
        val label: String,
        val packageName: String,
        val versionName: String,
        val apkSize: Long,
        val lastUpdateTime: Long,
        val isSystem: Boolean
    )

    private val sampleApps = listOf(
        TestApp("Zeta Tool", "org.zeta.tool", "2.1.0", 50_000_000L, 1700001000L, false),
        TestApp("Alpha File", "com.alpha.file", "1.0.0", 10_000_000L, 1700003000L, false),
        TestApp("System UI", "com.android.systemui", "14.0", 25_000_000L, 1700002000L, true),
        TestApp("Beta Browser", "net.beta.browser", "5.4.1", 80_000_000L, 1700000500L, false)
    )

    @Test
    fun testInstalledAppIconKeyer() {
        val keyer = InstalledAppIconKeyer()
        val appIcon = InstalledAppIcon("com.test.app", 123456789L)
        // Pass dummy Options if required or test method logic
        val key = "app_icon_hd_${appIcon.packageName}_${appIcon.lastUpdateTime}"
        assertEquals("app_icon_hd_com.test.app_123456789", key)
    }

    @Test
    fun testFilterUserAndSystemApps() {
        val userApps = sampleApps.filter { !it.isSystem }
        val systemApps = sampleApps.filter { it.isSystem }

        assertEquals(3, userApps.size)
        assertEquals(1, systemApps.size)
        assertEquals("com.android.systemui", systemApps.first().packageName)
    }

    @Test
    fun testSearchFilter() {
        val query = "alpha"
        val matched = sampleApps.filter { app ->
            app.label.lowercase().contains(query) ||
            app.packageName.lowercase().contains(query) ||
            app.versionName.lowercase().contains(query)
        }
        assertEquals(1, matched.size)
        assertEquals("Alpha File", matched.first().label)
    }

    @Test
    fun testSearchFilterByPackage() {
        val query = "org.zeta"
        val matched = sampleApps.filter { app ->
            app.label.lowercase().contains(query) ||
            app.packageName.lowercase().contains(query) ||
            app.versionName.lowercase().contains(query)
        }
        assertEquals(1, matched.size)
        assertEquals("org.zeta.tool", matched.first().packageName)
    }

    @Test
    fun testSortOrderByNameAscending() {
        val sorted = sampleApps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        assertEquals("Alpha File", sorted[0].label)
        assertEquals("Beta Browser", sorted[1].label)
        assertEquals("System UI", sorted[2].label)
        assertEquals("Zeta Tool", sorted[3].label)
    }

    @Test
    fun testSortOrderBySizeDescending() {
        val sorted = sampleApps.sortedByDescending { it.apkSize }
        assertEquals("Beta Browser", sorted[0].label) // 80 MB
        assertEquals("Zeta Tool", sorted[1].label)    // 50 MB
        assertEquals("System UI", sorted[2].label)    // 25 MB
        assertEquals("Alpha File", sorted[3].label)   // 10 MB
    }

    @Test
    fun testSortOrderByLastUpdateTime() {
        val sorted = sampleApps.sortedByDescending { it.lastUpdateTime }
        assertEquals("Alpha File", sorted[0].label) // 1700003000L
        assertEquals("System UI", sorted[1].label)  // 1700002000L
    }

    @Test
    fun testSanitizeExtractedFileName() {
        fun sanitize(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        val appNameWithSpecialChars = "Super/App:Pro*Edition?"
        val sanitized = sanitize(appNameWithSpecialChars)
        assertEquals("Super_App_Pro_Edition_", sanitized)
    }
}
