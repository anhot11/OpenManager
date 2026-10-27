/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

data class AxmlStringItem(
    val index: Int,
    var value: String,
    val originalValue: String = value
) {
    val isModified: Boolean get() = value != originalValue
}

object AxmlEngine {

    private const val AXML_MAGIC = 0x00080003
    private const val CHUNK_STRING_POOL = 0x0001
    private const val UTF8_FLAG = 0x0100

    fun parseStrings(bytes: ByteArray): Pair<List<AxmlStringItem>, IntRange> {
        if (!AxmlDecoder.isAxml(bytes)) {
            throw IllegalArgumentException("No es un archivo AXML binario válido")
        }

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.int
        val fileSize = bb.int

        var poolStart = -1
        var poolSize = 0
        val strings = mutableListOf<AxmlStringItem>()

        while (bb.hasRemaining()) {
            val chunkPos = bb.position()
            if (chunkPos + 8 > bytes.size) break
            val chunkType = bb.short.toInt() and 0xFFFF
            val headerSize = bb.short.toInt() and 0xFFFF
            val chunkSize = bb.int

            if (chunkSize <= 0 || chunkPos + chunkSize > bytes.size) break

            if (chunkType == CHUNK_STRING_POOL) {
                poolStart = chunkPos
                poolSize = chunkSize

                bb.position(chunkPos + 8)
                val stringCount = bb.int
                val styleCount = bb.int
                val flags = bb.int
                val stringsStart = bb.int
                val stylesStart = bb.int

                val isUtf8 = (flags and UTF8_FLAG) != 0
                val offsets = IntArray(stringCount)
                for (i in 0 until stringCount) {
                    offsets[i] = bb.int
                }

                val stringsBase = chunkPos + stringsStart
                for (i in 0 until stringCount) {
                    val strPos = stringsBase + offsets[i]
                    val text = if (strPos in 0 until bytes.size) {
                        if (isUtf8) {
                            readUtf8(bytes, strPos)
                        } else {
                            readUtf16(bytes, strPos)
                        }
                    } else {
                        ""
                    }
                    strings.add(AxmlStringItem(i, text))
                }
                break
            } else {
                bb.position(chunkPos + chunkSize)
            }
        }

        val range = if (poolStart >= 0) poolStart until (poolStart + poolSize) else IntRange.EMPTY
        return Pair(strings, range)
    }

    private fun readUtf8(bytes: ByteArray, offset: Int): String {
        var pos = offset
        if (pos >= bytes.size) return ""
        // Skip UTF16 len ULEB
        var charLen = bytes[pos].toInt() and 0xFF
        if ((charLen and 0x80) != 0) {
            pos++
            if (pos >= bytes.size) return ""
            charLen = ((charLen and 0x7F) shl 8) or (bytes[pos].toInt() and 0xFF)
        }
        pos++
        if (pos >= bytes.size) return ""

        // Read UTF8 byte len ULEB
        var byteLen = bytes[pos].toInt() and 0xFF
        if ((byteLen and 0x80) != 0) {
            pos++
            if (pos >= bytes.size) return ""
            byteLen = ((byteLen and 0x7F) shl 8) or (bytes[pos].toInt() and 0xFF)
        }
        pos++

        if (pos + byteLen > bytes.size) {
            byteLen = (bytes.size - pos).coerceAtLeast(0)
        }
        return String(bytes, pos, byteLen, StandardCharsets.UTF_8)
    }

    private fun readUtf16(bytes: ByteArray, offset: Int): String {
        var pos = offset
        if (pos + 2 > bytes.size) return ""
        val b0 = bytes[pos].toInt() and 0xFF
        val b1 = bytes[pos + 1].toInt() and 0xFF
        var len = b0 or (b1 shl 8)
        pos += 2

        if ((len and 0x8000) != 0) {
            if (pos + 2 > bytes.size) return ""
            val b2 = bytes[pos].toInt() and 0xFF
            val b3 = bytes[pos + 1].toInt() and 0xFF
            len = ((len and 0x7FFF) shl 16) or (b2 or (b3 shl 8))
            pos += 2
        }

        val byteLen = len * 2
        if (pos + byteLen > bytes.size) return ""
        return String(bytes, pos, byteLen, StandardCharsets.UTF_16LE)
    }

