package com.agentt.app.ui.chat

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class ChatCategory(val id: String, val name: String)

data class ChatSession(val id: String, val title: String, val categoryId: String?, val updatedAt: Long)

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val model: String? = null,
    val kind: String = "text"
)

data class AgentAction(
    val type: String,
    val tool: String = "",
    val url: String? = null,
    val query: String? = null,
    val content: String = "",
    val command: String? = null,
    val backend: String = "LOCAL",
    val timeoutMs: Long = 30_000,
    val maxOutputChars: Int = 256_000
)

fun extractJson(content: String): String? {
    val t = content.trim()
    if (t.startsWith("{")) {
        val s = t.indexOf('{')
        val e = t.lastIndexOf('}')
        return if (s >= 0 && e > s) t.substring(s, e + 1) else null
    }
    return Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(content)?.groupValues?.get(1)?.trim()
}

fun parseActionStream(content: String): List<AgentAction>? {
    val json = extractJson(content) ?: return null
    return try {
        val arr = JSONObject(json).optJSONArray("actions") ?: return null
        val list = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val rawType = o.optString("type")
                if (rawType.isBlank()) continue
                // Keep ChatScreen's existing tool branch as the single execution path.
                // The original action type remains visible in the model protocol tests.
                val isTerminal = rawType.equals("terminal", ignoreCase = true)
                add(AgentAction(
                    type = if (isTerminal) "browser" else rawType,
                    tool = if (isTerminal) "terminal" else o.optString("tool"),
                    url = o.optString("url").ifBlank { null },
                    query = if (isTerminal) o.toString() else o.optString("query").ifBlank { null },
                    content = o.optString("content"),
                    command = o.optString("command").ifBlank { null },
                    backend = o.optString("backend", "LOCAL").uppercase(),
                    timeoutMs = o.optLong("timeout_ms", 30_000).coerceIn(1_000, 120_000),
                    maxOutputChars = o.optInt("max_output_chars", 256_000).coerceIn(1_024, 512_000)
                ))
            }
        }
        list.takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }
}

class ChatStore(private val prefs: SharedPreferences) {
    fun loadCategories(): List<ChatCategory> {
        val raw = prefs.getString(KEY_CATEGORIES, null) ?: return emptyList()
        return try { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(ChatCategory(o.optString("id"), o.optString("name"))) } } } catch (_: Exception) { emptyList() }
    }
    fun saveCategories(list: List<ChatCategory>) { prefs.edit().putString(KEY_CATEGORIES, JSONArray().apply { list.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } }.toString()).apply() }
    fun loadSessions(): List<ChatSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(ChatSession(o.optString("id"), o.optString("title"), if (o.isNull("categoryId")) null else o.optString("categoryId"), o.optLong("updatedAt"))) } } } catch (_: Exception) { emptyList() }
    }
    fun saveSessions(list: List<ChatSession>) { prefs.edit().putString(KEY_SESSIONS, JSONArray().apply { list.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("categoryId", it.categoryId ?: JSONObject.NULL).put("updatedAt", it.updatedAt)) } }.toString()).apply() }
    fun loadMessages(sessionId: String): List<ChatMessage> {
        val raw = prefs.getString(KEY_MSG_PREFIX + sessionId, null) ?: return emptyList()
        return try { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(ChatMessage(o.optString("id"), o.optString("role"), o.optString("content"), o.optString("model").ifBlank { null }, o.optString("kind").ifBlank { "text" })) } } } catch (_: Exception) { emptyList() }
    }
    fun saveMessages(sessionId: String, list: List<ChatMessage>) { prefs.edit().putString(KEY_MSG_PREFIX + sessionId, JSONArray().apply { list.forEach { put(JSONObject().put("id", it.id).put("role", it.role).put("content", it.content).put("model", it.model ?: JSONObject.NULL).put("kind", it.kind)) } }.toString()).apply() }
    companion object {
        private const val KEY_CATEGORIES = "chat_categories"
        private const val KEY_SESSIONS = "chat_sessions"
        private const val KEY_MSG_PREFIX = "chat_msgs_"
        fun from(context: Context): ChatStore = ChatStore(context.getSharedPreferences("agentt_chat", Context.MODE_PRIVATE))
    }
}

fun createChatSession(store: ChatStore, categoryId: String? = null): ChatSession {
    val s = ChatSession(UUID.randomUUID().toString(), "新对话", categoryId, System.currentTimeMillis())
    store.saveSessions(store.loadSessions() + s)
    return s
}