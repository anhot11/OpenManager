/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.hex

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.AppActivity
import me.zhanghai.android.files.databinding.ActivityHexEditorBinding
import me.zhanghai.android.files.databinding.DialogHexEditByteBinding
import me.zhanghai.android.files.databinding.DialogHexFillBinding
import me.zhanghai.android.files.databinding.DialogHexGotoBinding
import me.zhanghai.android.files.databinding.DialogHexInsertBinding
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.showToast
import java.nio.charset.Charset
import java.security.MessageDigest

class HexEditorActivity : AppActivity() {

    private lateinit var binding: ActivityHexEditorBinding
    private lateinit var path: Path

    private var buffer: HexBuffer = HexBuffer(ByteArray(0))
    private lateinit var adapter: HexRowAdapter
    private var monoTypeface: Typeface = Typeface.MONOSPACE

    private var currentOffset: Int = 0
    private var isReadOnly: Boolean = false
    private var currentCharset: Charset = Charsets.UTF_8

    // Search state
    private var isSearchHexMode = true
    private var lastSearchQuery = ""
    private var lastMatchOffset = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHexEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        path = intent.extraPath ?: run {
            finish()
            return
        }

        try {
            monoTypeface = Typeface.createFromAsset(assets, "Mono.ttf")
        } catch (e: Exception) {
            monoTypeface = Typeface.MONOSPACE
        }

