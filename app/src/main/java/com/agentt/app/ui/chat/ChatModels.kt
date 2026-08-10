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

    companion object {
        private const val KEY_CATEGORIES = "chat_categories"
        private const val KEY_SESSIONS = "chat_sessions"
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