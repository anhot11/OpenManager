/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/**
 * High-performance line number gutter pinned to the left of the code editor.
 * Stays aligned with CodeEditText layout and never scrolls away during horizontal scroll.
 */
class LineNumberView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var editor: CodeEditText? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_LINE_NUMBER
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.MONOSPACE
    }

    private val currentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_CURRENT_LINE_NUMBER
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val backgroundPaint = Paint().apply {
        color = COLOR_GUTTER_BG
        style = Paint.Style.FILL
    }

    private val dividerPaint = Paint().apply {
        color = COLOR_DIVIDER
        strokeWidth = dpToPx(1f)
        style = Paint.Style.STROKE
    }

    private var totalLines = 1
    private var currentCursorLine = 0
    private var digitCount = 3

    init {
        updateTextSize(13f)
        updateWidth()
    }

    fun attachEditor(codeEditText: CodeEditText) {
        this.editor = codeEditText
        updateTextSize(codeEditText.textSizePx * 0.85f)
        post { syncLineCount() }
    }

    fun updateTextSize(spOrPx: Float) {
        textPaint.textSize = spOrPx
        currentLinePaint.textSize = spOrPx
        updateWidth()
        invalidate()
    }

    fun setCurrentLine(logicalLineIndex: Int) {
        if (currentCursorLine != logicalLineIndex) {
            currentCursorLine = logicalLineIndex
            invalidate()
        }
    }

    fun syncLineCount() {
        val count = editor?.lineCount ?: 1
        if (count != totalLines) {
            totalLines = max(1, count)
            val newDigits = max(3, totalLines.toString().length)
            if (newDigits != digitCount) {
                digitCount = newDigits
                updateWidth()
            }
            invalidate()
        }
    }

    private fun updateWidth() {
        val sampleText = "9".repeat(digitCount)
        val textWidth = textPaint.measureText(sampleText)
        val padding = dpToPx(16f)
        val desiredWidth = (textWidth + padding).toInt()

        val params = layoutParams
        if (params != null && params.width != desiredWidth) {
            params.width = desiredWidth
            layoutParams = params
        } else if (params == null) {
            layoutParams = ViewGroup.LayoutParams(desiredWidth, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val sampleText = "9".repeat(digitCount)
        val textWidth = textPaint.measureText(sampleText)
        val padding = dpToPx(16f)
        val desiredWidth = (textWidth + padding).toInt()
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(0, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw background
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        // 2. Draw divider line on the right
        canvas.drawLine(w - 1f, 0f, w - 1f, h, dividerPaint)

        val ed = editor ?: return
        val layout = ed.layout ?: return
        val scrollY = scrollY

        val paddingTop = ed.totalPaddingTop
        val textPaddingEnd = dpToPx(8f)
        val textX = w - textPaddingEnd

        val visibleTop = scrollY
        val visibleBottom = scrollY + height

        val startLine = layout.getLineForVertical(max(0, visibleTop - paddingTop))
        val endLine = layout.getLineForVertical(max(0, visibleBottom - paddingTop))

        var logicalLine = 1
        // Calculate logical line at startLine
        val text = ed.text ?: ""
        if (startLine > 0) {
            val startOffset = layout.getLineStart(startLine)
            var count = 1
            for (j in 0 until startOffset) {
                if (text[j] == '\n') count++
            }
            logicalLine = count
        }

        for (i in startLine..endLine) {
            val lineStart = layout.getLineStart(i)
            val isFirstPhysicalLineOfLogical = (i == 0 || (lineStart > 0 && text[lineStart - 1] == '\n'))

            if (isFirstPhysicalLineOfLogical) {
                val baseline = layout.getLineBaseline(i) + paddingTop
                val isCurrent = (logicalLine - 1 == currentCursorLine)
                val paint = if (isCurrent) currentLinePaint else textPaint

                canvas.drawText(logicalLine.toString(), textX, baseline.toFloat(), paint)
                logicalLine++
            }
        }
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    companion object {
        private const val COLOR_GUTTER_BG = 0xFF21252B.toInt()
        private const val COLOR_DIVIDER = 0xFF3E4451.toInt()
        private const val COLOR_LINE_NUMBER = 0xFF5C6370.toInt()
        private const val COLOR_CURRENT_LINE_NUMBER = 0xFF61AFEF.toInt()
    }
}
