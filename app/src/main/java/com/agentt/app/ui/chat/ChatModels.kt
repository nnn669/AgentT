package com.agentt.app.ui.chat

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class ChatCategory(val id: String, val name: String)

data class ChatSession(
    val id: String,
    val title: String,
    val categoryId: String?,
    val updatedAt: Long
)

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val model: String? = null,
    val kind: String = "text" // text | think | tool | reply
)

data class AgentAction(
    val type: String,
    val tool: String = "",
    val url: String? = null,
    val query: String? = null,
    val content: String = ""
)

fun extractJson(content: String): String? {
    val t = content.trim()
    if (t.startsWith("{")) {
        val s = t.indexOf('{')
        val e = t.lastIndexOf('}')
        return if (s >= 0 && e > s) t.substring(s, e + 1) else null
    }
    val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
    val m = fence.find(content)
    if (m != null) return m.groupValues[1].trim()
    return null
}

fun parseActionStream(content: String): List<AgentAction>? {
    val json = extractJson(content) ?: return null
    return try {
        val obj = JSONObject(json)
        val arr = obj.optJSONArray("actions") ?: return null
        val list = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    AgentAction(
                        type = o.optString("type"),
                        tool = o.optString("tool"),
                        url = o.optString("url").ifBlank { null },
                        query = o.optString("query").ifBlank { null },
                        content = o.optString("content")
                    )
                )
            }
        }.filter { it.type.isNotBlank() }
        if (list.isEmpty()) null else list
    } catch (_: Exception) {
        null
    }
}

class ChatStore(private val prefs: SharedPreferences) {

    fun loadCategories(): List<ChatCategory> {
        val raw = prefs.getString(KEY_CATEGORIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(ChatCategory(id = o.optString("id"), name = o.optString("name")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCategories(list: List<ChatCategory>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString(KEY_CATEGORIES, arr.toString()).apply()
    }

    fun loadSessions(): List<ChatSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ChatSession(
                            id = o.optString("id"),
                            title = o.optString("title"),
                            categoryId = if (o.isNull("categoryId")) null else o.optString("categoryId"),
                            updatedAt = o.optLong("updatedAt")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSessions(list: List<ChatSession>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("categoryId", it.categoryId ?: JSONObject.NULL)
                    .put("updatedAt", it.updatedAt)
            )
        }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    fun loadMessages(sessionId: String): List<ChatMessage> {
        val raw = prefs.getString(KEY_MSG_PREFIX + sessionId, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ChatMessage(
                            id = o.optString("id"),
                            role = o.optString("role"),
                            content = o.optString("content"),
                            model = o.optString("model").ifBlank { null },
                            kind = o.optString("kind").ifBlank { "text" }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(sessionId: String, list: List<ChatMessage>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("role", it.role)
                    .put("content", it.content)
                    .put("model", it.model ?: JSONObject.NULL)
                    .put("kind", it.kind)
            )
        }
        prefs.edit().putString(KEY_MSG_PREFIX + sessionId, arr.toString()).apply()
    }

    companion object {
        private const val KEY_CATEGORIES = "chat_categories"
        private const val KEY_SESSIONS = "chat_sessions"
        private const val KEY_MSG_PREFIX = "chat_msgs_"
        fun from(context: Context): ChatStore =
            ChatStore(context.getSharedPreferences("agentt_chat", Context.MODE_PRIVATE))
    }
}

fun createChatSession(store: ChatStore, categoryId: String? = null): ChatSession {
    val s = ChatSession(
        id = UUID.randomUUID().toString(),
        title = "新对话",
        categoryId = categoryId,
        updatedAt = System.currentTimeMillis()
    )
    store.saveSessions(store.loadSessions() + s)
    return s
}