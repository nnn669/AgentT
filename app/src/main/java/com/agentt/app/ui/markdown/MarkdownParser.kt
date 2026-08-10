package com.agentt.app.ui.markdown

/**
 * Lightweight markdown parser (ported from OpenMinis android's MarkdownParser.kt)
 * that converts raw markdown text into a list of block nodes.
 * Supports: headings, code blocks (fenced), blockquotes, bullet/numbered/task lists,
 * thematic breaks, tables, and paragraphs. Inline parsing is handled in MarkdownText.
 */
object MarkdownParser {

    sealed class Block {
        data class Heading(val level: Int, val content: String) : Block()
        data class Paragraph(val content: String) : Block()
        data class CodeBlock(val language: String, val code: String) : Block()
        data class Blockquote(val blocks: List<Block>) : Block()
        data class BulletList(val items: List<ListItem>) : Block()
        data class NumberedList(val startNumber: Int, val items: List<ListItem>) : Block()
        data class Table(val headers: List<String>, val alignments: List<Alignment>, val rows: List<List<String>>) : Block()
        data object ThematicBreak : Block()
    }

    data class ListItem(val content: String, val checked: Boolean? = null)
    enum class Alignment { LEFT, CENTER, RIGHT }

    fun parse(markdown: String): List<Block> {
        val lines = markdown.lines()
        val blocks = mutableListOf<Block>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            // Fenced code block
            if (line.trimStart().startsWith("```")) {
                val fence = line.trimStart()
                val lang = fence.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size) {
                    val cl = lines[i]
                    if (cl.trimStart().startsWith("```") && cl.trim() == "```") {
                        i++
                        break
                    }
                    codeLines.add(cl)
                    i++
                }
                blocks.add(Block.CodeBlock(lang, codeLines.joinToString("\n")))
                continue
            }

            // Thematic break
            if (line.matches(Regex("^\\s{0,3}([-*_])\\s*\\1\\s*\\1(\\s*\\1)*\\s*$"))) {
                blocks.add(Block.ThematicBreak)
                i++
                continue
            }

            // ATX Heading
            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val content = headingMatch.groupValues[2].trimEnd().removeSuffix("#").trimEnd()
                blocks.add(Block.Heading(level, content))
                i++
                continue
            }

            // Blockquote
            if (line.trimStart().startsWith("> ") || line.trimStart() == ">") {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && (lines[i].trimStart().startsWith("> ") || lines[i].trimStart() == ">")) {
                    val ql = lines[i].trimStart()
                    quoteLines.add(if (ql == ">") "" else ql.removePrefix("> "))
                    i++
                }
                blocks.add(Block.Blockquote(parse(quoteLines.joinToString("\n"))))
                continue
            }

            // Table (header + separator)
            if (i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                val table = parseTable(lines, i)
                if (table != null) {
                    blocks.add(table.first)
                    i = table.second
                    continue
                }
            }

            // Bullet list (-, *, +)
            val bulletMatch = Regex("^\\s{0,3}[-*+]\\s+(.*)$").find(line)
            if (bulletMatch != null) {
                val items = mutableListOf<ListItem>()
                while (i < lines.size) {
                    val bm = Regex("^\\s{0,3}[-*+]\\s+(.*)$").find(lines[i]) ?: break
                    items.add(parseListItem(bm.groupValues[1]))
                    i++
                    // Continuation lines (indented)
                    while (i < lines.size && lines[i].startsWith(" ") && !Regex("^\\s{0,3}[-*+]\\s").matches(lines[i])) {
                        items[items.lastIndex] = items.last().copy(content = items.last().content + "\n" + lines[i].trimStart())
                        i++
                    }
                }
                blocks.add(Block.BulletList(items))
                continue
            }

            // Numbered list. Marker digits capped at 2 so a paragraph starting
            // with a year or big number ("2020. 年…") isn't misparsed as a list.
            val numMatch = Regex("^\\s{0,3}(\\d{1,2})[.)]\\s*(.*)$").find(line)
            if (numMatch != null) {
                val startNum = numMatch.groupValues[1].toIntOrNull() ?: 1
                val items = mutableListOf<ListItem>()
                while (i < lines.size) {
                    val nm = Regex("^\\s{0,3}(\\d{1,2})[.)]\\s*(.*)$").find(lines[i]) ?: break
                    items.add(parseListItem(nm.groupValues[2]))
                    i++
                    while (i < lines.size && lines[i].startsWith(" ") && !Regex("^\\s{0,3}(\\d{1,2})[.)]\\s").matches(lines[i])) {
                        items[items.lastIndex] = items.last().copy(content = items.last().content + "\n" + lines[i].trimStart())
                        i++
                    }
                }
                blocks.add(Block.NumberedList(startNum, items))
                continue
            }

            // Paragraph
            if (line.isBlank()) {
                i++
                continue
            }
            val para = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank() &&
                !lines[i].trimStart().startsWith("```") &&
                !lines[i].trimStart().startsWith(">") &&
                !lines[i].matches(Regex("^\\s{0,3}([-*_])\\s*\\1\\s*\\1(\\s*\\1)*\\s*$")) &&
                !Regex("^(#{1,6})\\s+").matches(lines[i]) &&
                !Regex("^\\s{0,3}[-*+]\\s").matches(lines[i]) &&
                !Regex("^\\s{0,3}(\\d{1,2})[.)]\\s").matches(lines[i])
            ) {
                para.add(lines[i])
                i++
            }
            if (para.isNotEmpty()) blocks.add(Block.Paragraph(para.joinToString("\n")))
        }
        return blocks
    }

    private fun parseListItem(raw: String): ListItem {
        val task = Regex("^\\[([ xX])\\]\\s+").find(raw)
        return if (task != null) {
            ListItem(raw.substring(task.range.last + 1), task.groupValues[1].lowercase() == "x")
        } else {
            ListItem(raw)
        }
    }

    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains('|') || !trimmed.contains('-')) return false
        val cells = trimmed.split('|').filter { it.isNotBlank() }
        return cells.all { it.trim().matches(Regex("^:?-+:?$")) }
    }

    private fun parseTable(lines: List<String>, startIdx: Int): Pair<Block.Table, Int>? {
        val headerLine = lines[startIdx]
        val separatorLine = lines[startIdx + 1]

        val headers = splitTableRow(headerLine)
        val sepCells = splitTableRow(separatorLine)
        if (headers.isEmpty() || sepCells.isEmpty()) return null

        val alignments = sepCells.map { cell ->
            val trimmed = cell.trim()
            when {
                trimmed.startsWith(':') && trimmed.endsWith(':') -> Alignment.CENTER
                trimmed.endsWith(':') -> Alignment.RIGHT
                else -> Alignment.LEFT
            }
        }

        var i = startIdx + 2
        val rows = mutableListOf<List<String>>()
        while (i < lines.size) {
            val row = lines[i]
            if (row.isBlank() || !row.contains('|')) break
            rows.add(splitTableRow(row))
            i++
        }

        return Block.Table(headers, alignments, rows) to i
    }

    private fun splitTableRow(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        // Protect a `|` inside a markdown link URL `[text](url|with|pipe)`.
        if (!trimmed.contains("](")) {
            return trimmed.split('|').map { it.trim() }
        }
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        for (ch in trimmed) {
            if (ch == '(' && depth == 0) depth++
            else if (ch == ')' && depth > 0) depth--
            if (ch == '|' && depth == 0) {
                cells.add(current.toString().trim())
                current.clear()
            } else {
                current.append(ch)
            }
        }
        cells.add(current.toString().trim())
        return cells
    }
}
