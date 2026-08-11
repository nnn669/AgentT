package com.agentt.app.ui.chat

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPromptTest {

    @Test
    fun `system prompt includes Android environment declaration`() {
        assertTrue(
            "系统提示词应声明 Android 本机环境",
            SYSTEM_PROMPT.contains("Android")
        )
    }

    @Test
    fun `system prompt includes terminal execute tool`() {
        assertTrue(
            "系统提示词应包含 terminal.execute 工具说明",
            SYSTEM_PROMPT.contains("terminal.execute")
        )
    }

    @Test
    fun `system prompt includes no root constraint`() {
        assertTrue(
            "系统提示词应声明无 root 权限约束",
            SYSTEM_PROMPT.contains("无 root")
        )
    }

    @Test
    fun `system prompt includes environment constraints`() {
        assertTrue(
            "系统提示词应声明无默认运行时环境",
            SYSTEM_PROMPT.contains("无默认") || SYSTEM_PROMPT.contains("Git") || SYSTEM_PROMPT.contains("Node")
        )
    }

    @Test
    fun `system prompt includes all browser tools`() {
        val browserTools = listOf("browser.search", "browser.extract", "browser.title", "browser.links", "browser.open")
        for (tool in browserTools) {
            assertTrue("系统提示词应包含 $tool", SYSTEM_PROMPT.contains(tool))
        }
    }

    @Test
    fun `system prompt includes all file tools`() {
        val fileTools = listOf("file.list", "file.read", "file.write", "file.stat", "file.delete")
        for (tool in fileTools) {
            assertTrue("系统提示词应包含 $tool", SYSTEM_PROMPT.contains(tool))
        }
    }

    @Test
    fun `system prompt includes action stream format`() {
        assertTrue("系统提示词应包含 JSON 动作流格式说明", SYSTEM_PROMPT.contains("actions"))
        assertTrue("系统提示词应包含 think 类型", SYSTEM_PROMPT.contains("think"))
        assertTrue("系统提示词应包含 tool 类型", SYSTEM_PROMPT.contains("tool"))
        assertTrue("系统提示词应包含 reply 类型", SYSTEM_PROMPT.contains("reply"))
    }

    @Test
    fun `system prompt uses Chinese language`() {
        assertTrue("系统提示词应使用中文", SYSTEM_PROMPT.contains("智能体"))
        assertTrue("系统提示词应有中文规则说明", SYSTEM_PROMPT.contains("规则"))
    }
}