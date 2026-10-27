/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.arsc

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class ArscStringItem(
    val index: Int,
    var value: String,
    val originalValue: String = value
) {
    val isModified: Boolean get() = value != originalValue
}

data class ArscPackageItem(
    val id: Int,
    val name: String
)

data class ArscResourceItem(
    val type: String,
    val key: String
)

data class ArscModel(
    val strings: List<ArscStringItem>,
    val packages: List<ArscPackageItem>,
    val resources: List<ArscResourceItem>,
    val isUtf8: Boolean,
    val stringPoolOffset: Int,
    val stringPoolSize: Int
)

object ArscEngine {

    const val RES_STRING_POOL_TYPE: Short = 0x0001
    const val RES_TABLE_TYPE: Short = 0x0002
    const val RES_TABLE_PACKAGE_TYPE: Short = 0x0200
    const val UTF8_FLAG: Int = 0x0100

    fun parse(bytes: ByteArray): ArscModel {
        if (bytes.size < 12) {
            throw IllegalArgumentException("El archivo es demasiado pequeño para ser un archivo ARSC válido.")
        }

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val type = bb.short
        val headerSize = bb.short
        val tableSize = bb.int

        if (type != RES_TABLE_TYPE) {
            throw IllegalArgumentException("Cabecera ARSC inválida (tipo: 0x${type.toString(16)})")
        }

        // Global String Pool
        val poolOffset = headerSize.toInt()
        bb.position(poolOffset)

        val poolType = bb.short
        val poolHeaderSize = bb.short
        val poolSize = bb.int
        val stringCount = bb.int
        val styleCount = bb.int
        val flags = bb.int
        val stringsStart = bb.int
        val stylesStart = bb.int

        if (poolType != RES_STRING_POOL_TYPE) {
            throw IllegalArgumentException("StringPool inválido en ARSC (tipo: 0x${poolType.toString(16)})")
        }

        val isUtf8 = (flags and UTF8_FLAG) != 0
        val stringsList = mutableListOf<ArscStringItem>()

        val stringOffsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            stringOffsets[i] = bb.int
        }

        val stringsAbsoluteBase = poolOffset + stringsStart

        for (i in 0 until stringCount) {
            val strPos = stringsAbsoluteBase + stringOffsets[i]
            if (strPos in 0 until bytes.size) {
                val str = if (isUtf8) {
                    readUtf8String(bytes, strPos)
                } else {
                    readUtf16String(bytes, strPos)
                }
                stringsList.add(ArscStringItem(i, str))
            } else {
                stringsList.add(ArscStringItem(i, ""))
            }
        }

        // Parse Packages
        val packagesList = mutableListOf<ArscPackageItem>()
        val resourcesList = mutableListOf<ArscResourceItem>()

        var currentOffset = poolOffset + poolSize
        while (currentOffset + 8 <= bytes.size) {
            bb.position(currentOffset)
            val chunkType = bb.short
            val chunkHeaderSize = bb.short
            val chunkSize = bb.int

            if (chunkSize <= 0 || currentOffset + chunkSize > bytes.size) break

            if (chunkType == RES_TABLE_PACKAGE_TYPE && chunkHeaderSize >= 288) {
                val pkgId = bb.int
                val nameChars = CharArray(128)
                for (j in 0 until 128) {
                    nameChars[j] = bb.char
                }
                val pkgName = String(nameChars).trimEnd('\u0000')
                packagesList.add(ArscPackageItem(pkgId, pkgName))

                // Parse Type & Key String pools inside package
                val typeStringsOff = bb.int
                val lastPublicType = bb.int
                val keyStringsOff = bb.int

                if (keyStringsOff > 0 && currentOffset + keyStringsOff + 28 <= bytes.size) {
                    try {
                        val keyPoolPos = currentOffset + keyStringsOff
                        bb.position(keyPoolPos)
                        val kType = bb.short
                        val kHdrSize = bb.short
                        val kSize = bb.int
                        val kCount = bb.int
                        val kStyleCount = bb.int
                        val kFlags = bb.int
                        val kStringsStart = bb.int

                        if (kType == RES_STRING_POOL_TYPE && kCount in 1..20000) {
                            val kUtf8 = (kFlags and UTF8_FLAG) != 0
                            val kOffsets = IntArray(kCount)
                            for (k in 0 until kCount) {
                                kOffsets[k] = bb.int
                            }
                            val kBase = keyPoolPos + kStringsStart
                            for (k in 0 until kCount) {
                                val entryPos = kBase + kOffsets[k]
                                if (entryPos in 0 until bytes.size) {
                                    val keyStr = if (kUtf8) {
                                        readUtf8String(bytes, entryPos)
                                    } else {
                                        readUtf16String(bytes, entryPos)
                                    }
                                    if (keyStr.isNotEmpty()) {
                                        resourcesList.add(ArscResourceItem("resource", keyStr))
                                    }
                                }
                            }
                        }
                    } catch (ignored: Exception) {}
                }
            }

            currentOffset += chunkSize
        }