        setupToolbar()
        setupInspectorChips()
        setupSearchPanel()
        setupRecyclerView()
        loadFileContent()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.searchBarContainer.visibility == View.VISIBLE) {
                    closeSearch()
                    return
                }
                if (buffer.isModified) {
                    showUnsavedChangesDialog()
                } else {
                    finish()
                }
            }
        })
    }

    private fun setupToolbar() {
        binding.toolbar.title = path.fileName.toString()
        binding.toolbar.subtitle = getString(R.string.hex_editor_bytes_count, 0)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = HexRowAdapter(buffer, monoTypeface) { clickedOffset ->
            selectOffset(clickedOffset)
        }
        binding.rvHex.layoutManager = LinearLayoutManager(this)
        binding.rvHex.adapter = adapter
    }

    private fun setupInspectorChips() {
        binding.cardHex.tvCardLabel.text = "HEX"
        binding.cardDec.tvCardLabel.text = "DEC (U8)"
        binding.cardInt8.tvCardLabel.text = "INT8"
        binding.cardInt16.tvCardLabel.text = "INT16 (LE)"
        binding.cardInt32.tvCardLabel.text = "INT32 (LE)"
        binding.cardChar.tvCardLabel.text = "ASCII"
        binding.cardBin.tvCardLabel.text = "BINARIO"
        binding.cardFloat.tvCardLabel.text = "FLOAT32"

        val copyListener = View.OnClickListener { v ->
            val text = when (v) {
                binding.cardHex.root -> binding.cardHex.tvCardValue.text
                binding.cardDec.root -> binding.cardDec.tvCardValue.text
                binding.cardInt8.root -> binding.cardInt8.tvCardValue.text
                binding.cardInt16.root -> binding.cardInt16.tvCardValue.text
                binding.cardInt32.root -> binding.cardInt32.tvCardValue.text
                binding.cardChar.root -> binding.cardChar.tvCardValue.text
                binding.cardBin.root -> binding.cardBin.tvCardValue.text
                binding.cardFloat.root -> binding.cardFloat.tvCardValue.text
                else -> ""
            }
            if (text.isNotEmpty() && text != "-") {
                copyToClipboard(text.toString())
            }
        }

        binding.cardHex.root.setOnClickListener(copyListener)
        binding.cardDec.root.setOnClickListener(copyListener)
        binding.cardInt8.root.setOnClickListener(copyListener)
        binding.cardInt16.root.setOnClickListener(copyListener)
        binding.cardInt32.root.setOnClickListener(copyListener)
        binding.cardChar.root.setOnClickListener(copyListener)
        binding.cardBin.root.setOnClickListener(copyListener)
        binding.cardFloat.root.setOnClickListener(copyListener)

        binding.btnEditByte.setOnClickListener {
            if (isReadOnly) {
                showToast(R.string.hex_editor_readonly)
            } else {
                showEditByteDialog(currentOffset)
            }
        }

        binding.btnCopyHex.setOnClickListener {
            val hex = String.format("%02X", buffer.getByte(currentOffset).toInt() and 0xFF)
            copyToClipboard(hex)
        }

        binding.btnCopyAscii.setOnClickListener {
            val b = buffer.getByte(currentOffset).toInt() and 0xFF
            val ch = if (b in 32..126) b.toChar().toString() else "."
            copyToClipboard(ch)
        }
    }

    private fun setupSearchPanel() {
        binding.btnSearchMode.setOnClickListener {
            isSearchHexMode = !isSearchHexMode
            binding.btnSearchMode.text = if (isSearchHexMode) "HEX" else "TXT"
            binding.cbMatchCase.visibility = if (isSearchHexMode) View.GONE else View.VISIBLE
            binding.etSearch.hint = if (isSearchHexMode) {
                getString(R.string.hex_editor_search_hint)
            } else {
                "Buscar texto…"
            }
        }

        binding.btnSearchNext.setOnClickListener { performSearch(forward = true) }
        binding.btnSearchPrev.setOnClickListener { performSearch(forward = false) }

        binding.btnToggleReplace.setOnClickListener {
            val isVisible = binding.replaceContainer.visibility == View.VISIBLE
            binding.replaceContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        binding.btnCloseSearch.setOnClickListener { closeSearch() }

        binding.btnReplace.setOnClickListener { performReplaceCurrent() }
        binding.btnReplaceAll.setOnClickListener { performReplaceAll() }
    }

    private fun loadFileContent() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val rawBytes = withContext(Dispatchers.IO) {
                try {
                    path.newInputStream().use { stream -> stream.readBytes() }
                } catch (e: Exception) {
                    null
                }
            }
            binding.progressBar.visibility = View.GONE

            if (rawBytes == null) {
                showToast("Error al leer el archivo")
                finish()
                return@launch
            }

            buffer = HexBuffer(rawBytes)
            adapter.updateBuffer(buffer)
            selectOffset(0)
            updateToolbarInfo()
        }
    }

    private fun selectOffset(offset: Int) {
        if (buffer.size == 0) return
        currentOffset = offset.coerceIn(0, buffer.size - 1)
        adapter.selectedOffset = currentOffset

        val data = HexInspector.inspect(buffer, currentOffset)
        binding.cardHex.tvCardValue.text = data.hexStr
        binding.cardDec.tvCardValue.text = data.uint8Str
        binding.cardInt8.tvCardValue.text = data.int8Str
        binding.cardInt16.tvCardValue.text = data.int16LEStr
        binding.cardInt32.tvCardValue.text = data.int32LEStr
        binding.cardChar.tvCardValue.text = data.charStr
        binding.cardBin.tvCardValue.text = data.binStr
        binding.cardFloat.tvCardValue.text = data.float32LEStr

        val isMod = buffer.modifiedIndices.contains(currentOffset)
        val modLabel = if (isMod) " • [Modificado]" else ""
        binding.tvSelectionInfo.text = String.format("Offset: 0x%08X (%d)%s", currentOffset, currentOffset, modLabel)
        updateToolbarInfo()
    }

    private fun updateToolbarInfo() {
        val sizeFormatted = formatFileSize(buffer.size.toLong())
        val dirtyMark = if (buffer.isModified) "* " else ""
        binding.toolbar.title = "$dirtyMark${path.fileName}"
        binding.toolbar.subtitle = String.format("%s • Offset: 0x%08X", sizeFormatted, currentOffset)
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
        }
    }

    private fun performSearch(forward: Boolean) {
        val query = binding.etSearch.text.toString().trim()
        if (query.isEmpty()) return

        val searchStart = if (forward) {
            if (lastSearchQuery == query && lastMatchOffset != -1) lastMatchOffset + 1 else currentOffset
        } else {
            if (lastSearchQuery == query && lastMatchOffset != -1) lastMatchOffset - 1 else currentOffset
        }

        lastSearchQuery = query

        val matchOffset: Int
        val matchLength: Int

        if (isSearchHexMode) {
            val hexPattern = parseHexPattern(query)
            if (hexPattern == null || hexPattern.isEmpty()) {
                binding.tvSearchStatus.text = getString(R.string.hex_editor_invalid_hex)
                return
            }
            matchOffset = buffer.searchHex(hexPattern, searchStart, forward)
            matchLength = hexPattern.size
        } else {
            val ignoreCase = !binding.cbMatchCase.isChecked
            matchOffset = buffer.searchText(query, searchStart, forward, ignoreCase, currentCharset)
            matchLength = query.toByteArray(currentCharset).size
        }

        if (matchOffset != -1) {
            lastMatchOffset = matchOffset
            adapter.highlightRange = matchOffset until (matchOffset + matchLength)
            selectOffset(matchOffset)
            binding.rvHex.scrollToPosition(matchOffset / 16)
            binding.tvSearchStatus.text = getString(R.string.hex_editor_match_status, matchOffset)
        } else {
            binding.tvSearchStatus.text = getString(R.string.hex_editor_not_found)
        }
    }

    private fun performReplaceCurrent() {
        if (isReadOnly) {
            showToast(R.string.hex_editor_readonly)
            return
        }
        val targetQuery = binding.etSearch.text.toString().trim()
        val replaceQuery = binding.etReplace.text.toString().trim()
        if (targetQuery.isEmpty() || lastMatchOffset == -1) return

        val targetBytes = if (isSearchHexMode) parseHexPattern(targetQuery) else targetQuery.toByteArray(currentCharset)
        val replaceBytes = if (isSearchHexMode) parseHexPattern(replaceQuery) else replaceQuery.toByteArray(currentCharset)

        if (targetBytes == null || replaceBytes == null) {
            showToast(R.string.hex_editor_invalid_hex)
            return
        }

        buffer.replace(lastMatchOffset, targetBytes.size, replaceBytes)
        adapter.updateBuffer(buffer)
        selectOffset(lastMatchOffset)
        performSearch(forward = true)
    }

    private fun performReplaceAll() {
        if (isReadOnly) {
            showToast(R.string.hex_editor_readonly)
            return
        }
        val targetQuery = binding.etSearch.text.toString().trim()
        val replaceQuery = binding.etReplace.text.toString().trim()
        if (targetQuery.isEmpty()) return

        val targetBytes = if (isSearchHexMode) parseHexPattern(targetQuery) else targetQuery.toByteArray(currentCharset)
        val replaceBytes = if (isSearchHexMode) parseHexPattern(replaceQuery) else replaceQuery.toByteArray(currentCharset)

        if (targetBytes == null || replaceBytes == null) {
            showToast(R.string.hex_editor_invalid_hex)
            return
        }

        val count = buffer.replaceAllHex(targetBytes, replaceBytes)
        adapter.updateBuffer(buffer)
        selectOffset(currentOffset)
        showToast("Reemplazos realizados: $count")
        binding.tvSearchStatus.text = "Reemplazos: $count"
    }

    private fun closeSearch() {
        binding.searchBarContainer.visibility = View.GONE
        adapter.highlightRange = null
        lastMatchOffset = -1
    }

    private fun showEditByteDialog(offset: Int) {
        val dialogBinding = DialogHexEditByteBinding.inflate(layoutInflater)
        dialogBinding.tvTargetOffset.text = String.format("Offset: 0x%08X (%d)", offset, offset)

        val curByte = buffer.getByte(offset).toInt() and 0xFF
        dialogBinding.etValue.setText(String.format("%02X", curByte))
        dialogBinding.etValue.selectAll()

        dialogBinding.rgInputType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbHex) {
                dialogBinding.tilValue.hint = getString(R.string.hex_editor_input_hex_hint)
            } else {
                dialogBinding.tilValue.hint = getString(R.string.hex_editor_input_text_hint)
            }
        }

        val updatePreview: () -> Unit = {
            val text = dialogBinding.etValue.text.toString()
            if (dialogBinding.rbHex.isChecked) {
                val bytes = parseHexPattern(text)
                if (bytes != null) {
                    val ascii = bytes.map { b ->
                        val u = b.toInt() and 0xFF
                        if (u in 32..126) u.toChar() else '.'
                    }.joinToString("")
                    dialogBinding.tvPreview.text = "Bytes: ${bytes.size}  |  Texto: $ascii"
                } else {
                    dialogBinding.tvPreview.text = "Hex inválido"
                }
            } else {
                val bytes = text.toByteArray(currentCharset)
                val hexStr = bytes.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
                dialogBinding.tvPreview.text = "Bytes: ${bytes.size}  |  Hex: $hexStr"
            }
        }

        dialogBinding.etValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hex_editor_edit_byte)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = dialogBinding.etValue.text.toString()
                val bytes = if (dialogBinding.rbHex.isChecked) {
                    parseHexPattern(text)
                } else {
                    text.toByteArray(currentCharset)
                }

                if (bytes == null || bytes.isEmpty()) {
                    showToast(R.string.hex_editor_invalid_hex)
                    return@setPositiveButton
                }

                if (dialogBinding.rbOverwrite.isChecked) {
                    buffer.modifyBytes(offset, bytes)
                } else {
                    buffer.insertBytes(offset, bytes)
                }

                adapter.updateBuffer(buffer)
                selectOffset(offset)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showGotoOffsetDialog() {
        val dialogBinding = DialogHexGotoBinding.inflate(layoutInflater)
        dialogBinding.etOffset.setText(String.format("0x%X", currentOffset))
        dialogBinding.etOffset.selectAll()

        dialogBinding.btnQuickStart.setOnClickListener {
            dialogBinding.etOffset.setText("0x0")
        }
        dialogBinding.btnQuickEnd.setOnClickListener {
            dialogBinding.etOffset.setText(String.format("0x%X", (buffer.size - 1).coerceAtLeast(0)))
        }
        dialogBinding.btnQuickMinus1K.setOnClickListener {
            val cur = parseOffset(dialogBinding.etOffset.text.toString()) ?: currentOffset
            val next = (cur - 1024).coerceAtLeast(0)
            dialogBinding.etOffset.setText(String.format("0x%X", next))
        }
        dialogBinding.btnQuickPlus1K.setOnClickListener {
            val cur = parseOffset(dialogBinding.etOffset.text.toString()) ?: currentOffset
            val next = (cur + 1024).coerceAtMost((buffer.size - 1).coerceAtLeast(0))
            dialogBinding.etOffset.setText(String.format("0x%X", next))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hex_editor_goto_offset)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val target = parseOffset(dialogBinding.etOffset.text.toString())
                if (target == null || target < 0 || target >= buffer.size) {
                    showToast(R.string.hex_editor_invalid_offset)
                    return@setPositiveButton
                }
                selectOffset(target)
                binding.rvHex.scrollToPosition(target / 16)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showInsertBytesDialog() {
        if (isReadOnly) {
            showToast(R.string.hex_editor_readonly)
            return
        }
        val dialogBinding = DialogHexInsertBinding.inflate(layoutInflater)
        dialogBinding.etOffset.setText(String.format("0x%X", currentOffset))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hex_editor_insert_bytes)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val off = parseOffset(dialogBinding.etOffset.text.toString()) ?: currentOffset
                val count = dialogBinding.etCount.text.toString().toIntOrNull() ?: 1
                val fillByte = parseByte(dialogBinding.etFillByte.text.toString()) ?: 0

                val newBytes = ByteArray(count) { fillByte }
                buffer.insertBytes(off, newBytes)
                adapter.updateBuffer(buffer)
                selectOffset(off)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteBytesDialog() {
        if (isReadOnly) {
            showToast(R.string.hex_editor_readonly)
            return
        }
        val input = android.widget.EditText(this).apply {
            hint = "Cantidad de bytes a eliminar"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hex_editor_delete_bytes)
            .setMessage(String.format("Eliminar bytes a partir del offset 0x%08X", currentOffset))
            .setView(input)
            .setPositiveButton(R.string.delete) { _, _ ->
                val count = input.text.toString().toIntOrNull() ?: 1
                buffer.deleteBytes(currentOffset, count)
                adapter.updateBuffer(buffer)
                selectOffset(currentOffset)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFillRangeDialog() {
        if (isReadOnly) {
            showToast(R.string.hex_editor_readonly)
            return
        }
        val dialogBinding = DialogHexFillBinding.inflate(layoutInflater)
        dialogBinding.etStartOffset.setText(String.format("0x%X", currentOffset))
        dialogBinding.etEndOffset.setText(String.format("0x%X", (currentOffset + 15).coerceAtMost(buffer.size - 1)))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hex_editor_fill_range)
            .setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val start = parseOffset(dialogBinding.etStartOffset.text.toString()) ?: currentOffset
                val end = parseOffset(dialogBinding.etEndOffset.text.toString()) ?: currentOffset
                val fillByte = parseByte(dialogBinding.etFillByte.text.toString()) ?: 0

                buffer.fillRange(start, end, fillByte)
                adapter.updateBuffer(buffer)
                selectOffset(start)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFileInfoDialog() {
        lifecycleScope.launch {
            val md5: String
            val sha1: String
            val sha256: String
            withContext(Dispatchers.Default) {
                val bytes = buffer.toByteArray()
                md5 = hashBytes(bytes, "MD5")
                sha1 = hashBytes(bytes, "SHA-1")
                sha256 = hashBytes(bytes, "SHA-256")
            }

            val msg = StringBuilder()
                .append("Archivo: ").append(path.fileName).append("\n")
                .append("Tamaño: ").append(buffer.size).append(" bytes (").append(formatFileSize(buffer.size.toLong())).append(")\n\n")
                .append("MD5:\n").append(md5).append("\n\n")
                .append("SHA-1:\n").append(sha1).append("\n\n")
                .append("SHA-256:\n").append(sha256)
                .toString()

            MaterialAlertDialogBuilder(this@HexEditorActivity)
                .setTitle(R.string.hex_editor_file_info)
                .setMessage(msg)
                .setPositiveButton("Copiar Hashes") { _, _ ->
                    copyToClipboard(msg)
                }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    private fun showEncodingDialog() {
        val encodings = arrayOf("UTF-8", "ASCII", "ISO-8859-1", "UTF-16LE", "UTF-16BE", "GBK")
        val currentIdx = encodings.indexOfFirst { it.equals(currentCharset.name(), ignoreCase = true) }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hex_editor_encoding)
            .setSingleChoiceItems(encodings, currentIdx) { dialog, which ->
                try {
                    currentCharset = Charset.forName(encodings[which])
                    showToast("Codificación: ${encodings[which]}")
                } catch (e: Exception) {
                    showToast("Codificación no soportada")
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveFile() {
        if (!buffer.isModified) {
            showToast(R.string.hex_editor_saved_success)
            return
        }

        val content = buffer.toByteArray()
        FileJobService.write(path, content, this) { successful ->
            if (successful) {
                buffer.markSaved()
                adapter.notifyDataSetChanged()
                updateToolbarInfo()
                showToast(R.string.hex_editor_saved_success)
            } else {
                showToast(getString(R.string.hex_editor_save_failed, "I/O error"))
            }
        }
    }

    private fun showUnsavedChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.hex_editor_title)
            .setMessage(R.string.hex_editor_unsaved_changes)
            .setPositiveButton(R.string.save) { _, _ ->
                saveFile()
                finish()
            }
            .setNegativeButton("Descartar") { _, _ ->
                finish()
            }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.hex_editor_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_undo)?.isEnabled = buffer.canUndo && !isReadOnly
        menu.findItem(R.id.action_redo)?.isEnabled = buffer.canRedo && !isReadOnly
        menu.findItem(R.id.action_save)?.isEnabled = buffer.isModified && !isReadOnly
        menu.findItem(R.id.action_readonly_toggle)?.isChecked = isReadOnly
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_undo -> {
                val restoredOffset = buffer.undo()
                if (restoredOffset != -1) {
                    adapter.updateBuffer(buffer)
                    selectOffset(restoredOffset)
                }
                invalidateOptionsMenu()
                true
            }
            R.id.action_redo -> {
                val restoredOffset = buffer.redo()
                if (restoredOffset != -1) {
                    adapter.updateBuffer(buffer)
                    selectOffset(restoredOffset)
                }
                invalidateOptionsMenu()
                true
            }
            R.id.action_search -> {
                binding.searchBarContainer.visibility = View.VISIBLE
                binding.etSearch.requestFocus()
                true
            }
            R.id.action_save -> {
                saveFile()
                true
            }
            R.id.action_goto -> {
                showGotoOffsetDialog()
                true
            }
            R.id.action_edit -> {
                showEditByteDialog(currentOffset)
                true
            }
            R.id.action_insert -> {
                showInsertBytesDialog()
                true
            }
            R.id.action_delete -> {
                showDeleteBytesDialog()
                true
            }
            R.id.action_fill -> {
                showFillRangeDialog()
                true
            }
            R.id.action_encoding -> {
                showEncodingDialog()
                true
            }
            R.id.action_file_info -> {
                showFileInfoDialog()
                true
            }
            R.id.action_readonly_toggle -> {
                isReadOnly = !isReadOnly
                item.isChecked = isReadOnly
                showToast(if (isReadOnly) R.string.hex_editor_readonly else R.string.hex_editor_edit_mode)
                invalidateOptionsMenu()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun parseHexPattern(hexStr: String): ByteArray? {
        val clean = hexStr.replace("\\s+".toRegex(), "").replace("0x", "", ignoreCase = true)
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return try {
            val len = clean.length / 2
            val result = ByteArray(len)
            for (i in 0 until len) {
                result[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOffset(str: String): Int? {
        val clean = str.trim()
        return try {
            if (clean.startsWith("0x", ignoreCase = true)) {
                clean.substring(2).toInt(16)
            } else {
                clean.toInt()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseByte(str: String): Byte? {
        val clean = str.trim().replace("0x", "", ignoreCase = true)
        return try {
            clean.toInt(16).toByte()
        } catch (e: Exception) {
            null
        }
    }

    private fun hashBytes(bytes: ByteArray, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val hash = digest.digest(bytes)
            hash.joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) {
            "Error"
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService<ClipboardManager>()
        clipboard?.setPrimaryClip(ClipData.newPlainText("hex", text))
        showToast(R.string.hex_editor_copied_toast)
    }

    companion object {
        fun start(context: Context, path: Path) {
            val intent = Intent(context, HexEditorActivity::class.java).apply {
                extraPath = path
            }
            context.startActivity(intent)
        }
    }
}
