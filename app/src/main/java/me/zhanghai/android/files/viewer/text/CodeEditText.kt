/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Editable
import android.text.Selection
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText
import kotlin.math.max
import kotlin.math.min

/**
 * Modern, high-performance code editor with current-line highlight,
 * bracket matching, zero-overhead onDraw search highlighting,
 * smart auto-indentation, and pair symbol completion.
 */
class CodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    val textSizePx: Float get() = textSize

    // Highlighting Paints
    private val currentLinePaint = Paint().apply {
        color = COLOR_CURRENT_LINE
        style = Paint.Style.FILL
    }

    private val searchMatchPaint = Paint().apply {
        color = COLOR_SEARCH_MATCH
        style = Paint.Style.FILL
    }

    private val currentSearchMatchPaint = Paint().apply {
        color = COLOR_CURRENT_SEARCH_MATCH
        style = Paint.Style.FILL
    }

    private val currentSearchBorderPaint = Paint().apply {
        color = COLOR_CURRENT_SEARCH_BORDER
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }

    private val bracketMatchPaint = Paint().apply {
        color = COLOR_BRACKET_BG
        style = Paint.Style.FILL
    }

    private val bracketBorderPaint = Paint().apply {
        color = COLOR_BRACKET_BORDER
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }

    // Bracket matching state
    private var matchingBracketPair: Pair<Int, Int>? = null

    // Search matches state for zero-overhead onDraw rendering
    var searchMatches: List<IntRange> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var currentMatchIndex: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    // Callbacks
    var onCursorPositionChanged: ((line: Int, col: Int, totalLines: Int) -> Unit)? = null
    var onLineCountChanged: (() -> Unit)? = null

    init {
        // Monospace font and comfortable code line spacing
        typeface = Typeface.MONOSPACE
        setLineSpacing(0f, 1.2f)
        setBackgroundColor(0) // Transparent background
    }

    override fun onPreDraw(): Boolean = true

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        updateBracketMatching(selStart)
        notifyCursorChanged(selStart)
        invalidate() // Refresh current line and bracket highlights
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        onLineCountChanged?.invoke()
    }

    private fun notifyCursorChanged(cursorPos: Int) {
        val layout = layout ?: return
        val pos = cursorPos.coerceIn(0, (text?.length ?: 0))
        val physicalLine = layout.getLineForOffset(pos)
        val lineStart = layout.getLineStart(physicalLine)
        val col = pos - lineStart + 1

        val textStr = text ?: ""
        var logicalLine = 1
        for (i in 0 until min(pos, textStr.length)) {
            if (textStr[i] == '\n') logicalLine++
        }

        var totalLogical = 1
        for (i in textStr.indices) {
            if (textStr[i] == '\n') totalLogical++
        }

        onCursorPositionChanged?.invoke(logicalLine, col, totalLogical)
    }

    private fun updateBracketMatching(cursorPos: Int) {
        val text = text ?: return
        if (text.isEmpty()) {
            matchingBracketPair = null
            return
        }

        val pos = cursorPos.coerceIn(0, text.length)
        var bracketPos = -1
        var bracketChar = ' '

        // Check character right before or right after cursor
        if (pos > 0 && isBracket(text[pos - 1])) {
            bracketPos = pos - 1
            bracketChar = text[pos - 1]
        } else if (pos < text.length && isBracket(text[pos])) {
            bracketPos = pos
            bracketChar = text[pos]
        }

        if (bracketPos == -1) {
            matchingBracketPair = null
            return
        }

        val partnerPos = findPartnerBracket(text, bracketPos, bracketChar)
        matchingBracketPair = if (partnerPos != -1) {
            Pair(bracketPos, partnerPos)
        } else {
            null
        }
    }

    private fun isBracket(c: Char): Boolean =
        c == '(' || c == ')' || c == '{' || c == '}' || c == '[' || c == ']'

    private fun findPartnerBracket(text: CharSequence, pos: Int, openBracket: Char): Int {
        val isOpen = openBracket == '(' || openBracket == '{' || openBracket == '['
        val partner = when (openBracket) {
            '(' -> ')'
            ')' -> '('
            '{' -> '}'
            '}' -> '{'
            '[' -> ']'
            ']' -> '['
            else -> return -1
        }

        val dir = if (isOpen) 1 else -1
        var depth = 0
        var i = pos

        while (i in text.indices) {
            val c = text[i]
            if (c == openBracket) {
                depth++
            } else if (c == partner) {
                depth--
                if (depth == 0) return i
            }
            i += dir
        }
        return -1
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layout
        if (layout != null) {
            val totalPadTop = totalPaddingTop
            val totalPadLeft = totalPaddingLeft

            // 1. Draw Current Line Highlight
            if (isFocused && selectionStart >= 0) {
                val cursorLine = layout.getLineForOffset(selectionStart.coerceIn(0, text?.length ?: 0))
                val lineTop = layout.getLineTop(cursorLine) + totalPadTop
                val lineBottom = layout.getLineBottom(cursorLine) + totalPadTop
                canvas.drawRect(
                    0f,
                    lineTop.toFloat(),
                    max(width.toFloat(), layout.width.toFloat() + totalPadLeft),
                    lineBottom.toFloat(),
                    currentLinePaint
                )
            }

            // 2. Draw Matching Bracket Highlight
            matchingBracketPair?.let { (b1, b2) ->
                drawBracketHighlight(canvas, layout, b1)
                drawBracketHighlight(canvas, layout, b2)
            }

            // 3. Draw Search Matches directly on canvas (Zero-Span Allocation)
            if (searchMatches.isNotEmpty()) {
                val clip = canvas.clipBounds
                val startLine = layout.getLineForVertical(max(0, clip.top - totalPadTop))
                val endLine = layout.getLineForVertical(max(0, clip.bottom - totalPadTop))
                val startOffset = layout.getLineStart(startLine)
                val endOffset = layout.getLineEnd(endLine)

                for (idx in searchMatches.indices) {
                    val range = searchMatches[idx]
                    if (range.last < startOffset || range.first > endOffset) continue

                    val isCurrent = (idx == currentMatchIndex)
                    drawRangeHighlight(canvas, layout, range.first, range.last + 1, isCurrent)
                }
            }
        }

        super.onDraw(canvas)
    }

    private fun drawBracketHighlight(canvas: Canvas, layout: android.text.Layout, offset: Int) {
        if (offset !in 0 until (text?.length ?: 0)) return
        val line = layout.getLineForOffset(offset)
        val top = layout.getLineTop(line) + totalPaddingTop
        val bottom = layout.getLineBottom(line) + totalPaddingTop
        val left = layout.getPrimaryHorizontal(offset) + totalPaddingLeft
        val right = layout.getPrimaryHorizontal(offset + 1) + totalPaddingLeft

        val rect = RectF(min(left, right), top.toFloat(), max(left, right), bottom.toFloat())
        canvas.drawRoundRect(rect, dpToPx(2f), dpToPx(2f), bracketMatchPaint)
        canvas.drawRoundRect(rect, dpToPx(2f), dpToPx(2f), bracketBorderPaint)
    }

    private fun drawRangeHighlight(
        canvas: Canvas,
        layout: android.text.Layout,
        start: Int,
        end: Int,
        isCurrent: Boolean
    ) {
        val startLine = layout.getLineForOffset(start)
        val endLine = layout.getLineForOffset(end)

        for (line in startLine..endLine) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)

            val segStart = max(start, lineStart)
            val segEnd = min(end, lineEnd)
            if (segStart >= segEnd) continue

            val x1 = layout.getPrimaryHorizontal(segStart) + totalPaddingLeft
            val x2 = layout.getPrimaryHorizontal(segEnd) + totalPaddingLeft
            val y1 = layout.getLineTop(line) + totalPaddingTop
            val y2 = layout.getLineBottom(line) + totalPaddingTop

            val rect = RectF(min(x1, x2), y1.toFloat(), max(x1, x2), y2.toFloat())
            if (isCurrent) {
                canvas.drawRect(rect, currentSearchMatchPaint)
                canvas.drawRect(rect, currentSearchBorderPaint)
            } else {
                canvas.drawRect(rect, searchMatchPaint)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    if (handleSmartEnter()) return true
                }
                KeyEvent.KEYCODE_DEL -> {
                    if (handleSmartBackspace()) return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Auto-indentation: copies leading whitespace of previous line and adds
     * 4 spaces if the line ended with '{', '(', '[', or ':'.
     */
    fun handleSmartEnter(): Boolean {
        val editable = text ?: return false
        val selStart = selectionStart
        val selEnd = selectionEnd
        if (selStart < 0 || selStart != selEnd) return false

        val textStr = editable.toString()
        val lineStart = textStr.lastIndexOf('\n', selStart - 1).let { if (it == -1) 0 else it + 1 }
        val currentLineBeforeCursor = textStr.substring(lineStart, selStart)

        // Find leading indentation
        var indentLen = 0
        while (indentLen < currentLineBeforeCursor.length &&
            (currentLineBeforeCursor[indentLen] == ' ' || currentLineBeforeCursor[indentLen] == '\t')
        ) {
            indentLen++
        }
        val leadingIndent = currentLineBeforeCursor.substring(0, indentLen)

        // Check if line ends with block opener
        val trimmed = currentLineBeforeCursor.trimEnd()
        val extraIndent = if (trimmed.endsWith("{") || trimmed.endsWith("(") ||
            trimmed.endsWith("[") || trimmed.endsWith(":")
        ) {
            "    "
        } else {
            ""
        }

        val insertion = "\n" + leadingIndent + extraIndent
        editable.replace(selStart, selEnd, insertion)
        setSelection(selStart + insertion.length)
        return true
    }

    /**
     * Smart backspace: deletes up to 4 spaces at once if cursor is on an indent boundary.
     */
    fun handleSmartBackspace(): Boolean {
        val editable = text ?: return false
        val selStart = selectionStart
        val selEnd = selectionEnd
        if (selStart <= 0 || selStart != selEnd) return false

        val textStr = editable.toString()
        val lineStart = textStr.lastIndexOf('\n', selStart - 1).let { if (it == -1) 0 else it + 1 }
        val prefix = textStr.substring(lineStart, selStart)

        if (prefix.isNotEmpty() && prefix.all { it == ' ' }) {
            val spacesToDelete = when (val remainder = prefix.length % 4) {
                0 -> 4
                else -> remainder
            }
            val deleteStart = max(0, selStart - spacesToDelete)
            editable.delete(deleteStart, selStart)
            setSelection(deleteStart)
            return true
        }
        return false
    }

    /**
     * Inserts symbol or wraps selection if text is selected.
     */
    fun insertSymbol(sym: String) {
        val editable = text ?: return
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        val min = min(start, end)
        val max = max(start, end)

        val pairs = mapOf(
            "{" to "}",
            "(" to ")",
            "[" to "]",
            "\"" to "\"",
            "'" to "'",
            "<" to ">"
        )

        val close = pairs[sym]
        if (min != max && close != null) {
            // Wrap selected text
            val selected = editable.subSequence(min, max).toString()
            val wrapped = "$sym$selected$close"
            editable.replace(min, max, wrapped)
            setSelection(min + wrapped.length)
        } else if (close != null) {
            // Insert pair and place cursor inside
            editable.replace(min, max, "$sym$close")
            setSelection(min + sym.length)
        } else {
            editable.replace(min, max, sym)
            setSelection(min + sym.length)
        }
    }

    /**
     * Indents selected lines by 4 spaces.
     */
    fun indentSelection() {
        val editable = text ?: return
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        val min = min(start, end)
        val max = max(start, end)

        val textStr = editable.toString()
        val firstLineStart = textStr.lastIndexOf('\n', min - 1).let { if (it == -1) 0 else it + 1 }
        val lastLineEnd = textStr.indexOf('\n', max).let { if (it == -1) textStr.length else it }

        val block = textStr.substring(firstLineStart, lastLineEnd)
        val indented = block.split('\n').joinToString("\n") { "    $it" }
        editable.replace(firstLineStart, lastLineEnd, indented)
        setSelection(firstLineStart, firstLineStart + indented.length)
    }

    /**
     * Unindents selected lines by up to 4 spaces.
     */
    fun unindentSelection() {
        val editable = text ?: return
        val start = selectionStart.coerceAtLeast(0)
        val end = selectionEnd.coerceAtLeast(0)
        val min = min(start, end)
        val max = max(start, end)

        val textStr = editable.toString()
        val firstLineStart = textStr.lastIndexOf('\n', min - 1).let { if (it == -1) 0 else it + 1 }
        val lastLineEnd = textStr.indexOf('\n', max).let { if (it == -1) textStr.length else it }

        val block = textStr.substring(firstLineStart, lastLineEnd)
        val unindented = block.split('\n').joinToString("\n") { line ->
            when {
                line.startsWith("    ") -> line.substring(4)
                line.startsWith("\t") -> line.substring(1)
                line.startsWith("   ") -> line.substring(3)
                line.startsWith("  ") -> line.substring(2)
                line.startsWith(" ") -> line.substring(1)
                else -> line
            }
        }
        editable.replace(firstLineStart, lastLineEnd, unindented)
        setSelection(firstLineStart, firstLineStart + unindented.length)
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    companion object {
        private const val COLOR_CURRENT_LINE = 0x1A61AFEF.toInt() // Subtle transparent blue/gray
        private const val COLOR_SEARCH_MATCH = 0x55E5C07B.toInt() // Semi-transparent yellow
        private const val COLOR_CURRENT_SEARCH_MATCH = 0x99FFA000.toInt() // Vibrant orange
        private const val COLOR_CURRENT_SEARCH_BORDER = 0xFFFFB300.toInt()
        private const val COLOR_BRACKET_BG = 0x4461AFEF.toInt()
        private const val COLOR_BRACKET_BORDER = 0xFF61AFEF.toInt()
    }
}
