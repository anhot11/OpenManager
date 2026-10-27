/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastSyntaxScannerTest {

    @Test
    fun testJavaCodeTokenization() {
        val code = """
            package com.example;
            
            // Single line comment
            public class Main {
                /* Block comment */
                public static void main(String[] args) {
                    int count = 42;
                    String msg = "Hello, OpenManager!";
                }
            }
        """.trimIndent()

        val tokens = FastSyntaxScanner.scan(code, SyntaxLanguage.JAVA)
        assertTrue(tokens.isNotEmpty())

        // Verify tokens are non-overlapping and within bounds
        for (i in tokens.indices) {
            val token = tokens[i]
            assertTrue(token.start >= 0)
            assertTrue(token.end <= code.length)
            assertTrue(token.start < token.end)
            if (i > 0) {
                assertTrue(token.start >= tokens[i - 1].end)
            }
        }

        // Verify specific tokens exist
        val comments = tokens.filter { it.color == FastSyntaxScanner.COLOR_COMMENT }
        assertEquals(2, comments.size)
        assertTrue(comments.all { it.isItalic })

        val strings = tokens.filter { it.color == FastSyntaxScanner.COLOR_STRING }
        assertEquals(1, strings.size)
        assertEquals("\"Hello, OpenManager!\"", code.substring(strings[0].start, strings[0].end))

        val numbers = tokens.filter { it.color == FastSyntaxScanner.COLOR_NUMBER }
        assertEquals(1, numbers.size)
        assertEquals("42", code.substring(numbers[0].start, numbers[0].end))
    }

    @Test
    fun testSmaliCodeTokenization() {
        val smali = """
            .class public Lcom/example/Test;
            .super Ljava/lang/Object;
            
            # Direct method
            .method public static test(I)V
                .registers 2
                const/4 v0, 0x1
                return-void
            .end method
        """.trimIndent()

        val tokens = FastSyntaxScanner.scan(smali, SyntaxLanguage.SMALI)
        assertTrue(tokens.isNotEmpty())

        // Verify smali directive token
        val directives = tokens.filter {
            smali.substring(it.start, it.end) == ".class" ||
            smali.substring(it.start, it.end) == ".method"
        }
        assertEquals(2, directives.size)
        assertTrue(directives.all { it.color == FastSyntaxScanner.COLOR_KEYWORD })

        // Verify smali register token v0
        val registers = tokens.filter { smali.substring(it.start, it.end) == "v0" }
        assertEquals(1, registers.size)
        assertEquals(FastSyntaxScanner.COLOR_TYPE, registers[0].color)

        // Verify hex number 0x1
        val hex = tokens.filter { smali.substring(it.start, it.end) == "0x1" }
        assertEquals(1, hex.size)
        assertEquals(FastSyntaxScanner.COLOR_NUMBER, hex[0].color)
    }

    @Test
    fun testXmlCodeTokenization() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <!-- Application manifest -->
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="me.zhanghai.android.files">
                <application android:allowBackup="true" />
            </manifest>
        """.trimIndent()

        val tokens = FastSyntaxScanner.scan(xml, SyntaxLanguage.XML)
        assertTrue(tokens.isNotEmpty())

        val comment = tokens.filter { it.color == FastSyntaxScanner.COLOR_COMMENT }
        assertEquals(1, comment.size)
        assertEquals("<!-- Application manifest -->", xml.substring(comment[0].start, comment[0].end))

        val tags = tokens.filter { it.color == FastSyntaxScanner.COLOR_TAG }
        assertTrue(tags.isNotEmpty())

        val attrs = tokens.filter { it.color == FastSyntaxScanner.COLOR_ATTRIBUTE }
        assertTrue(attrs.isNotEmpty())
    }

    @Test
    fun testPerformanceOnLargeCode() {
        // Build 5,000 lines of code (~150KB)
        val sb = java.lang.StringBuilder()
        for (i in 1..5000) {
            sb.append("    int val").append(i).append(" = ").append(i * 10).append(";\n")
            sb.append("    String text").append(i).append(" = \"item_").append(i).append("\"; // comment\n")
        }
        val text = sb.toString()

        val startTime = System.currentTimeMillis()
        val tokens = FastSyntaxScanner.scan(text, SyntaxLanguage.JAVA)
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue(tokens.isNotEmpty())
        // Should tokenize 10,000 lines in less than 500ms
        assertTrue("Elapsed $elapsed ms was too slow", elapsed < 500)
    }
}
