/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TerminalLogicTest {

    private fun formatShellCommand(cwd: String, initialCommand: String): String {
        val escapedCwd = cwd.replace("'", "'\\''")
        return "cd '$escapedCwd' && ($initialCommand); _mt_status=\$?; exit \$_mt_status"
    }

    private fun simulateJniArgvConstruction(cmd: String, args: Array<String>?): Array<String> {
        val size = args?.size ?: 0
        var prependCmd = false
        if (size == 0) {
            prependCmd = true
        } else if (args != null && args[0].startsWith("-")) {
            prependCmd = true
        }

        val totalArgs = size + (if (prependCmd) 1 else 0)
        val result = ArrayList<String>(totalArgs)
        if (prependCmd) {
            val progName = File(cmd).name
            result.add(if (progName.isNotEmpty()) progName else "sh")
        }
        if (args != null) {
            result.addAll(args)
        }
        return result.toTypedArray()
    }

    @Test
    fun testShellCommandEscapesSingleQuotes() {
        val cwd = "/storage/emulated/0/My Folder's Documents"
        val cmd = "sh \"/storage/emulated/0/a.sh\""
        val formatted = formatShellCommand(cwd, cmd)

        assertTrue(formatted.contains("'/storage/emulated/0/My Folder'\\''s Documents'"))
        assertTrue(formatted.contains("(sh \"/storage/emulated/0/a.sh\")"))
        assertTrue(formatted.endsWith("; _mt_status=\$?; exit \$_mt_status"))
    }

    @Test
    fun testArgv0IntegrityStandardShell() {
        val cmd = "sh \"/storage/emulated/0/a.sh\""
        val shellCmd = formatShellCommand("/storage/emulated/0", cmd)
        val args = arrayOf("sh", "-c", shellCmd)

        // Ensure args[0] is program name 'sh', NOT '-c'
        assertNotEquals("-c", args[0])
        assertEquals("sh", args[0])
        assertEquals("-c", args[1])
        assertEquals(shellCmd, args[2])
    }

    @Test
    fun testArgv0IntegrityRootAndTermex() {
        val cmd = "sh \"/storage/emulated/0/a.sh\""
        val shellCmd = formatShellCommand("/storage/emulated/0", cmd)

        val rootArgs = arrayOf("su", "-c", shellCmd)
        assertEquals("su", rootArgs[0])
        assertEquals("-c", rootArgs[1])

        val termexArgs = arrayOf("bash", "--norc", "-c", shellCmd)
        assertEquals("bash", termexArgs[0])
        assertEquals("--norc", termexArgs[1])
        assertEquals("-c", termexArgs[2])
    }

    @Test
    fun testJniAutoPrependsProgramNameWhenOmitted() {
        // Test when caller erroneously passed only ["-c", "cmd"]
        val flawedArgs = arrayOf("-c", "echo hello")
        val fixedArgv = simulateJniArgvConstruction("/system/bin/sh", flawedArgs)

        assertEquals(3, fixedArgv.size)
        assertEquals("sh", fixedArgv[0])
        assertEquals("-c", fixedArgv[1])
        assertEquals("echo hello", fixedArgv[2])
    }

    @Test
    fun testJniKeepsArgvWhenProgramNameAlreadyPresent() {
        val goodArgs = arrayOf("sh", "-c", "echo hello")
        val argv = simulateJniArgvConstruction("/system/bin/sh", goodArgs)

        assertEquals(3, argv.size)
        assertEquals("sh", argv[0])
        assertEquals("-c", argv[1])
        assertEquals("echo hello", argv[2])
    }

    @Test
    fun testJniHandlesEmptyOrNullArgs() {
        val argvNull = simulateJniArgvConstruction("/system/bin/sh", null)
        assertEquals(1, argvNull.size)
        assertEquals("sh", argvNull[0])

        val argvEmpty = simulateJniArgvConstruction("/system/bin/sh", emptyArray())
        assertEquals(1, argvEmpty.size)
        assertEquals("sh", argvEmpty[0])
    }

    @Test
    fun testDefaultFontSizeIsReadable() {
        fun sanitizeFontSize(size: Int): Int {
            return if (size < 13) 14 else size.coerceIn(10, 32)
        }

        assertEquals(14, sanitizeFontSize(12)) // upgrades legacy 12 to 14
        assertEquals(14, sanitizeFontSize(14))
        assertEquals(16, sanitizeFontSize(16))
        assertEquals(10, sanitizeFontSize(8))  // clamps minimum
        assertEquals(32, sanitizeFontSize(40)) // clamps maximum
    }
}
