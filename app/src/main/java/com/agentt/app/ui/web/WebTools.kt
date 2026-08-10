package com.agentt.app.ui.web

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object WebTools {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    suspend fun runTool(tool: String, url: String?, query: String?): String = when (tool) {
        "extract" -> extract(url ?: "")
        "title" -> title(url ?: "")
        "links" -> links(url ?: "")
        "search" -> search(query ?: url ?: "")
        "open" -> "将在内置浏览器中打开：${url ?: "无地址"}"
        else -> "未知工具：$tool"
    }

    suspend fun extract(url: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).userAgent(UA).timeout(15000).get()
            val text = doc.body().text().trim()
            "${doc.title()}\n\n${if (text.length > 6000) text.take(6000) + "\n…(已截断)" else text}"
        } catch (e: Exception) {
            "抓取失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun title(url: String): String = withContext(Dispatchers.IO) {
        try {
            Jsoup.connect(url).userAgent(UA).timeout(15000).get().title().ifBlank { "（无标题）" }
        } catch (e: Exception) {
            "获取失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun links(url: String): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url).userAgent(UA).timeout(15000).get()
            val links = doc.select("a[href]").mapNotNull { a ->
                val href = a.absUrl("href").ifBlank { null }
                if (href == null || href.startsWith("javascript:") || href.startsWith("mailto:")) null
                else "${a.text().ifBlank { href }} → $href"
            }.distinct().take(20)
            if (links.isEmpty()) "未找到链接" else links.joinToString("\n")
        } catch (e: Exception) {
            "获取失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.bing.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
            val doc = Jsoup.connect(url).userAgent(UA).timeout(15000).get()
            val results = doc.select("li.b_algo").take(6).map { li ->
                val a = li.selectFirst("h2 a")
                val p = li.selectFirst(".b_caption p") ?: li.selectFirst("p")
                "${a?.text()?.ifBlank { "（无标题）" }}\n${a?.absUrl("href")}\n${p?.text()?.ifBlank { "" }}"
            }
            if (results.isEmpty()) "未搜索到结果" else results.joinToString("\n---\n")
        } catch (e: Exception) {
            "搜索失败：${e.message ?: e.javaClass.simpleName}"
        }
    }
}