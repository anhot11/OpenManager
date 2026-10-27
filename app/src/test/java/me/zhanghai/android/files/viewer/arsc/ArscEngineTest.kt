/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.arsc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ArscEngineTest {

    private fun createMinimalArscTable(strings: List<String>): ByteArray {
        val headerSize = 12
        // Package Count = 0
        val packageCount = 0

        val stringPoolBytes = buildMockStringPool(strings)
        val totalSize = headerSize + stringPoolBytes.size

        val bb = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(ArscEngine.RES_TABLE_TYPE)
        bb.putShort(headerSize.toShort())
        bb.putInt(totalSize)
        bb.putInt(packageCount)
        bb.put(stringPoolBytes)

        return bb.array()
    }

    private fun buildMockStringPool(strings: List<String>): ByteArray {
        val stringCount = strings.size
        val poolHeaderSize = 28
        val offsetsSize = stringCount * 4
        val stringsStart = poolHeaderSize + offsetsSize

        val dataStream = java.io.ByteArrayOutputStream()
        val offsets = IntArray(stringCount)

        for (i in 0 until stringCount) {
            offsets[i] = dataStream.size()
            val utf8 = strings[i].toByteArray(Charsets.UTF_8)
            dataStream.write(strings[i].length) // charLen
            dataStream.write(utf8.size) // byteLen
            dataStream.write(utf8)
            dataStream.write(0) // null
        }

        val raw = dataStream.toByteArray()
        val pad = (4 - (raw.size % 4)) % 4
        val paddedSize = raw.size + pad
        val totalPoolSize = stringsStart + paddedSize

        val bb = ByteBuffer.allocate(totalPoolSize).order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(ArscEngine.RES_STRING_POOL_TYPE)
        bb.putShort(poolHeaderSize.toShort())
        bb.putInt(totalPoolSize)
        bb.putInt(stringCount)
        bb.putInt(0) // styleCount
        bb.putInt(ArscEngine.UTF8_FLAG)
        bb.putInt(stringsStart)
        bb.putInt(0) // stylesStart

        for (off in offsets) {
            bb.putInt(off)
        }
        bb.put(raw)
        for (p in 0 until pad) {
            bb.put(0.toByte())
        }

        return bb.array()
    }

    @Test
    fun testParseAndRebuildArsc() {
        val originalStrings = listOf("OpenManager", "app_name", "version_1.0", "settings")
        val arscBytes = createMinimalArscTable(originalStrings)

        val model = ArscEngine.parse(arscBytes)
        assertNotNull(model)
        assertEquals(4, model.strings.size)
        assertEquals("OpenManager", model.strings[0].value)
        assertEquals("app_name", model.strings[1].value)
        assertEquals("version_1.0", model.strings[2].value)
        assertEquals("settings", model.strings[3].value)

        // Modify strings
        val modified = listOf("OpenManager Pro", "app_name", "v2.0-mt", "settings")
        val rebuiltBytes = ArscEngine.rebuildWithModifiedStrings(arscBytes, modified)
        assertTrue(rebuiltBytes.size > 0)

        // Re-parse and verify
        val newModel = ArscEngine.parse(rebuiltBytes)
        assertEquals(4, newModel.strings.size)
        assertEquals("OpenManager Pro", newModel.strings[0].value)
        assertEquals("app_name", newModel.strings[1].value)
        assertEquals("v2.0-mt", newModel.strings[2].value)
        assertEquals("settings", newModel.strings[3].value)
    }
}
