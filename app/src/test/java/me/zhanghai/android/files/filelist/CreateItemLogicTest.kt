/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateItemLogicTest {

    private fun applyExtension(currentText: String, ext: String): Pair<String, Int> {
        return if (currentText.isEmpty()) {
            val defaultName = "nuevo$ext"
            Pair(defaultName, "nuevo".length)
        } else {
            val dot = currentText.lastIndexOf('.')
            val base = if (dot > 0) currentText.substring(0, dot) else currentText
            val newText = base + ext
            Pair(newText, base.length)
        }
    }

    @Test
    fun testApplyExtensionToEmptyText() {
        val (text, selectionEnd) = applyExtension("", ".txt")
        assertEquals("nuevo.txt", text)
        assertEquals(5, selectionEnd)
    }

    @Test
    fun testApplyExtensionToNameWithoutExtension() {
        val (text, selectionEnd) = applyExtension("my_script", ".sh")
        assertEquals("my_script.sh", text)
        assertEquals(9, selectionEnd)
    }

    @Test
    fun testReplaceExistingExtension() {
        val (text, selectionEnd) = applyExtension("manifest.xml", ".json")
        assertEquals("manifest.json", text)
        assertEquals(8, selectionEnd)
    }

    @Test
    fun testNameValidation() {
        val validFolderName = "New_Folder_123"
        val invalidFolderName1 = "folder/subfolder"
        val invalidFolderName2 = "folder\u0000"

        assertTrue(isValidFileName(validFolderName))
        assertFalse(isValidFileName(invalidFolderName1))
        assertFalse(isValidFileName(invalidFolderName2))
    }

    private fun isValidFileName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name.contains('/') || name.contains('\\') || name.contains('\u0000')) return false
        return true
    }
}
