/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.dex

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.databinding.DialogDexOpenChoiceBinding
import me.zhanghai.android.files.viewer.text.TextEditorActivity
import me.zhanghai.android.files.filelist.HexViewerDialogFragment
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.viewer.binary.DexInfoDialogFragment
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexActionDialogFragment : AppCompatDialogFragment() {

    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val path = args.path
        val fileName = path.fileName.toString()
        val binding = DialogDexOpenChoiceBinding.inflate(layoutInflater)

        binding.dexFileName.text = fileName

        var version = "DEX"
        var classCount = 0
        var methodCount = 0
        var fileSize = 0

        try {
            path.newInputStream().use { input ->
                val header = ByteArray(112)
                val read = input.read(header)
                if (read >= 112 && header[0] == 0x64.toByte() && header[1] == 0x65.toByte() && header[2] == 0x78.toByte()) {
                    version = "DEX " + String(header, 4, 3)
                    val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                    fileSize = bb.getInt(32)
                    methodCount = bb.getInt(88)
                    classCount = bb.getInt(96)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val sizeStr = if (fileSize > 0) String.format("%.2f MB", fileSize / (1024.0 * 1024.0)) else ""
        binding.dexFileDetails.text = "$version  •  $sizeStr  •  $classCount clases  •  $methodCount métodos"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()

        // 1. Editor DEX+ (Completo)
        binding.btnModeEditorPlus.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), DexEditorActivity::class.java).apply {
                extraPath = path
                putExtra(DexEditorActivity.EXTRA_INITIAL_TAB, 0)
            }
            startActivity(intent)
        }

        // 2. Editor de Cadenas (String Pool)
        binding.btnModeStringPool.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), DexEditorActivity::class.java).apply {
                extraPath = path
                putExtra(DexEditorActivity.EXTRA_INITIAL_TAB, 1)
            }
            startActivity(intent)
        }

        // 3. Resumen e Información
        binding.btnModeSummary.setOnClickListener {
            dialog.dismiss()
            DexInfoDialogFragment.show(path, parentFragmentManager)
        }

        // 4. Editor Hexadecimal
        binding.btnModeHex.setOnClickListener {
            dialog.dismiss()
            HexViewerDialogFragment.show(path, parentFragmentManager)
        }

        // 5. Abrir como Texto
        binding.btnModeText.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), TextEditorActivity::class.java).apply {
                extraPath = path
            }
            startActivity(intent)
        }

        return dialog
    }

    companion object {
        fun show(path: Path, fragmentManager: FragmentManager) {
            DexActionDialogFragment().putArgs(Args(path)).show(fragmentManager, null)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs
}
