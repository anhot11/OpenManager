/*
 * OpenManager - Android Binary XML (AXML) Decoder
 * Decodes compiled binary Android XML (AndroidManifest.xml, layout, resources) into readable formatted XML text.
 */

package me.zhanghai.android.files.file

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object AxmlDecoder {

    private const val AXML_MAGIC = 0x00080003

    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_RESOURCE_MAP = 0x0180
    private const val CHUNK_START_NAMESPACE = 0x0100
    private const val CHUNK_END_NAMESPACE = 0x0101
    private const val CHUNK_START_ELEMENT = 0x0102
    private const val CHUNK_END_ELEMENT = 0x0103
    private const val CHUNK_CDATA = 0x0104

    // TypedValue types
    private const val TYPE_NULL = 0x00
    private const val TYPE_REFERENCE = 0x01
    private const val TYPE_ATTRIBUTE = 0x02
    private const val TYPE_STRING = 0x03
    private const val TYPE_FLOAT = 0x04
    private const val TYPE_DIMENSION = 0x05
    private const val TYPE_FRACTION = 0x06
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val TYPE_INT_COLOR_ARGB8 = 0x1c
    private const val TYPE_INT_COLOR_RGB8 = 0x1d
    private const val TYPE_INT_COLOR_ARGB4 = 0x1e
    private const val TYPE_INT_COLOR_RGB4 = 0x1f

    fun isAxml(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return buffer.int == AXML_MAGIC
    }

    fun decode(bytes: ByteArray): String {
        if (!isAxml(bytes)) {
            return String(bytes, StandardCharsets.UTF_8)
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int
        val fileSize = buffer.int

        var stringPool: List<String> = emptyList()
        val namespaces = mutableMapOf<String, String>() // uri -> prefix
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        var indent = 0

        while (buffer.hasRemaining()) {
            val chunkPos = buffer.position()
            if (chunkPos + 8 > bytes.size) break
            val chunkType = buffer.short.toInt() and 0xFFFF
            val headerSize = buffer.short.toInt() and 0xFFFF
            val chunkSize = buffer.int

            if (chunkSize <= 0 || chunkPos + chunkSize > bytes.size) {
                break
            }

            when (chunkType) {
                CHUNK_STRING_POOL -> {
                    stringPool = parseStringPool(buffer, chunkPos, chunkSize)
                    buffer.position(chunkPos + chunkSize)
                }
                CHUNK_RESOURCE_MAP -> {
                    // Skip resource IDs
                    buffer.position(chunkPos + chunkSize)
                }
                CHUNK_START_NAMESPACE -> {
                    buffer.position(chunkPos + 16)
                    val prefixIdx = buffer.int
                    val uriIdx = buffer.int
                    val prefix = stringPool.getOrNull(prefixIdx) ?: ""
                    val uri = stringPool.getOrNull(uriIdx) ?: ""
                    if (uri.isNotEmpty()) {
                        namespaces[uri] = prefix
                    }
                    buffer.position(chunkPos + chunkSize)
                }
                CHUNK_END_NAMESPACE -> {
                    buffer.position(chunkPos + chunkSize)
                }
                CHUNK_START_ELEMENT -> {
                    buffer.position(chunkPos + 16)
                    val nsIdx = buffer.int
                    val nameIdx = buffer.int
                    val attrStart = buffer.short.toInt() and 0xFFFF
                    val attrSize = buffer.short.toInt() and 0xFFFF
                    val attrCount = buffer.short.toInt() and 0xFFFF
                    val idIndex = buffer.short.toInt() and 0xFFFF
                    val classIndex = buffer.short.toInt() and 0xFFFF
                    val styleIndex = buffer.short.toInt() and 0xFFFF

                    val tagName = stringPool.getOrNull(nameIdx) ?: "unknown"

                    appendIndent(sb, indent)
                    sb.append("<").append(tagName)

                    if (indent == 0) {
                        for ((uri, prefix) in namespaces) {
                            sb.append("\n    xmlns")
                            if (prefix.isNotEmpty()) {
                                sb.append(":").append(prefix)
                            }
                            sb.append("=\"").append(escapeXml(uri)).append("\"")
                        }
                    }

                    // Attributes
                    var attrOffset = chunkPos + 16 + 20 // after header fields
                    for (i in 0 until attrCount) {
                        if (attrOffset + 20 > chunkPos + chunkSize) break
                        buffer.position(attrOffset)
                        val aNsIdx = buffer.int
                        val aNameIdx = buffer.int
                        val aValStringIdx = buffer.int
                        val aTypeAndFlags = buffer.int
                        val aData = buffer.int

                        val aNs = stringPool.getOrNull(aNsIdx)
                        val aName = stringPool.getOrNull(aNameIdx) ?: "attr$i"
                        val prefix = if (!aNs.isNullOrEmpty()) namespaces[aNs]?.let { "$it:" } ?: "" else ""

                        val aVal = formatAttributeValue(
                            aTypeAndFlags ushr 24,
                            aData,
                            stringPool.getOrNull(aValStringIdx),
                            stringPool
                        )

                        sb.append("\n")
                        appendIndent(sb, indent + 1)
                        sb.append(prefix).append(aName).append("=\"").append(escapeXml(aVal)).append("\"")

                        attrOffset += 20
                    }

                    sb.append(">\n")
                    indent++
                    buffer.position(chunkPos + chunkSize)
                }
                CHUNK_END_ELEMENT -> {
                    indent = maxOf(0, indent - 1)
                    buffer.position(chunkPos + 16)
                    val nsIdx = buffer.int
                    val nameIdx = buffer.int
                    val tagName = stringPool.getOrNull(nameIdx) ?: "unknown"
                    appendIndent(sb, indent)
                    sb.append("</").append(tagName).append(">\n")
                    buffer.position(chunkPos + chunkSize)
                }
                CHUNK_CDATA -> {
                    buffer.position(chunkPos + 16)
                    val dataIdx = buffer.int
                    val cdata = stringPool.getOrNull(dataIdx) ?: ""
                    appendIndent(sb, indent)
                    sb.append(escapeXml(cdata)).append("\n")
                    buffer.position(chunkPos + chunkSize)
                }
                else -> {
                    buffer.position(chunkPos + chunkSize)
                }
            }
        }

        return sb.toString()
    }

    private fun parseStringPool(buffer: ByteBuffer, chunkPos: Int, chunkSize: Int): List<String> {
        val stringCount = buffer.getInt(chunkPos + 8)
        val styleCount = buffer.getInt(chunkPos + 12)
        val flags = buffer.getInt(chunkPos + 16)
        val stringsStart = chunkPos + buffer.getInt(chunkPos + 20)
        val isUtf8 = (flags and (1 shl 8)) != 0

        val stringOffsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            stringOffsets[i] = buffer.getInt(chunkPos + 28 + i * 4)
        }

        val result = ArrayList<String>(stringCount)
        val bytes = buffer.array()

        for (i in 0 until stringCount) {
            val offset = stringsStart + stringOffsets[i]
            if (offset >= bytes.size) {
                result.add("")
                continue
            }
            if (isUtf8) {
                // Skip character length byte(s)
                var pos = offset
                if ((bytes[pos].toInt() and 0x80) != 0) pos += 2 else pos += 1
                // Byte length
                val len: Int
                if ((bytes[pos].toInt() and 0x80) != 0) {
                    len = ((bytes[pos].toInt() and 0x7F) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
                    pos += 2
                } else {
                    len = bytes[pos].toInt() and 0xFF
                    pos += 1
                }
                val str = if (pos + len <= bytes.size) {
                    String(bytes, pos, len, StandardCharsets.UTF_8)
                } else {
                    ""
                }
                result.add(str)
            } else {
                // UTF-16LE
                var pos = offset
                val charLen: Int
                val b0 = bytes[pos].toInt() and 0xFF
                val b1 = bytes[pos + 1].toInt() and 0xFF
                if ((b1 and 0x80) != 0) {
                    charLen = ((b1 and 0x7F) shl 8) or b0
                    pos += 4
                } else {
                    charLen = (b1 shl 8) or b0
                    pos += 2
                }
                val byteLen = charLen * 2
                val str = if (pos + byteLen <= bytes.size) {
                    String(bytes, pos, byteLen, StandardCharsets.UTF_16LE)
                } else {
                    ""
                }
                result.add(str)
            }
        }

        return result
    }

    private fun formatAttributeValue(
        type: Int,
        data: Int,
        rawString: String?,
        stringPool: List<String>
    ): String {
        if (!rawString.isNullOrEmpty()) {
            return rawString
        }
        return when (type) {
            TYPE_STRING -> stringPool.getOrNull(data) ?: ""
            TYPE_ATTRIBUTE -> String.format("?0x%08X", data)
            TYPE_REFERENCE -> if (data == 0) "@null" else String.format("@0x%08X", data)
            TYPE_FLOAT -> java.lang.Float.intBitsToFloat(data).toString()
            TYPE_INT_HEX -> String.format("0x%08X", data)
            TYPE_INT_BOOLEAN -> if (data != 0) "true" else "false"
            TYPE_DIMENSION -> "${data shr 8}${getDimensionUnit(data and 0xFF)}"
            TYPE_FRACTION -> "${data shr 8}${getFractionUnit(data and 0xFF)}"
            TYPE_INT_COLOR_ARGB8, TYPE_INT_COLOR_RGB8,
            TYPE_INT_COLOR_ARGB4, TYPE_INT_COLOR_RGB4 -> String.format("#%08X", data)
            TYPE_INT_DEC -> data.toString()
            else -> if (data != 0) data.toString() else ""
        }
    }

    private fun getDimensionUnit(data: Int): String = when (data) {
        0 -> "px"
        1 -> "dp"
        2 -> "sp"
        3 -> "pt"
        4 -> "in"
        5 -> "mm"
        else -> ""
    }

    private fun getFractionUnit(data: Int): String = when (data) {
        0 -> "%"
        1 -> "%p"
        else -> ""
    }

    private fun appendIndent(sb: StringBuilder, indent: Int) {
        for (i in 0 until indent) {
            sb.append("    ")
        }
    }

    private fun escapeXml(str: String): String =
        str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
