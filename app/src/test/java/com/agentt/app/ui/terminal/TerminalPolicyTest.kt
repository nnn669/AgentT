package com.agentt.app.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalPolicyTest {
    @Test
    fun classifiesReadOnlyCommands() {
        assertEquals(CommandRisk.READ_ONLY, TerminalPolicy.classify("ls -la"))
        assertEquals(CommandRisk.READ_ONLY, TerminalPolicy.classify("getprop ro.build.version.release"))
    }

    @Test
    fun classifiesWorkspaceWrites() {
        assertEquals(CommandRisk.WRITE, TerminalPolicy.classify("mkdir sample"))
        assertEquals(CommandRisk.WRITE, TerminalPolicy.classify("echo hello > note.txt"))
    }

    @Test
    fun privilegedRulesAreReadyForFutureBackend() {
        assertEquals(CommandRisk.PRIVILEGED, TerminalPolicy.classify("settings put global airplane_mode_on 1"))
        assertEquals(CommandRisk.PRIVILEGED, TerminalPolicy.classify("pm clear com.example.app"))
    }
}
