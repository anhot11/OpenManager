/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.hex

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class HexInspectorData(
    val offset: Int,
    val hexStr: String,
    val uint8Str: String,
    val int8Str: String,
    val int16LEStr: String,
    val int32LEStr: String,
    val float32LEStr: String,
    val binStr: String,
    val charStr: String
)

object HexInspector {

    fun inspect(buffer: HexBuffer, offset: Int): HexInspectorData {
        if (offset < 0 || offset >= buffer.size) {
            return HexInspectorData(
                offset = offset,
                hexStr = "-",
                uint8Str = "-",
                int8Str = "-",
                int16LEStr = "-",
                int32LEStr = "-",
                float32LEStr = "-",
                binStr = "-",
                charStr = "-"
            )
        }

        val b0 = buffer.getByte(offset).toInt() and 0xFF
        val hexStr = String.format("0x%02X", b0)
        val uint8Str = b0.toString()
        val int8Str = buffer.getByte(offset).toInt().toString()

        val binStr = String.format("%8s", Integer.toBinaryString(b0)).replace(' ', '0')
        val charStr = if (b0 in 32..126) "'${b0.toChar()}'" else "·"

        // Int16 LE
        val int16LEStr = if (offset + 1 < buffer.size) {
            val bytes = buffer.getBytes(offset, 2)
            val v = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short
            String.format("0x%04X (%d)", v.toInt() and 0xFFFF, v)
        } else {
            "-"
        }

        // Int32 LE
        val int32LEStr = if (offset + 3 < buffer.size) {
            val bytes = buffer.getBytes(offset, 4)
            val v = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
            String.format("0x%08X (%d)", v, v)
        } else {
            "-"
        }

        // Float32 LE
        val float32LEStr = if (offset + 3 < buffer.size) {
            val bytes = buffer.getBytes(offset, 4)
            val v = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).float
            String.format("%.4f", v)
        } else {
            "-"
        }

        return HexInspectorData(
            offset = offset,
            hexStr = hexStr,
            uint8Str = uint8Str,
            int8Str = int8Str,
            int16LEStr = int16LEStr,
            int32LEStr = int32LEStr,
            float32LEStr = float32LEStr,
            binStr = binStr,
            charStr = charStr
        )
    }
}
