/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.hex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HexBufferTest {

    @Test
    fun testEmptyBufferBounds() {
        val buffer = HexBuffer(ByteArray(0))
        assertEquals(0, buffer.size)
        assertEquals(-1, buffer.searchHex(byteArrayOf(0x01, 0x02), 0, forward = true))
        assertEquals(-1, buffer.searchHex(byteArrayOf(0x01, 0x02), 0, forward = false))
        assertEquals(-1, buffer.searchText("test", 0, forward = true, ignoreCase = false))
        assertEquals(-1, buffer.searchText("test", 0, forward = false, ignoreCase = true))
        assertEquals(0, buffer.replaceAllHex(byteArrayOf(0x01), byteArrayOf(0x02)))
    }

    @Test
    fun testPatternLargerThanBuffer() {
        val data = byteArrayOf(0x41, 0x42) // "AB"
        val buffer = HexBuffer(data)
        val pattern = byteArrayOf(0x41, 0x42, 0x43, 0x44) // "ABCD"
        assertEquals(-1, buffer.searchHex(pattern, 0, forward = true))
        assertEquals(-1, buffer.searchHex(pattern, 0, forward = false))
        assertEquals(-1, buffer.searchText("ABCDEF", 0, forward = true, ignoreCase = true))
        assertEquals(-1, buffer.searchText("ABCDEF", 0, forward = false, ignoreCase = true))
    }

    @Test
    fun testSearchHexForwardAndBackward() {
        // "Hello World Hello"
        val text = "Hello World Hello"
        val buffer = HexBuffer(text.toByteArray())

        val pattern = "Hello".toByteArray()
        val first = buffer.searchHex(pattern, 0, forward = true)
        assertEquals(0, first)

        val second = buffer.searchHex(pattern, 5, forward = true)
        assertEquals(12, second)

        val backward = buffer.searchHex(pattern, 16, forward = false)
        assertEquals(12, backward)

        val backwardStart = buffer.searchHex(pattern, 10, forward = false)
        assertEquals(0, backwardStart)
    }

    @Test
    fun testSearchTextCaseInsensitive() {
        val text = "OpenManager MT Suite"
        val buffer = HexBuffer(text.toByteArray())

        val pos = buffer.searchText("mt suite", 0, forward = true, ignoreCase = true)
        assertEquals(12, pos)

        val notFound = buffer.searchText("notfound", 0, forward = true, ignoreCase = true)
        assertEquals(-1, notFound)
    }

    @Test
    fun testModifyAndUndoRedo() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val buffer = HexBuffer(data)

        assertFalse(buffer.isModified)
        assertFalse(buffer.canUndo)

        buffer.modifyByte(2, 99.toByte())
        assertTrue(buffer.isModified)
        assertTrue(buffer.canUndo)
        assertEquals(99.toByte(), buffer.toByteArray()[2])

        val restored = buffer.undo()
        assertEquals(2, restored)
        assertEquals(3.toByte(), buffer.toByteArray()[2])
        assertFalse(buffer.isModified)
        assertTrue(buffer.canRedo)

        buffer.redo()
        assertEquals(99.toByte(), buffer.toByteArray()[2])
        assertTrue(buffer.isModified)
    }

    @Test
    fun testReplaceAllHex() {
        val data = "foo bar foo baz foo".toByteArray()
        val buffer = HexBuffer(data)

        val count = buffer.replaceAllHex("foo".toByteArray(), "OM".toByteArray())
        assertEquals(3, count)
        val result = String(buffer.toByteArray())
        assertEquals("OM bar OM baz OM", result)
    }
}
