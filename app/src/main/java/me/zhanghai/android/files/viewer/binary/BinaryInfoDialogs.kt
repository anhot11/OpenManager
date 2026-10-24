/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.binary

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import me.zhanghai.android.files.viewer.text.TextEditorActivity
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// DEX Info Dialog
class DexInfoDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val path = args.path
        val fileName = path.fileName.toString()

        var version = "DEX"
        var fileSize = 0
        var classCount = 0
        var methodCount = 0
        var fieldCount = 0
        var stringCount = 0

        try {
            path.newInputStream().use { input ->
                val header = ByteArray(112)
                val read = input.read(header)
                if (read >= 112 && header[0] == 0x64.toByte() && header[1] == 0x65.toByte() && header[2] == 0x78.toByte()) {
                    version = "DEX " + String(header, 4, 3)
                    val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    fileSize = bb.getInt(32)
                    stringCount = bb.getInt(56)
                    fieldCount = bb.getInt(80)
                    methodCount = bb.getInt(88)
                    classCount = bb.getInt(96)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val message = StringBuilder().apply {
            append("• Formato: $version\n")
            append("• Clases definidas: $classCount\n")
            append("• Métodos: $methodCount\n")
            append("• Campos: $fieldCount\n")
            append("• Cadenas de texto: $stringCount\n")
        }.toString()

        return MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.file_dex_icon)
            .setTitle(fileName)
            .setMessage(message)
            .setNeutralButton("Ver cadenas") { _, _ ->
                extractAndShowDexStrings(path)
            }
            .setNegativeButton("Abrir como texto") { _, _ ->
                val intent = Intent(context, TextEditorActivity::class.java).apply {
                    extraPath = path
                }
                context.startActivity(intent)
            }
            .setPositiveButton("Cerrar", null)
            .create()
    }

    private fun extractAndShowDexStrings(path: Path) {
        val context = requireContext()
        try {
            val strings = mutableListOf<String>()
            path.newInputStream().use { input ->
                val bytes = input.readBytes()
                if (bytes.size >= 112) {
                    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    val stringCount = bb.getInt(56)
                    val stringIdsOff = bb.getInt(60)

                    for (i in 0 until stringCount.coerceAtMost(5000)) {
                        val off = bb.getInt(stringIdsOff + i * 4)
                        if (off in 0 until bytes.size) {
                            // Skip ULEB128 length
                            var pos = off
                            while (pos < bytes.size && (bytes[pos].toInt() and 0x80) != 0) {
                                pos++
                            }
                            pos++ // past length byte
                            val sb = StringBuilder()
                            while (pos < bytes.size && bytes[pos].toInt() != 0) {
                                val c = bytes[pos].toInt().toChar()
                                if (c in ' '..'~' || c == '\n' || c == '\t') {
                                    sb.append(c)
                                }
                                pos++
                            }
                            if (sb.isNotEmpty()) {
                                strings.add(sb.toString())
                            }
                        }
                    }
                }
            }

            val temp = File(context.cacheDir, "${path.fileName}_strings.txt")
            temp.writeText(strings.joinToString("\n"))
            val intent = Intent(context, TextEditorActivity::class.java).apply {
                extraPath = java8.nio.file.Paths.get(temp.absolutePath)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al extraer cadenas: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun show(path: Path, fragmentManager: FragmentManager) {
            DexInfoDialogFragment().putArgs(Args(path)).show(fragmentManager, null)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}

// ARSC Info Dialog
class ArscInfoDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val path = args.path
        val fileName = path.fileName.toString()

        var isValidArsc = false
        var packageCount = 0

        try {
            path.newInputStream().use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read >= 12 && header[0] == 0x02.toByte() && header[1] == 0x00.toByte()) {
                    isValidArsc = true
                    val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    packageCount = bb.getInt(8)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val message = if (isValidArsc) {
            "• Tabla de recursos binarios compilados de Android\n• Paquetes declarados: $packageCount\n• Estado: Válido"
        } else {
            "Tabla de recursos binarios (resources.arsc)"
        }

        return MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.file_arsc_icon)
            .setTitle(fileName)
            .setMessage(message)
            .setNegativeButton("Abrir como texto") { _, _ ->
                val intent = Intent(context, TextEditorActivity::class.java).apply {
                    extraPath = path
                }
                context.startActivity(intent)
            }
            .setPositiveButton("Cerrar", null)
            .create()
    }

    companion object {
        fun show(path: Path, fragmentManager: FragmentManager) {
            ArscInfoDialogFragment().putArgs(Args(path)).show(fragmentManager, null)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}

// ELF / SO Info Dialog
class ElfInfoDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val path = args.path
        val fileName = path.fileName.toString()

        var arch = "Desconocida"
        var bitClass = ""
        var endian = ""
        val neededLibs = mutableListOf<String>()

        try {
            path.newInputStream().use { input ->
                val header = ByteArray(64)
                val read = input.read(header)
                if (read >= 20 && header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() && header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()) {
                    bitClass = if (header[4] == 2.toByte()) "64-bit" else "32-bit"
                    endian = if (header[5] == 1.toByte()) "Little Endian" else "Big Endian"
                    val bb = ByteBuffer.wrap(header).order(if (header[5] == 1.toByte()) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
                    val machine = bb.getShort(18).toInt() and 0xFFFF
                    arch = when (machine) {
                        0x28 -> "ARM (armeabi-v7a)"
                        0xB7 -> "AArch64 (arm64-v8a)"
                        0x03 -> "x86 (i386)"
                        0x3E -> "x86_64 (amd64)"
                        0xF3 -> "RISC-V"
                        else -> "Código $machine"
                    }
                }
            }

            // Quick scan first 32KB for dependencies like libc.so, etc.
            path.newInputStream().use { input ->
                val sample = ByteArray(32768)
                val len = input.read(sample)
                if (len > 0) {
                    val text = String(sample, 0, len, Charsets.ISO_8859_1)
                    val matches = Regex("""[a-zA-Z0-9_\-\.]+\.so""").findAll(text)
                    for (m in matches) {
                        val lib = m.value
                        if (lib != fileName && !neededLibs.contains(lib)) {
                            neededLibs.add(lib)
                            if (neededLibs.size >= 8) break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val message = StringBuilder().apply {
            append("• Arquitectura: $arch ($bitClass)\n")
            append("• Formato: ELF binario nativo ($endian)\n")
            if (neededLibs.isNotEmpty()) {
                append("• Dependencias detectadas:\n  - ")
                append(neededLibs.joinToString("\n  - "))
                append("\n")
            }
        }.toString()

        return MaterialAlertDialogBuilder(context)
            .setIcon(R.drawable.file_so_icon)
            .setTitle(fileName)
            .setMessage(message)
            .setNegativeButton("Abrir como texto") { _, _ ->
                val intent = Intent(context, TextEditorActivity::class.java).apply {
                    extraPath = path
                }
                context.startActivity(intent)
            }
            .setPositiveButton("Cerrar", null)
            .create()
    }

    companion object {
        fun show(path: Path, fragmentManager: FragmentManager) {
            ElfInfoDialogFragment().putArgs(Args(path)).show(fragmentManager, null)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}
