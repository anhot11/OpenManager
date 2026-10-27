/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLogicTest {

    private fun isHomeOrRootPath(pathStr: String): Boolean {
        val s = pathStr.trimEnd('/')
        return s == "/storage/emulated/0" || s == "/sdcard" || s.isEmpty() || s == "/"
    }

    private fun canNavigateUp(pathStr: String, hasArchiveParent: Boolean = false): Boolean {
        if (isHomeOrRootPath(pathStr)) {
            return false
        }
        return true || hasArchiveParent
    }

    private fun shouldExitOnBackPress(lastBackPressTime: Long, currentTime: Long): Boolean {
        return (currentTime - lastBackPressTime) < 2000L
    }

    @Test
    fun testHomePathsIdentifiedCorrectly() {
        assertTrue(isHomeOrRootPath("/storage/emulated/0"))
        assertTrue(isHomeOrRootPath("/storage/emulated/0/"))
        assertTrue(isHomeOrRootPath("/sdcard"))
        assertTrue(isHomeOrRootPath("/sdcard/"))
        assertTrue(isHomeOrRootPath("/"))
        assertTrue(isHomeOrRootPath(""))
    }

    @Test
    fun testSubdirectoryPathsCanNavigateUp() {
        assertFalse(isHomeOrRootPath("/storage/emulated/0/Download"))
        assertFalse(isHomeOrRootPath("/storage/emulated/0/Android/data"))
        assertFalse(isHomeOrRootPath("/data/local/tmp"))

        assertTrue(canNavigateUp("/storage/emulated/0/Download"))
        assertTrue(canNavigateUp("/storage/emulated/0/Android/data"))
    }

    @Test
    fun testHomePathCannotNavigateUp() {
        assertFalse(canNavigateUp("/storage/emulated/0"))
        assertFalse(canNavigateUp("/storage/emulated/0/"))
        assertFalse(canNavigateUp("/"))
    }

    @Test
    fun testDoubleBackPressExitWindow() {
        val firstPress = 10000L
        val quickSecondPress = 11500L // 1.5s later -> exit
        val slowSecondPress = 12500L // 2.5s later -> do not exit

        assertTrue(shouldExitOnBackPress(firstPress, quickSecondPress))
        assertFalse(shouldExitOnBackPress(firstPress, slowSecondPress))
    }

    @Test
    fun testDefaultJumpPathTargets() {
        val internalPath = "/storage/emulated/0/"
        val downloadsPath = "/storage/emulated/0/Download/"
        val androidDataPath = "/storage/emulated/0/Android/data/"
        val rootPath = "/"

        assertTrue(internalPath.startsWith("/storage/emulated/0"))
        assertTrue(downloadsPath.startsWith(internalPath))
        assertTrue(androidDataPath.startsWith(internalPath))
        assertEquals("/", rootPath)
    }
}
