/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryStorageLogicTest {

    @Test
    fun testSecondaryStorageVolumePath() {
        val uuid = "1234-5678"
        val storage = SecondaryStorageVolume(uuid, null, true)
        assertEquals("/storage/1234-5678", storage.linuxPath)
        assertEquals("/storage/1234-5678", storage.description)
        assertNotNull(storage.path)
    }

    @Test
    fun testSecondaryStorageVolumeIdUniqueness() {
        val storage1 = SecondaryStorageVolume("1234-5678", null, true)
        val storage2 = SecondaryStorageVolume("8765-4321", null, true)
        val storage3 = SecondaryStorageVolume("1234-5678", "Custom", false)

        assertNotEquals(storage1.id, storage2.id)
        assertEquals(storage1.id, storage3.id)
    }

    @Test
    fun testSecondaryStorageCopy() {
        val original = SecondaryStorageVolume("9C33-6BBD", null, true)
        val renamed = original.copy_(customName = "My MicroSD")
        assertEquals("My MicroSD", renamed.customName)
        assertTrue(renamed.isVisible)

        val hidden = original.copy_(isVisible = false)
        assertFalse(hidden.isVisible)
    }

    @Test
    fun testStorageFilterForRemovableVolumes() {
        val existingPaths = setOf("/storage/emulated/0", "/")
        val detectedPath = "/storage/1234-5678"

        assertTrue(detectedPath !in existingPaths)

        val updatedPaths = existingPaths + detectedPath
        assertTrue(detectedPath in updatedPaths)
    }
}