    fun rebuildWithModifiedStrings(originalBytes: ByteArray, modifiedStrings: List<String>): ByteArray {
        val (_, poolRange) = parseStrings(originalBytes)
        if (poolRange.isEmpty()) {
            throw IllegalStateException("No se encontró StringPool en el archivo AXML")
        }

        val poolStart = poolRange.first
        val oldPoolSize = poolRange.last - poolRange.first + 1

        val newPoolBytes = buildStringPool(modifiedStrings, useUtf8 = true)
        val newPoolSize = newPoolBytes.size

        val sizeDiff = newPoolSize - oldPoolSize
        val newTotalSize = originalBytes.size + sizeDiff

        val result = ByteArray(newTotalSize)

        // 1. Copy AXML header
        System.arraycopy(originalBytes, 0, result, 0, poolStart)

        // 2. Copy new StringPool
        System.arraycopy(newPoolBytes, 0, result, poolStart, newPoolSize)

        // 3. Copy remaining XML chunks
        val restOffset = poolStart + oldPoolSize
        val restLen = originalBytes.size - restOffset
        if (restLen > 0) {
            System.arraycopy(originalBytes, restOffset, result, poolStart + newPoolSize, restLen)
        }

        // 4. Update AXML total file size at offset 4
        val bb = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(4, newTotalSize)

        return result
    }

    private fun buildStringPool(strings: List<String>, useUtf8: Boolean): ByteArray {
        val stringCount = strings.size
        val headerSize = 28
        val offsetsSize = stringCount * 4
        val stringsStart = headerSize + offsetsSize

        val dataStream = ByteArrayOutputStream()
        val offsets = IntArray(stringCount)

        for (i in 0 until stringCount) {
            offsets[i] = dataStream.size()
            val str = strings[i]
            if (useUtf8) {
                val utf8Bytes = str.toByteArray(StandardCharsets.UTF_8)
                writeUleb128(dataStream, str.length)
                writeUleb128(dataStream, utf8Bytes.size)
                dataStream.write(utf8Bytes)
                dataStream.write(0)
            } else {
                val utf16Bytes = str.toByteArray(StandardCharsets.UTF_16LE)
                val len = str.length
                if (len <= 0x7FFF) {
                    dataStream.write(len and 0xFF)
                    dataStream.write((len shr 8) and 0xFF)
                } else {
                    val high = ((len shr 16) and 0x7FFF) or 0x8000
                    val low = len and 0xFFFF
                    dataStream.write(high and 0xFF)
                    dataStream.write((high shr 8) and 0xFF)
                    dataStream.write(low and 0xFF)
                    dataStream.write((low shr 8) and 0xFF)
                }
                dataStream.write(utf16Bytes)
                dataStream.write(0)
                dataStream.write(0)
            }
        }

        val rawData = dataStream.toByteArray()
        val pad = (4 - (rawData.size % 4)) % 4
        val paddedDataSize = rawData.size + pad
        val totalSize = stringsStart + paddedDataSize

        val bb = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(CHUNK_STRING_POOL.toShort())
        bb.putShort(headerSize.toShort())
        bb.putInt(totalSize)
        bb.putInt(stringCount)
        bb.putInt(0) // styleCount
        bb.putInt(if (useUtf8) UTF8_FLAG else 0)
        bb.putInt(stringsStart)
        bb.putInt(0) // stylesStart

        for (off in offsets) {
            bb.putInt(off)
        }

        bb.put(rawData)
        for (p in 0 until pad) {
            bb.put(0.toByte())
        }

        return bb.array()
    }

    private fun writeUleb128(os: ByteArrayOutputStream, value: Int) {
        if (value <= 0x7F) {
            os.write(value)
        } else {
            val b0 = ((value shr 8) and 0x7F) or 0x80
            val b1 = value and 0xFF
            os.write(b0)
            os.write(b1)
        }
    }
}
