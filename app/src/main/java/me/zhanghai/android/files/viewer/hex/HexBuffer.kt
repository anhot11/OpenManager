/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.hex

import java.nio.charset.Charset

class HexBuffer(initialData: ByteArray) {

    private var data: ByteArray = initialData
    val size: Int get() = data.size

    val modifiedIndices = mutableSetOf<Int>()

    sealed class Action {
        data class Replace(val offset: Int, val oldBytes: ByteArray, val newBytes: ByteArray) : Action()
        data class Insert(val offset: Int, val bytes: ByteArray) : Action()
        data class Delete(val offset: Int, val oldBytes: ByteArray) : Action()
    }

    private val undoStack = mutableListOf<Action>()
    private val redoStack = mutableListOf<Action>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isModified: Boolean get() = undoStack.isNotEmpty() || modifiedIndices.isNotEmpty()

    fun getByte(offset: Int): Byte {
        if (offset < 0 || offset >= data.size) return 0
        return data[offset]
    }

    fun getBytes(offset: Int, length: Int): ByteArray {
        if (offset < 0 || offset >= data.size || length <= 0) return ByteArray(0)
        val actualLen = minOf(length, data.size - offset)
        return data.copyOfRange(offset, offset + actualLen)
    }

    fun modifyBytes(offset: Int, newBytes: ByteArray) {
        if (offset < 0 || offset >= data.size || newBytes.isEmpty()) return
        val count = minOf(newBytes.size, data.size - offset)
        val oldBytes = data.copyOfRange(offset, offset + count)
        val appliedNewBytes = if (count == newBytes.size) newBytes else newBytes.copyOfRange(0, count)

        System.arraycopy(appliedNewBytes, 0, data, offset, count)
        for (i in 0 until count) {
            modifiedIndices.add(offset + i)
        }
        undoStack.add(Action.Replace(offset, oldBytes, appliedNewBytes))
        redoStack.clear()
    }

    fun insertBytes(offset: Int, bytesToInsert: ByteArray) {
        if (offset < 0 || offset > data.size || bytesToInsert.isEmpty()) return
        val newArr = ByteArray(data.size + bytesToInsert.size)
        System.arraycopy(data, 0, newArr, 0, offset)
        System.arraycopy(bytesToInsert, 0, newArr, offset, bytesToInsert.size)
        System.arraycopy(data, offset, newArr, offset + bytesToInsert.size, data.size - offset)
        data = newArr

        val shifted = mutableSetOf<Int>()
        for (idx in modifiedIndices) {
            if (idx >= offset) {
                shifted.add(idx + bytesToInsert.size)
            } else {
                shifted.add(idx)
            }
        }
        for (i in bytesToInsert.indices) {
            shifted.add(offset + i)
        }
        modifiedIndices.clear()
        modifiedIndices.addAll(shifted)

        undoStack.add(Action.Insert(offset, bytesToInsert))
        redoStack.clear()
    }

    fun deleteBytes(offset: Int, count: Int) {
        if (offset < 0 || offset >= data.size || count <= 0) return
        val actualCount = minOf(count, data.size - offset)
        val deleted = data.copyOfRange(offset, offset + actualCount)
        val newArr = ByteArray(data.size - actualCount)
        System.arraycopy(data, 0, newArr, 0, offset)
        System.arraycopy(data, offset + actualCount, newArr, offset, data.size - (offset + actualCount))
        data = newArr

        val shifted = mutableSetOf<Int>()
        for (idx in modifiedIndices) {
            if (idx in offset until (offset + actualCount)) {
                // deleted
            } else if (idx >= offset + actualCount) {
                shifted.add(idx - actualCount)
            } else {
                shifted.add(idx)
            }
        }
        modifiedIndices.clear()
        modifiedIndices.addAll(shifted)

        undoStack.add(Action.Delete(offset, deleted))
        redoStack.clear()
    }

    fun fillRange(startOffset: Int, endOffset: Int, fillByte: Byte) {
        if (startOffset < 0 || startOffset >= data.size) return
        val end = endOffset.coerceIn(startOffset, data.size - 1)
        val count = end - startOffset + 1
        val fillArr = ByteArray(count) { fillByte }
        modifyBytes(startOffset, fillArr)
    }

    fun undo(): Int {
        if (undoStack.isEmpty()) return -1
        val action = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(action)
        return when (action) {
            is Action.Replace -> {
                System.arraycopy(action.oldBytes, 0, data, action.offset, action.oldBytes.size)
                rebuildModifiedIndices()
                action.offset
            }
            is Action.Insert -> {
                val newArr = ByteArray(data.size - action.bytes.size)
                System.arraycopy(data, 0, newArr, 0, action.offset)
                System.arraycopy(data, action.offset + action.bytes.size, newArr, action.offset, data.size - (action.offset + action.bytes.size))
                data = newArr
                rebuildModifiedIndices()
                action.offset
            }
            is Action.Delete -> {
                val newArr = ByteArray(data.size + action.oldBytes.size)
                System.arraycopy(data, 0, newArr, 0, action.offset)
                System.arraycopy(action.oldBytes, 0, newArr, action.offset, action.oldBytes.size)
                System.arraycopy(data, action.offset, newArr, action.offset + action.oldBytes.size, data.size - action.offset)
                data = newArr
                rebuildModifiedIndices()
                action.offset
            }
        }
    }

