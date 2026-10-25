/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.permission

import android.app.Dialog
import android.os.Bundle
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.SetModeDialogBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.common.PosixFileAttributes
import me.zhanghai.android.files.provider.common.PosixFileMode
import me.zhanghai.android.files.provider.common.PosixFileModeBit
import me.zhanghai.android.files.provider.common.toInt
import me.zhanghai.android.files.provider.common.toModeString
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.enumSetOf
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import me.zhanghai.android.files.util.viewModels

class SetModeDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val viewModel by viewModels { { SetModeViewModel(argsMode) } }

    private lateinit var binding: SetModeDialogBinding
    private var isUpdatingUi = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.file_properties_permission_set_mode_title)
            .apply {
                binding = SetModeDialogBinding.inflate(context.layoutInflater)
                val isDirectory = args.file.attributes.isDirectory

                binding.recursiveCheck.isVisible = isDirectory
                binding.recursiveCheck.setOnCheckedChangeListener { _, isChecked ->
                    binding.uppercaseXCheck.isEnabled = isChecked
                }
                binding.uppercaseXCheck.isVisible = isDirectory
                if (savedInstanceState == null) {
                    binding.uppercaseXCheck.isEnabled = false
                    binding.uppercaseXCheck.isChecked = true
                }

                setupListeners()
                setView(binding.root)

                viewModel.modeLiveData.observe(this@SetModeDialogFragment) { onModeChanged(it) }
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> setMode() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

    private fun setupListeners() {
        val checkListener = CompoundButton.OnCheckedChangeListener { _, _ ->
            if (isUpdatingUi) return@OnCheckedChangeListener
            viewModel.setMode(computeModeFromCheckboxes())
        }

        binding.ownerReadCheck.setOnCheckedChangeListener(checkListener)
        binding.ownerWriteCheck.setOnCheckedChangeListener(checkListener)
        binding.ownerExecCheck.setOnCheckedChangeListener(checkListener)

        binding.groupReadCheck.setOnCheckedChangeListener(checkListener)
        binding.groupWriteCheck.setOnCheckedChangeListener(checkListener)
        binding.groupExecCheck.setOnCheckedChangeListener(checkListener)

        binding.otherReadCheck.setOnCheckedChangeListener(checkListener)
        binding.otherWriteCheck.setOnCheckedChangeListener(checkListener)
        binding.otherExecCheck.setOnCheckedChangeListener(checkListener)

        binding.setUidCheck.setOnCheckedChangeListener(checkListener)
        binding.setGidCheck.setOnCheckedChangeListener(checkListener)
        binding.stickyCheck.setOnCheckedChangeListener(checkListener)

        binding.octalEdit.doAfterTextChanged { text ->
            if (isUpdatingUi) return@doAfterTextChanged
            val str = text?.toString()?.trim() ?: ""
            val octal = str.toIntOrNull(8)
            if (octal != null) {
                viewModel.setFromOctal(octal)
            }
        }
    }

    private fun computeModeFromCheckboxes(): Set<PosixFileModeBit> =
        enumSetOf<PosixFileModeBit>().apply {
            if (binding.ownerReadCheck.isChecked) this += PosixFileModeBit.OWNER_READ
            if (binding.ownerWriteCheck.isChecked) this += PosixFileModeBit.OWNER_WRITE
            if (binding.ownerExecCheck.isChecked) this += PosixFileModeBit.OWNER_EXECUTE

            if (binding.groupReadCheck.isChecked) this += PosixFileModeBit.GROUP_READ
            if (binding.groupWriteCheck.isChecked) this += PosixFileModeBit.GROUP_WRITE
            if (binding.groupExecCheck.isChecked) this += PosixFileModeBit.GROUP_EXECUTE

            if (binding.otherReadCheck.isChecked) this += PosixFileModeBit.OTHERS_READ
            if (binding.otherWriteCheck.isChecked) this += PosixFileModeBit.OTHERS_WRITE
            if (binding.otherExecCheck.isChecked) this += PosixFileModeBit.OTHERS_EXECUTE

            if (binding.setUidCheck.isChecked) this += PosixFileModeBit.SET_USER_ID
            if (binding.setGidCheck.isChecked) this += PosixFileModeBit.SET_GROUP_ID
            if (binding.stickyCheck.isChecked) this += PosixFileModeBit.STICKY
        }

    private fun onModeChanged(mode: Set<PosixFileModeBit>) {
        isUpdatingUi = true
        try {
            binding.ownerReadCheck.isChecked = PosixFileModeBit.OWNER_READ in mode
            binding.ownerWriteCheck.isChecked = PosixFileModeBit.OWNER_WRITE in mode
            binding.ownerExecCheck.isChecked = PosixFileModeBit.OWNER_EXECUTE in mode

            binding.groupReadCheck.isChecked = PosixFileModeBit.GROUP_READ in mode
            binding.groupWriteCheck.isChecked = PosixFileModeBit.GROUP_WRITE in mode
            binding.groupExecCheck.isChecked = PosixFileModeBit.GROUP_EXECUTE in mode

            binding.otherReadCheck.isChecked = PosixFileModeBit.OTHERS_READ in mode
            binding.otherWriteCheck.isChecked = PosixFileModeBit.OTHERS_WRITE in mode
            binding.otherExecCheck.isChecked = PosixFileModeBit.OTHERS_EXECUTE in mode

            binding.setUidCheck.isChecked = PosixFileModeBit.SET_USER_ID in mode
            binding.setGidCheck.isChecked = PosixFileModeBit.SET_GROUP_ID in mode
            binding.stickyCheck.isChecked = PosixFileModeBit.STICKY in mode

            binding.modeStringText.text = mode.toModeString()

            val modeInt = mode.toInt()
            val octalStr = if (modeInt >= 01000) String.format("%04o", modeInt) else String.format("%03o", modeInt)
            if (binding.octalEdit.text.toString() != octalStr) {
                val selection = binding.octalEdit.selectionStart
                binding.octalEdit.setText(octalStr)
                if (binding.octalEdit.hasFocus() && selection <= octalStr.length) {
                    binding.octalEdit.setSelection(selection)
                }
            }
        } finally {
            isUpdatingUi = false
        }
    }

    private fun setMode() {
        val mode = viewModel.mode
        val recursive = binding.recursiveCheck.isChecked
        if (!recursive) {
            if (mode == argsMode) {
                return
            }
        }
        val uppercaseX = binding.uppercaseXCheck.isChecked
        FileJobService.setMode(args.file.path, mode, recursive, uppercaseX, requireContext())
    }

    private val argsMode: Set<PosixFileModeBit>
        get() {
            val attributes = args.file.attributes as PosixFileAttributes
            return attributes.mode() ?: emptySet()
        }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            SetModeDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem) : ParcelableArgs
}
