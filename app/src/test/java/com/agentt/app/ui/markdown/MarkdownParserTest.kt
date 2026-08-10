package com.agentt.app.ui.markdown

import com.agentt.app.ui.markdown.MarkdownParser.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parsesHeadings() {
        val blocks = MarkdownParser.parse("# 标题一\n\n## 标题二\n")
        assertEquals(Block.Heading(1, "标题一"), blocks[0])
        assertEquals(Block.Heading(2, "标题二"), blocks[1])
    }

    @Test
    fun parsesFencedCode() {
        val blocks = MarkdownParser.parse("```kotlin\nval x = 1\n```")
        assertEquals(Block.CodeBlock("kotlin", "val x = 1"), blocks[0])
    }

    @Test
    fun parsesBulletList() {
        val blocks = MarkdownParser.parse("- 苹果\n- 香蕉\n")
        val list = blocks[0] as Block.BulletList
        assertEquals(2, list.items.size)
        assertEquals("苹果", list.items[0].content)
        assertEquals("香蕉", list.items[1].content)
    }

    @Test
    fun parsesTaskList() {
        val blocks = MarkdownParser.parse("- [x] 完成\n- [ ] 未完成\n")
        val list = blocks[0] as Block.BulletList
        assertTrue(list.items[0].checked == true)
        assertTrue(list.items[1].checked == false)
    }

    @Test
    fun parsesTable() {
        val blocks = MarkdownParser.parse("| 列A | 列B |\n| --- | --- |\n| 1 | 2 |\n")
        val table = blocks[0] as Block.Table
        assertEquals(listOf("列A", "列B"), table.headers)
        assertEquals(listOf("1", "2"), table.rows[0])
    }

    @Test
    fun parsesBlockquote() {
        val blocks = MarkdownParser.parse("> 引用内容\n")
        val quote = blocks[0] as Block.Blockquote
        assertTrue(quote.blocks.isNotEmpty())
    }

    @Test
    fun parsesThematicBreak() {
        val blocks = MarkdownParser.parse("---\n")
        assertEquals(Block.ThematicBreak, blocks[0])
    }

    @Test
    fun doesNotMisparseYearAsNumberedList() {
        val blocks = MarkdownParser.parse("2020. 年的故事")
        assertEquals(Block.Paragraph("2020. 年的故事"), blocks[0])
    }
}