    fun redo(): Int {
        if (redoStack.isEmpty()) return -1
        val action = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(action)
        return when (action) {
            is Action.Replace -> {
                System.arraycopy(action.newBytes, 0, data, action.offset, action.newBytes.size)
                rebuildModifiedIndices()
                action.offset
            }
            is Action.Insert -> {
                val newArr = ByteArray(data.size + action.bytes.size)
                System.arraycopy(data, 0, newArr, 0, action.offset)
                System.arraycopy(action.bytes, 0, newArr, action.offset, action.bytes.size)
                System.arraycopy(data, action.offset, newArr, action.offset + action.bytes.size, data.size - action.offset)
                data = newArr
                rebuildModifiedIndices()
                action.offset
            }
            is Action.Delete -> {
                val newArr = ByteArray(data.size - action.oldBytes.size)
                System.arraycopy(data, 0, newArr, 0, action.offset)
                System.arraycopy(data, action.offset + action.oldBytes.size, newArr, action.offset, data.size - (action.offset + action.oldBytes.size))
                data = newArr
                rebuildModifiedIndices()
                action.offset
            }
        }
    }

    private fun rebuildModifiedIndices() {
        modifiedIndices.clear()
        for (act in undoStack) {
            when (act) {
                is Action.Replace -> {
                    for (i in act.newBytes.indices) {
                        modifiedIndices.add(act.offset + i)
                    }
                }
                is Action.Insert -> {
                    for (i in act.bytes.indices) {
                        modifiedIndices.add(act.offset + i)
                    }
                }
                is Action.Delete -> {}
            }
        }
    }

    fun toByteArray(): ByteArray = data.clone()

    fun searchHex(pattern: ByteArray, startOffset: Int, forward: Boolean): Int {
        if (pattern.isEmpty() || data.isEmpty()) return -1
        if (forward) {
            val start = startOffset.coerceIn(0, (data.size - pattern.size).coerceAtLeast(0))
            for (i in start..(data.size - pattern.size)) {
                var match = true
                for (j in pattern.indices) {
                    if (data[i + j] != pattern[j]) {
                        match = false
                        break
                    }
                }
                if (match) return i
            }
        } else {
            val start = startOffset.coerceIn(0, (data.size - pattern.size).coerceAtLeast(0))
            for (i in start downTo 0) {
                var match = true
                for (j in pattern.indices) {
                    if (data[i + j] != pattern[j]) {
                        match = false
                        break
                    }
                }
                if (match) return i
            }
        }
        return -1
    }

    fun searchText(text: String, startOffset: Int, forward: Boolean, ignoreCase: Boolean, charset: Charset = Charsets.UTF_8): Int {
        if (text.isEmpty() || data.isEmpty()) return -1
        val pattern = text.toByteArray(charset)
        if (!ignoreCase) {
            return searchHex(pattern, startOffset, forward)
        } else {
            val lower = text.lowercase()
            val len = text.length
            if (forward) {
                val start = startOffset.coerceIn(0, (data.size - len).coerceAtLeast(0))
                for (i in start..(data.size - len)) {
                    var match = true
                    for (j in 0 until len) {
                        val c = (data[i + j].toInt() and 0xFF).toChar().lowercaseChar()
                        if (c != lower[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) return i
                }
            } else {
                val start = startOffset.coerceIn(0, (data.size - len).coerceAtLeast(0))
                for (i in start downTo 0) {
                    var match = true
                    for (j in 0 until len) {
                        val c = (data[i + j].toInt() and 0xFF).toChar().lowercaseChar()
                        if (c != lower[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) return i
                }
            }
            return -1
        }
    }

    fun replace(offset: Int, patternLength: Int, replacement: ByteArray) {
        if (patternLength == replacement.size) {
            modifyBytes(offset, replacement)
        } else {
            deleteBytes(offset, patternLength)
            insertBytes(offset, replacement)
        }
    }

    fun replaceAllHex(pattern: ByteArray, replacement: ByteArray): Int {
        if (pattern.isEmpty()) return 0
        var count = 0
        var cur = searchHex(pattern, 0, forward = true)
        while (cur != -1 && cur < data.size) {
            replace(cur, pattern.size, replacement)
            count++
            cur = searchHex(pattern, cur + replacement.size, forward = true)
        }
        return count
    }

    fun markSaved() {
        undoStack.clear()
        redoStack.clear()
        modifiedIndices.clear()
    }
}