        return ArscModel(
            strings = stringsList,
            packages = packagesList,
            resources = resourcesList,
            isUtf8 = isUtf8,
            stringPoolOffset = poolOffset,
            stringPoolSize = poolSize
        )
    }

    private fun readUtf8String(bytes: ByteArray, offset: Int): String {
        var pos = offset
        if (pos >= bytes.size) return ""

        // Skip UTF-16 length ULEB
        var charLen = bytes[pos].toInt() and 0xFF
        if ((charLen and 0x80) != 0) {
            pos++
            if (pos >= bytes.size) return ""
            charLen = ((charLen and 0x7F) shl 8) or (bytes[pos].toInt() and 0xFF)
        }
        pos++
        if (pos >= bytes.size) return ""

        // Read UTF-8 byte length ULEB
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

    private fun readUtf16String(bytes: ByteArray, offset: Int): String {
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
            val high = b2 or (b3 shl 8)
            len = ((len and 0x7FFF) shl 16) or high
            pos += 2
        }

        val byteLen = len * 2
        if (pos + byteLen > bytes.size) {
            return ""
        }

        return String(bytes, pos, byteLen, StandardCharsets.UTF_16LE)
    }

    fun rebuildWithModifiedStrings(originalBytes: ByteArray, modifiedStrings: List<String>): ByteArray {
        val model = parse(originalBytes)
        val poolOffset = model.stringPoolOffset
        val oldPoolSize = model.stringPoolSize

        val newPoolBytes = buildStringPool(modifiedStrings, useUtf8 = true)
        val newPoolSize = newPoolBytes.size

        val sizeDiff = newPoolSize - oldPoolSize
        val newTotalSize = originalBytes.size + sizeDiff

        val result = ByteArray(newTotalSize)

        // 1. Copy table header (0 until poolOffset)
        System.arraycopy(originalBytes, 0, result, 0, poolOffset)

        // 2. Copy new string pool
        System.arraycopy(newPoolBytes, 0, result, poolOffset, newPoolSize)

        // 3. Copy remainder of original file (after old string pool)
        val restOffset = poolOffset + oldPoolSize
        val restLen = originalBytes.size - restOffset
        if (restLen > 0) {
            System.arraycopy(originalBytes, restOffset, result, poolOffset + newPoolSize, restLen)
        }

        // 4. Update root table total size at offset 4
        val bb = ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(4, newTotalSize)

        return result
    }

    private fun buildStringPool(strings: List<String>, useUtf8: Boolean): ByteArray {
        val stringCount = strings.size
        val headerSize = 28
        val offsetsSize = stringCount * 4
        val stringsStart = headerSize + offsetsSize

        val stringDataStream = ByteArrayOutputStream()
        val offsets = IntArray(stringCount)

        for (i in 0 until stringCount) {
            offsets[i] = stringDataStream.size()
            val str = strings[i]
            if (useUtf8) {
                val utf8Bytes = str.toByteArray(StandardCharsets.UTF_8)
                val charLen = str.length
                val byteLen = utf8Bytes.size

                writeUleb128(stringDataStream, charLen)
                writeUleb128(stringDataStream, byteLen)
                stringDataStream.write(utf8Bytes)
                stringDataStream.write(0) // Null terminator
            } else {
                val utf16Bytes = str.toByteArray(StandardCharsets.UTF_16LE)
                val charLen = str.length

                if (charLen <= 0x7FFF) {
                    stringDataStream.write(charLen and 0xFF)
                    stringDataStream.write((charLen shr 8) and 0xFF)
                } else {
                    val high = ((charLen shr 16) and 0x7FFF) or 0x8000
                    val low = charLen and 0xFFFF
                    stringDataStream.write(high and 0xFF)
                    stringDataStream.write((high shr 8) and 0xFF)
                    stringDataStream.write(low and 0xFF)
                    stringDataStream.write((low shr 8) and 0xFF)
                }
                stringDataStream.write(utf16Bytes)
                stringDataStream.write(0)
                stringDataStream.write(0) // 2-byte null terminator
            }
        }

        val rawStringData = stringDataStream.toByteArray()
        // Pad strings to 4 bytes boundary
        val pad = (4 - (rawStringData.size % 4)) % 4
        val paddedStringDataSize = rawStringData.size + pad

        val totalChunkSize = stringsStart + paddedStringDataSize

        val bb = ByteBuffer.allocate(totalChunkSize).order(ByteOrder.LITTLE_ENDIAN)
        bb.putShort(RES_STRING_POOL_TYPE)
        bb.putShort(headerSize.toShort())
        bb.putInt(totalChunkSize)
        bb.putInt(stringCount)
        bb.putInt(0) // styleCount
        bb.putInt(if (useUtf8) UTF8_FLAG else 0)
        bb.putInt(stringsStart)
        bb.putInt(0) // stylesStart

        for (off in offsets) {
            bb.putInt(off)
        }

        bb.put(rawStringData)
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
