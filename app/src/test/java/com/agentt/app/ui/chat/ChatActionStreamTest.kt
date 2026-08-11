package com.agentt.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatActionStreamTest {
    @Test fun parsesStandardActionStream() {
        val actions = parseActionStream("""{"actions":[{"type":"think","content":"先查一下"},{"type":"browser","tool":"extract","url":"https://example.com"},{"type":"reply","content":"好的"}]}""")!!
        assertEquals(3, actions.size); assertEquals("think", actions[0].type); assertEquals("browser", actions[1].type)
        assertEquals("extract", actions[1].tool); assertEquals("https://example.com", actions[1].url); assertEquals("好的", actions[2].content)
    }

    @Test fun routesTerminalActionThroughAutomaticToolLoopWithLimits() {
        val action = parseActionStream("""{"actions":[{"type":"terminal","command":"ls -la","backend":"local","timeout_ms":999999,"max_output_chars":10}]}""")!!.single()
        assertEquals("browser", action.type)
        assertEquals("terminal", action.tool)
        assertEquals("ls -la", action.command)
        assertTrue(action.query!!.contains("\"command\":\"ls -la\""))
        assertEquals("LOCAL", action.backend); assertEquals(120_000L, action.timeoutMs); assertEquals(1_024, action.maxOutputChars)
    }

    @Test fun parsesFencedJson() {
        val actions = parseActionStream("```json\n{\"actions\":[{\"type\":\"reply\",\"content\":\"完成\"}]}\n```")
        assertTrue(actions != null); assertEquals("reply", actions!![0].type); assertEquals("完成", actions[0].content)
    }

    @Test fun returnsNullForPlainText() { assertNull(parseActionStream("这是一个普通的回复，不包含动作。")) }
    @Test fun returnsNullForEmptyActions() { assertNull(parseActionStream("""{"actions":[]}""")) }
    @Test fun extractsSearchQuery() {
        val action = parseActionStream("""{"actions":[{"type":"browser","tool":"search","query":"天气"}]}""")!!.single()
        assertEquals("search", action.tool); assertEquals("天气", action.query)
    }
}