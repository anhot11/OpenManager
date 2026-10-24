/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.util.regex.Pattern

object SyntaxHighlighter {
    // Theme colors (MT Manager Dark Code Theme)
    private const val COLOR_KEYWORD = 0xFFC678DD.toInt()   // Purple / Pink
    private const val COLOR_TYPE = 0xFFE5C07B.toInt()      // Warm Gold / Amber
    private const val COLOR_STRING = 0xFF98C379.toInt()    // Pleasant Green
    private const val COLOR_NUMBER = 0xFFD19A66.toInt()    // Orange
    private const val COLOR_COMMENT = 0xFF7F848E.toInt()   // Slate Gray
    private const val COLOR_ANNOTATION = 0xFF61AFEF.toInt()// Sky Blue
    private const val COLOR_TAG = 0xFFE06C75.toInt()       // Coral Red
    private const val COLOR_ATTRIBUTE = 0xFFD19A66.toInt() // Orange
    private const val COLOR_OPERATOR = 0xFF56B6C2.toInt()  // Cyan

    // Common patterns
    private val PATTERN_STRING = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'")
    private val PATTERN_NUMBER = Pattern.compile("\\b(0x[0-9a-fA-F]+|[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?[fFdDlL]?)\\b")
    private val PATTERN_WORD = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
    private val PATTERN_ANNOTATION = Pattern.compile("(@[a-zA-Z0-9_]+|#[a-zA-Z0-9_]+|\\<\\?php|\\?\\>)")
    private val PATTERN_SMALI_REGISTER = Pattern.compile("\\b([vp][0-9]+)\\b")

    // XML/HTML patterns
    private val PATTERN_XML_TAG = Pattern.compile("</?[a-zA-Z0-9_:-]+|/?>")
    private val PATTERN_XML_ATTR = Pattern.compile("\\b[a-zA-Z0-9_:-]+(?=\\s*=)")
    private val PATTERN_XML_COMMENT = Pattern.compile("<!--[\\s\\S]*?-->")

    // Custom tag class to easily remove our syntax spans without clearing system spans
    class SyntaxSpan(val color: Int) : ForegroundColorSpan(color)
    class SyntaxItalicSpan : StyleSpan(Typeface.ITALIC)

    fun highlight(editable: Editable?, language: SyntaxLanguage) {
        if (editable == null) return
        if (language == SyntaxLanguage.PLAIN_TEXT || editable.length > 200_000) {
            clearSpans(editable)
            return
        }

        clearSpans(editable)

        val text = editable.toString()

        if (language.isXmlHtml) {
            highlightXmlHtml(editable, text)
            return
        }

        // 1. Highlight numbers
        val mNumber = PATTERN_NUMBER.matcher(text)
        while (mNumber.find()) {
            editable.setSpan(SyntaxSpan(COLOR_NUMBER), mNumber.start(), mNumber.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // 2. Highlight keywords and types
        if (language.keywords.isNotEmpty() || language.types.isNotEmpty()) {
            val mWord = PATTERN_WORD.matcher(text)
            val isSql = language == SyntaxLanguage.SQL
            while (mWord.find()) {
                val word = mWord.group()
                val checkWord = if (isSql) word.uppercase() else word
                if (checkWord in language.keywords) {
                    editable.setSpan(SyntaxSpan(COLOR_KEYWORD), mWord.start(), mWord.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (checkWord in language.types) {
                    editable.setSpan(SyntaxSpan(COLOR_TYPE), mWord.start(), mWord.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        // 2.5 Smali specific registers
        if (language == SyntaxLanguage.SMALI) {
            val mReg = PATTERN_SMALI_REGISTER.matcher(text)
            while (mReg.find()) {
                editable.setSpan(SyntaxSpan(COLOR_TYPE), mReg.start(), mReg.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // 3. Highlight annotations / directives
        val mAnno = PATTERN_ANNOTATION.matcher(text)
        while (mAnno.find()) {
            editable.setSpan(SyntaxSpan(COLOR_ANNOTATION), mAnno.start(), mAnno.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // 4. Highlight strings (overwrites keywords if inside string)
        val mString = PATTERN_STRING.matcher(text)
        while (mString.find()) {
            removeSyntaxSpans(editable, mString.start(), mString.end())
            editable.setSpan(SyntaxSpan(COLOR_STRING), mString.start(), mString.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // 5. Highlight single-line comments
        language.commentLine?.let { prefix ->
            val escaped = Pattern.quote(prefix)
            val pComment = Pattern.compile("$escaped.*")
            val mComment = pComment.matcher(text)
            while (mComment.find()) {
                removeSyntaxSpans(editable, mComment.start(), mComment.end())
                editable.setSpan(SyntaxSpan(COLOR_COMMENT), mComment.start(), mComment.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                editable.setSpan(SyntaxItalicSpan(), mComment.start(), mComment.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // 6. Highlight multi-line block comments
        if (language.commentBlockStart != null && language.commentBlockEnd != null) {
            val pStart = Pattern.quote(language.commentBlockStart)
            val pEnd = Pattern.quote(language.commentBlockEnd)
            val pBlock = Pattern.compile("$pStart[\\s\\S]*?$pEnd")
            val mBlock = pBlock.matcher(text)
            while (mBlock.find()) {
                removeSyntaxSpans(editable, mBlock.start(), mBlock.end())
                editable.setSpan(SyntaxSpan(COLOR_COMMENT), mBlock.start(), mBlock.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                editable.setSpan(SyntaxItalicSpan(), mBlock.start(), mBlock.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun highlightXmlHtml(editable: Editable, text: String) {
        // Tags
        val mTag = PATTERN_XML_TAG.matcher(text)
        while (mTag.find()) {
            editable.setSpan(SyntaxSpan(COLOR_TAG), mTag.start(), mTag.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Attributes
        val mAttr = PATTERN_XML_ATTR.matcher(text)
        while (mAttr.find()) {
            editable.setSpan(SyntaxSpan(COLOR_ATTRIBUTE), mAttr.start(), mAttr.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Attribute strings
        val mString = PATTERN_STRING.matcher(text)
        while (mString.find()) {
            removeSyntaxSpans(editable, mString.start(), mString.end())
            editable.setSpan(SyntaxSpan(COLOR_STRING), mString.start(), mString.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // XML Comments
        val mComment = PATTERN_XML_COMMENT.matcher(text)
        while (mComment.find()) {
            removeSyntaxSpans(editable, mComment.start(), mComment.end())
            editable.setSpan(SyntaxSpan(COLOR_COMMENT), mComment.start(), mComment.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            editable.setSpan(SyntaxItalicSpan(), mComment.start(), mComment.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun clearSpans(editable: Editable) {
        val syntaxSpans = editable.getSpans(0, editable.length, SyntaxSpan::class.java)
        for (span in syntaxSpans) {
            editable.removeSpan(span)
        }
        val italicSpans = editable.getSpans(0, editable.length, SyntaxItalicSpan::class.java)
        for (span in italicSpans) {
            editable.removeSpan(span)
        }
    }

    private fun removeSyntaxSpans(editable: Editable, start: Int, end: Int) {
        val syntaxSpans = editable.getSpans(start, end, SyntaxSpan::class.java)
        for (span in syntaxSpans) {
            editable.removeSpan(span)
        }
        val italicSpans = editable.getSpans(start, end, SyntaxItalicSpan::class.java)
        for (span in italicSpans) {
            editable.removeSpan(span)
        }
    }
}
