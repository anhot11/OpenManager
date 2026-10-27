/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.text.Editable
import android.text.SpannableStringBuilder

/**
 * Syntax highlighter bridge delegating to the high-performance FastSyntaxScanner.
 */
object SyntaxHighlighter {

    fun highlight(editable: Editable?, language: SyntaxLanguage) {
        if (editable == null) return
        FastSyntaxScanner.applyHighlightTo(editable, language)
    }

    fun highlightToSpannable(text: CharSequence, language: SyntaxLanguage): SpannableStringBuilder {
        return FastSyntaxScanner.highlightToSpannable(text, language)
    }
}
