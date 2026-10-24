/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.format.Formatter
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.TextEditorFragmentBinding
import me.zhanghai.android.files.ui.ThemedFastScroller
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.DataState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.addOnBackPressedCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.fadeInUnsafe
import me.zhanghai.android.files.util.fadeOutUnsafe
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.viewModels
import java.nio.charset.Charset

class TextEditorFragment : Fragment(), ConfirmReloadDialogFragment.Listener,
    ConfirmCloseDialogFragment.Listener {
    private val args by args<Args>()
    private lateinit var argsFile: Path

    private lateinit var binding: TextEditorFragmentBinding

    private lateinit var menuBinding: MenuBinding

    private val viewModel by viewModels { { TextEditorViewModel(argsFile) } }

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private var isSettingText = false

    private lateinit var undoManager: TextEditorUndoManager

    private var currentLanguage = SyntaxLanguage.PLAIN_TEXT

    private val syntaxHandler = Handler(Looper.getMainLooper())
    private val syntaxRunnable = Runnable {
        if (view != null && !isSettingText) {
            SyntaxHighlighter.highlight(binding.textEdit.text, currentLanguage)
        }
    }

    // Search and replace state
    private val searchMatches = mutableListOf<IntRange>()
    private var currentMatchIndex = -1
    private var isSearchCaseSensitive = false

    // Display options
    private var isWordWrap = true
    private var isReadOnly = false
    private var currentFontSizeSp = 14f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        lifecycleScope.launchWhenStarted {
            onBackPressedCallback = object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    if (binding.searchPanel.visibility == View.VISIBLE) {
                        closeSearchPanel()
                        return
                    }
                    ConfirmCloseDialogFragment.show(this@TextEditorFragment)
                }
            }
            launch {
                viewModel.isTextChanged.collect {
                    onBackPressedCallback.isEnabled = viewModel.isTextChanged.value
                }
            }
            addOnBackPressedCallback(onBackPressedCallback)

            launch { viewModel.encoding.collect { onEncodingChanged(it) } }
            launch { viewModel.textState.collect { onTextStateChanged(it) } }
            launch { viewModel.isTextChanged.collect { onIsTextChangedChanged(it) } }
            launch { viewModel.writeFileState.collect { onWriteFileStateChanged(it) } }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        TextEditorFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val argsFile = args.intent.extraPath
        if (argsFile == null) {
            finish()
            return
        }
        this.argsFile = argsFile

        // Auto-detect language
        currentLanguage = SyntaxLanguage.detectFromFileName(argsFile.fileName.toString())

        val activity = requireActivity() as AppCompatActivity
        activity.lifecycleScope.launchWhenCreated {
            activity.setSupportActionBar(binding.toolbar)
            activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }

        ThemedFastScroller.create(binding.scrollView)
        try {
            val monoTypeface = Typeface.createFromAsset(requireContext().assets, "Mono.ttf")
            binding.textEdit.typeface = monoTypeface
        } catch (e: Exception) {
            binding.textEdit.typeface = Typeface.MONOSPACE
        }

        // Setup Undo Manager
        undoManager = TextEditorUndoManager(binding.textEdit) { canUndo, canRedo ->
            updateUndoRedoMenuItems()
        }
        binding.textEdit.addTextChangedListener(undoManager)

        // Manually save and restore state in view model to avoid TransactionTooLargeException.
        binding.textEdit.isSaveEnabled = false
        val textEditSavedState = viewModel.removeEditTextSavedState()
        if (textEditSavedState != null) {
            binding.textEdit.onRestoreInstanceState(textEditSavedState)
        }

        binding.textEdit.doAfterTextChanged {
            if (isSettingText) {
                return@doAfterTextChanged
            }
            if (viewModel.textState.value !is DataState.Success) {
                return@doAfterTextChanged
            }
            viewModel.isTextChanged.value = true
            requestSyntaxHighlight()
            if (binding.searchPanel.visibility == View.VISIBLE) {
                performSearch(resetCurrent = false)
            }
        }

        setupSearchPanel()
        setupSymbolBar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        syntaxHandler.removeCallbacks(syntaxRunnable)
    }

    private fun requestSyntaxHighlight() {
        syntaxHandler.removeCallbacks(syntaxRunnable)
        syntaxHandler.postDelayed(syntaxRunnable, 350)
    }

    private fun setupSymbolBar() {
        val container = binding.symbolContainer
        container.removeAllViews()

        // TAB (4 spaces)
        addTextSymbolButton("TAB") {
            insertTextAtCursor("    ")
        }

        // Undo
        addImageSymbolButton(R.drawable.ic_undo_24dp, R.string.text_editor_undo) {
            undoManager.undo()
        }

        // Redo
        addImageSymbolButton(R.drawable.ic_redo_24dp, R.string.text_editor_redo) {
            undoManager.redo()
        }

        // Search toggle
        addImageSymbolButton(R.drawable.search_icon_control_normal_24dp, R.string.text_editor_search) {
            toggleSearchPanel()
        }

        // Common programming symbols
        val symbols = listOf(
            "{", "}", "(", ")", "[", "]", "<", ">",
            "\"", "'", ";", ":", "=", "+", "-", "*",
            "/", "\\", "_", "$", "&", "|", "!",
            ",", ".", "?", "#", "@"
        )
        for (sym in symbols) {
            addTextSymbolButton(sym) {
                insertTextAtCursor(sym)
            }
        }
    }

    private fun addTextSymbolButton(text: String, onClick: () -> Unit) {
        val btn = MaterialButton(
            requireContext(),
            null,
            androidx.appcompat.R.attr.borderlessButtonStyle
        ).apply {
            this.text = text
            minWidth = 0
            minimumWidth = 0
            setPadding(24, 0, 24, 0)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setOnClickListener { onClick() }
        }
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        binding.symbolContainer.addView(btn, params)
    }

    private fun addImageSymbolButton(resId: Int, contentDescriptionRes: Int, onClick: () -> Unit) {
        val btn = ImageButton(requireContext()).apply {
            setImageResource(resId)
            contentDescription = getString(contentDescriptionRes)
            setBackgroundResource(android.R.drawable.list_selector_background)
            setPadding(20, 0, 20, 0)
            setOnClickListener { onClick() }
        }
        val params = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        binding.symbolContainer.addView(btn, params)
    }

    private fun insertTextAtCursor(insert: String) {
        if (isReadOnly) return
        val start = binding.textEdit.selectionStart.coerceAtLeast(0)
        val end = binding.textEdit.selectionEnd.coerceAtLeast(0)
        val min = minOf(start, end)
        val max = maxOf(start, end)
        binding.textEdit.text?.replace(min, max, insert)
        binding.textEdit.setSelection(min + insert.length)
    }

    private fun setupSearchPanel() {
        binding.searchEdit.doAfterTextChanged {
            performSearch(resetCurrent = true)
        }
        binding.searchPrevBtn.setOnClickListener {
            prevSearchMatch()
        }
        binding.searchNextBtn.setOnClickListener {
            nextSearchMatch()
        }
        binding.searchCaseBtn.setOnClickListener {
            isSearchCaseSensitive = !isSearchCaseSensitive
            binding.searchCaseBtn.alpha = if (isSearchCaseSensitive) 1.0f else 0.5f
            performSearch(resetCurrent = true)
        }
        binding.searchCaseBtn.alpha = 0.5f

        binding.searchCloseBtn.setOnClickListener {
            closeSearchPanel()
        }

        binding.replaceBtn.setOnClickListener {
            replaceCurrentMatch()
        }
        binding.replaceAllBtn.setOnClickListener {
            replaceAllMatches()
        }
    }

    private fun toggleSearchPanel() {
        if (binding.searchPanel.visibility == View.VISIBLE) {
            closeSearchPanel()
        } else {
            openSearchPanel()
        }
    }

    private fun openSearchPanel() {
        binding.searchPanel.visibility = View.VISIBLE
        binding.searchEdit.requestFocus()
        performSearch(resetCurrent = true)
    }

    private fun closeSearchPanel() {
        binding.searchPanel.visibility = View.GONE
        clearSearchSpans()
        searchMatches.clear()
        currentMatchIndex = -1
        binding.searchCountText.setText(R.string.text_editor_no_matches)
    }

    private class SearchMatchSpan : BackgroundColorSpan(0x66FFEB3B.toInt())
    private class CurrentSearchMatchSpan : BackgroundColorSpan(0xFFFFA000.toInt())

    private fun performSearch(resetCurrent: Boolean) {
        val query = binding.searchEdit.text.toString()
        clearSearchSpans()
        searchMatches.clear()

        if (query.isEmpty()) {
            currentMatchIndex = -1
            binding.searchCountText.setText(R.string.text_editor_no_matches)
            return
        }

        val text = binding.textEdit.text.toString()
        var index = 0
        while (index < text.length) {
            val found = text.indexOf(query, index, ignoreCase = !isSearchCaseSensitive)
            if (found == -1) break
            searchMatches.add(found until (found + query.length))
            index = found + query.length.coerceAtLeast(1)
        }

        if (searchMatches.isEmpty()) {
            currentMatchIndex = -1
            binding.searchCountText.setText(R.string.text_editor_no_matches)
            return
        }

        if (resetCurrent || currentMatchIndex !in searchMatches.indices) {
            // Find match nearest to cursor
            val cursor = binding.textEdit.selectionStart
            val nearest = searchMatches.indexOfFirst { it.first >= cursor }
            currentMatchIndex = if (nearest != -1) nearest else 0
        }

        highlightSearchMatches()
        scrollToMatch(currentMatchIndex)
    }

    private fun highlightSearchMatches() {
        val editable = binding.textEdit.text ?: return
        clearSearchSpans()

        for (i in searchMatches.indices) {
            val range = searchMatches[i]
            val span = if (i == currentMatchIndex) {
                CurrentSearchMatchSpan()
            } else {
                SearchMatchSpan()
            }
            editable.setSpan(span, range.first, range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.searchCountText.text = getString(
            R.string.text_editor_match_count,
            currentMatchIndex + 1,
            searchMatches.size
        )
    }

    private fun clearSearchSpans() {
        val editable = binding.textEdit.text ?: return
        val searchSpans = editable.getSpans(0, editable.length, SearchMatchSpan::class.java)
        for (span in searchSpans) {
            editable.removeSpan(span)
        }
        val currentSpans = editable.getSpans(0, editable.length, CurrentSearchMatchSpan::class.java)
        for (span in currentSpans) {
            editable.removeSpan(span)
        }
    }

    private fun nextSearchMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
        highlightSearchMatches()
        scrollToMatch(currentMatchIndex)
    }

    private fun prevSearchMatch() {
        if (searchMatches.isEmpty()) return
        currentMatchIndex = if (currentMatchIndex - 1 < 0) searchMatches.size - 1 else currentMatchIndex - 1
        highlightSearchMatches()
        scrollToMatch(currentMatchIndex)
    }

    private fun scrollToMatch(index: Int) {
        if (index !in searchMatches.indices) return
        val range = searchMatches[index]
        binding.textEdit.setSelection(range.first, range.last + 1)

        val layout = binding.textEdit.layout ?: return
        val line = layout.getLineForOffset(range.first)
        val lineTop = layout.getLineTop(line)
        val scrollY = (lineTop - binding.scrollView.height / 3).coerceAtLeast(0)
        binding.scrollView.smoothScrollTo(0, scrollY)

        if (!isWordWrap) {
            val horizontalX = layout.getPrimaryHorizontal(range.first).toInt()
            val scrollX = (horizontalX - binding.horizontalScrollView.width / 3).coerceAtLeast(0)
            binding.horizontalScrollView.smoothScrollTo(scrollX, 0)
        }
    }

    private fun replaceCurrentMatch() {
        if (isReadOnly || currentMatchIndex !in searchMatches.indices) return
        val range = searchMatches[currentMatchIndex]
        val replacement = binding.replaceEdit.text.toString()

        binding.textEdit.text?.replace(range.first, range.last + 1, replacement)
        performSearch(resetCurrent = false)
    }

    private fun replaceAllMatches() {
        if (isReadOnly || searchMatches.isEmpty()) return
        val replacement = binding.replaceEdit.text.toString()
        val count = searchMatches.size

        // Replace backwards to maintain valid indices
        for (i in searchMatches.indices.reversed()) {
            val range = searchMatches[i]
            binding.textEdit.text?.replace(range.first, range.last + 1, replacement)
        }

        showToast(getString(R.string.text_editor_replaced_count, count))
        performSearch(resetCurrent = true)
    }

    private fun showSyntaxLanguageDialog() {
        val languages = SyntaxLanguage.entries.toTypedArray()
        val names = languages.map { it.displayName }.toTypedArray()
        val selectedIndex = languages.indexOf(currentLanguage)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.text_editor_syntax_title)
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                val selected = languages[which]
                if (selected != currentLanguage) {
                    currentLanguage = selected
                    SyntaxHighlighter.highlight(binding.textEdit.text, currentLanguage)
                    showToast(getString(R.string.text_editor_syntax_changed, selected.displayName))
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showGotoLineDialog() {
        val totalLines = binding.textEdit.lineCount.coerceAtLeast(1)
        val layout = binding.textEdit.layout
        val currentLine = if (layout != null) {
            layout.getLineForOffset(binding.textEdit.selectionStart) + 1
        } else 1

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.text_editor_goto_line_hint, totalLines)
            setText(currentLine.toString())
            selectAll()
            setPadding(48, 32, 48, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.text_editor_goto_line)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val targetLine = input.text.toString().toIntOrNull()
                if (targetLine != null && targetLine in 1..totalLines) {
                    val lineStart = layout?.getLineStart(targetLine - 1) ?: 0
                    binding.textEdit.setSelection(lineStart)
                    val lineTop = layout?.getLineTop(targetLine - 1) ?: 0
                    val scrollY = (lineTop - binding.scrollView.height / 3).coerceAtLeast(0)
                    binding.scrollView.smoothScrollTo(0, scrollY)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFileStatsDialog() {
        val fileName = viewModel.file.value.fileName.toString()
        val filePath = viewModel.file.value.toAbsolutePath().toString()
        val sizeBytes = viewModel.fileSize.value
        val formattedSize = "${Formatter.formatFileSize(requireContext(), sizeBytes)} ($sizeBytes bytes)"
        val encodingName = viewModel.encoding.value.displayName()
        val syntaxName = currentLanguage.displayName
        val text = binding.textEdit.text.toString()
        val lineCount = binding.textEdit.lineCount
        val charCount = text.length
        val wordCount = if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size

        val message = """
            ${getString(R.string.file_properties_basic_name)}: $fileName
            Ruta: $filePath
            ${getString(R.string.file_properties_basic_size)}: $formattedSize
            ${getString(R.string.text_editor_encoding)}: $encodingName
            ${getString(R.string.text_editor_syntax)}: $syntaxName
            ${getString(R.string.text_editor_word_wrap)}: ${if (isWordWrap) "Activado" else "Desactivado"}
            
            Líneas: $lineCount
            Palabras: $wordCount
            Caracteres: $charCount
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.text_editor_file_stats)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setWordWrap(wrap: Boolean) {
        isWordWrap = wrap
        binding.textEdit.setHorizontallyScrolling(!wrap)
        val params = binding.textEdit.layoutParams
        params.width = if (wrap) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        binding.textEdit.layoutParams = params
        binding.textEdit.requestLayout()
    }

    private fun setReadOnly(readOnly: Boolean) {
        isReadOnly = readOnly
        binding.textEdit.isFocusable = !readOnly
        binding.textEdit.isFocusableInTouchMode = !readOnly
        binding.textEdit.isCursorVisible = !readOnly
        binding.symbolBar.visibility = if (readOnly) View.GONE else View.VISIBLE
    }

    private fun setFontSize(sizeSp: Float) {
        currentFontSizeSp = sizeSp.coerceIn(8f, 32f)
        binding.textEdit.textSize = currentFontSizeSp
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.setEditTextSavedState(binding.textEdit.onSaveInstanceState())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menuBinding = MenuBinding.inflate(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        updateSaveMenuItem()
        updateEncodingMenuItems()
        updateUndoRedoMenuItems()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_save -> {
                save()
                true
            }
            R.id.action_undo -> {
                undoManager.undo()
                true
            }
            R.id.action_redo -> {
                undoManager.redo()
                true
            }
            R.id.action_search -> {
                toggleSearchPanel()
                true
            }
            R.id.action_syntax -> {
                showSyntaxLanguageDialog()
                true
            }
            R.id.action_goto_line -> {
                showGotoLineDialog()
                true
            }
            R.id.action_word_wrap -> {
                item.isChecked = !item.isChecked
                setWordWrap(item.isChecked)
                true
            }
            R.id.action_read_only -> {
                item.isChecked = !item.isChecked
                setReadOnly(item.isChecked)
                true
            }
            R.id.action_font_size_increase -> {
                setFontSize(currentFontSizeSp + 2f)
                true
            }
            R.id.action_font_size_decrease -> {
                setFontSize(currentFontSizeSp - 2f)
                true
            }
            R.id.action_font_size_reset -> {
                setFontSize(14f)
                true
            }
            R.id.action_file_stats -> {
                showFileStatsDialog()
                true
            }
            R.id.action_reload -> {
                onReload()
                true
            }
            Menu.FIRST -> {
                viewModel.encoding.value = Charset.forName(item.titleCondensed!!.toString())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    fun onSupportNavigateUp(): Boolean {
        if (binding.searchPanel.visibility == View.VISIBLE) {
            closeSearchPanel()
            return true
        }
        if (onBackPressedCallback.isEnabled) {
            onBackPressedCallback.handleOnBackPressed()
            return true
        }
        return false
    }

    override fun finish() {
        requireActivity().finish()
    }

    private fun onEncodingChanged(encoding: Charset) {
        updateEncodingMenuItems()
    }

    private fun updateEncodingMenuItems() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val charsetName = viewModel.encoding.value.name()
        val charsetItem = menuBinding.encodingSubMenu.children
            .find { it.titleCondensed == charsetName }
        charsetItem?.isChecked = true
    }

    private fun updateUndoRedoMenuItems() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.undoItem.isEnabled = undoManager.canUndo
        menuBinding.redoItem.isEnabled = undoManager.canRedo
    }

    private fun onTextStateChanged(state: DataState<String>) {
        updateTitle()
        when (state) {
            is DataState.Loading -> {
                binding.progress.fadeInUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeOutUnsafe()
            }
            is DataState.Success -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeInUnsafe()
                if (!viewModel.isTextChanged.value) {
                    setText(state.data)
                    SyntaxHighlighter.highlight(binding.textEdit.text, currentLanguage)
                    undoManager.clear()
                    checkLargeFileSize()
                }
            }
            is DataState.Error -> {
                state.throwable.printStackTrace()
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeInUnsafe()
                binding.errorText.text = state.throwable.toString()
                binding.textEdit.fadeOutUnsafe()
            }
        }
    }

    private var hasWarnedLargeFile = false
    private fun checkLargeFileSize() {
        val size = viewModel.fileSize.value
        if (size > 3 * 1024 * 1024L && !hasWarnedLargeFile) {
            hasWarnedLargeFile = true
            showToast(getString(R.string.text_editor_large_file_warning, Formatter.formatFileSize(requireContext(), size)))
        }
    }

    private fun setText(text: String?) {
        if (binding.textEdit.text.toString() != text) {
            isSettingText = true
            binding.textEdit.setText(text)
            isSettingText = false
        }
        viewModel.isTextChanged.value = false
    }

    private fun onIsTextChangedChanged(changed: Boolean) {
        updateTitle()
    }

    private fun updateTitle() {
        val fileName = viewModel.file.value.fileName.toString()
        val changed = viewModel.isTextChanged.value
        requireActivity().title = getString(
            if (changed) {
                R.string.text_editor_title_changed_format
            } else {
                R.string.text_editor_title_format
            }, fileName
        )
    }

    private fun onReload() {
        if (viewModel.isTextChanged.value) {
            ConfirmReloadDialogFragment.show(this)
        } else {
            reload()
        }
    }

    override fun reload() {
        viewModel.isTextChanged.value = false
        closeSearchPanel()
        viewModel.reload()
    }

    private fun save() {
        val text = binding.textEdit.text.toString()
        viewModel.writeFile(argsFile, text, requireContext())
    }

    private fun onWriteFileStateChanged(state: ActionState<Pair<Path, String>, Unit>) {
        when (state) {
            is ActionState.Ready, is ActionState.Running -> updateSaveMenuItem()
            is ActionState.Success -> {
                showToast(R.string.text_editor_save_success)
                viewModel.finishWritingFile()
                viewModel.isTextChanged.value = false
            }
            is ActionState.Error -> viewModel.finishWritingFile()
        }
    }

    private fun updateSaveMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.saveItem.isEnabled = viewModel.writeFileState.value.isReady
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    private class MenuBinding private constructor(
        val menu: Menu,
        val saveItem: MenuItem,
        val undoItem: MenuItem,
        val redoItem: MenuItem,
        val searchItem: MenuItem,
        val syntaxItem: MenuItem,
        val wordWrapItem: MenuItem,
        val readOnlyItem: MenuItem,
        val encodingSubMenu: SubMenu
    ) {
        companion object {
            fun inflate(menu: Menu, inflater: MenuInflater): MenuBinding {
                inflater.inflate(R.menu.text_editor, menu)
                val encodingSubMenu = menu.findItem(R.id.action_encoding).subMenu!!
                for ((charsetName, charset) in Charset.availableCharsets()) {
                    encodingSubMenu.add(Menu.NONE, Menu.FIRST, Menu.NONE, charset.displayName())
                        .titleCondensed = charsetName
                }
                encodingSubMenu.setGroupCheckable(Menu.NONE, true, true)
                return MenuBinding(
                    menu = menu,
                    saveItem = menu.findItem(R.id.action_save),
                    undoItem = menu.findItem(R.id.action_undo),
                    redoItem = menu.findItem(R.id.action_redo),
                    searchItem = menu.findItem(R.id.action_search),
                    syntaxItem = menu.findItem(R.id.action_syntax),
                    wordWrapItem = menu.findItem(R.id.action_word_wrap),
                    readOnlyItem = menu.findItem(R.id.action_read_only),
                    encodingSubMenu = encodingSubMenu
                )
            }
        }
    }
}
