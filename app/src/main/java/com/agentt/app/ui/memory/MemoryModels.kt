package com.agentt.app.ui.memory

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AgentMemory(
    val id: String,
    val content: String,
    val assistantId: String = "",      // 关联的助手ID，空字符串表示全局记忆
    val tagId: String = "",             // 关联的标签ID，遵循助手分组原则
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("content", content)
        put("assistantId", assistantId)
        put("tagId", tagId)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): AgentMemory = AgentMemory(
            id = o.optString("id", UUID.randomUUID().toString()),
            content = o.optString("content"),
            assistantId = o.optString("assistantId"),
            tagId = o.optString("tagId"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
        fun encodeList(list: List<AgentMemory>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        fun decodeList(raw: String): List<AgentMemory> = try {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(fromJson(arr.getJSONObject(i))) }
        } catch (_: Exception) { emptyList() }
    }
}

class MemoryStore(private val prefs: SharedPreferences) {
    private val KEY_MEMORIES = "agent_memories_v1"

    fun load(): List<AgentMemory> {
        val raw = prefs.getString(KEY_MEMORIES, null) ?: return emptyList()
        return AgentMemory.decodeList(raw)
    }

    fun save(list: List<AgentMemory>) {
        prefs.edit().putString(KEY_MEMORIES, AgentMemory.encodeList(list)).apply()
    }

    /** 获取所有记忆，按更新时间倒序 */
    fun loadSorted(): List<AgentMemory> =
        load().sortedByDescending { it.updatedAt }

    /** 获取某个助手的记忆（直接关联 + 通过标签关联 + 全局记忆） */
    fun memoriesForAssistant(assistantId: String, assistantTagId: String? = null): List<AgentMemory> {
        val all = load()
        return all.filter { mem ->
            mem.assistantId == assistantId ||
                mem.assistantId.isEmpty() ||
                (assistantTagId != null && mem.tagId == assistantTagId)
        }.sortedByDescending { it.updatedAt }
    }

    /** 获取某个标签下的所有记忆 */
    fun memoriesByTag(tagId: String): List<AgentMemory> =
        load().filter { it.tagId == tagId }.sortedByDescending { it.updatedAt }

    /** 获取全局记忆（未关联任何助手或标签） */
    fun globalMemories(): List<AgentMemory> =
        load().filter { it.assistantId.isEmpty() && it.tagId.isEmpty() }
            .sortedByDescending { it.updatedAt }

    /** 构建记忆上下文字符串，供注入系统提示词使用 */
    fun buildMemoryContext(assistantId: String, assistantTagId: String? = null): String {
        val memories = memoriesForAssistant(assistantId, assistantTagId)
        if (memories.isEmpty()) return ""
        val sb = StringBuilder("\n\n<agent_memory>\n以下是关于用户和对话上下文的重要记忆：\n")
        memories.forEachIndexed { idx, mem ->
            sb.append("${idx + 1}. ${mem.content}\n")
        }
        sb.append("</agent_memory>")
        return sb.toString()
    }

    fun add(memory: AgentMemory) {
        val list = load().toMutableList()
        list.add(memory)
        save(list)
    }

    fun update(updated: AgentMemory) {
        val list = load().toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx == -1) return
        list[idx] = updated.copy(updatedAt = System.currentTimeMillis())
        save(list)
    }

    fun delete(id: String) {
        val list = load().toMutableList()
        list.removeAll { it.id == id }
        save(list)
    }

    fun deleteByAssistant(assistantId: String) {
        val list = load().toMutableList()
        list.removeAll { it.assistantId == assistantId }
        save(list)
    }

    fun deleteByTag(tagId: String) {
        val list = load().toMutableList()
        list.removeAll { it.tagId == tagId }
        save(list)
    }

    companion object {
        private var instance: MemoryStore? = null
        fun from(context: Context): MemoryStore {
            if (instance == null) {
                instance = MemoryStore(context.getSharedPreferences("agentt_memories", Context.MODE_PRIVATE))
            }
            return instance!!
        }
        fun resetInstance() { instance = null }
    }
}