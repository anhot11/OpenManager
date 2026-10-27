/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class CodeEditorLogicTest {

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

    @Test
    fun testBracketMatching() {
        val code = "function test() { if (a[0] == 1) { return (x + y); } }"

        // match '(' at index 13 with ')' at index 14
        val p1 = findPartnerBracket(code, 13, '(')
        assertEquals(14, p1)

        // match '{' at index 16 with '}' at index 53
        val p2 = findPartnerBracket(code, 16, '{')
        assertEquals(53, p2)

        // match '[' at index 23 with ']' at index 25
        val p3 = findPartnerBracket(code, 23, '[')
        assertEquals(25, p3)

        // match backwards '}' at index 53 with '{' at index 16
        val p4 = findPartnerBracket(code, 53, '}')
        assertEquals(16, p4)
    }

    @Test
    fun testSearchPatternMatching() {
        val text = "OpenManager openmanager OPENMANAGER MTManager"

        // Plain search case insensitive
        val matchesCaseInsensitive = mutableListOf<Int>()
        var idx = 0
        while (idx < text.length) {
            val found = text.indexOf("openmanager", idx, ignoreCase = true)
            if (found == -1) break
            matchesCaseInsensitive.add(found)
            idx = found + 11
        }
        assertEquals(3, matchesCaseInsensitive.size)

        // Case sensitive
        val matchesCaseSensitive = mutableListOf<Int>()
        idx = 0
        while (idx < text.length) {
            val found = text.indexOf("openmanager", idx, ignoreCase = false)
            if (found == -1) break
            matchesCaseSensitive.add(found)
            idx = found + 11
        }
        assertEquals(1, matchesCaseSensitive.size)

        // Regex search
        val regex = Pattern.compile("(?i)[a-z]+manager")
        val matcher = regex.matcher(text)
        val regexMatches = mutableListOf<String>()
        while (matcher.find()) {
            regexMatches.add(matcher.group())
        }
        assertEquals(4, regexMatches.size) // OpenManager, openmanager, OPENMANAGER, MTManager
    }

    @Test
    fun testLineEndingConversion() {
        val crlfText = "line1\r\nline2\r\nline3\r\n"
        assertTrue(crlfText.contains("\r\n"))

        val normalized = crlfText.replace("\r\n", "\n")
        assertEquals("line1\nline2\nline3\n", normalized)

        val restoredCrlf = normalized.replace("\n", "\r\n")
        assertEquals(crlfText, restoredCrlf)
    }
}
