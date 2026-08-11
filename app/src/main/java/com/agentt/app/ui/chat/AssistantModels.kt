package com.agentt.app.ui.chat

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AgentAssistant(
    val id: String,
    val name: String,
    val avatar: String = "",
    val systemPrompt: String = "",
    val providerId: String = "",
    val modelId: String = "",
    val contextMessageSize: Int = 64,
    val streamOutput: Boolean = true,
    val searchEnabled: Boolean = false,
    val order: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("avatar", avatar)
        put("systemPrompt", systemPrompt)
        put("providerId", providerId)
        put("modelId", modelId)
        put("contextMessageSize", contextMessageSize)
        put("streamOutput", streamOutput)
        put("searchEnabled", searchEnabled)
        put("order", order)
    }

    companion object {
        fun fromJson(o: JSONObject): AgentAssistant = AgentAssistant(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            avatar = o.optString("avatar"),
            systemPrompt = o.optString("systemPrompt"),
            providerId = o.optString("providerId"),
            modelId = o.optString("modelId"),
            contextMessageSize = o.optInt("contextMessageSize", 64),
            streamOutput = o.optBoolean("streamOutput", true),
            searchEnabled = o.optBoolean("searchEnabled", false),
            order = o.optInt("order", 0)
        )
        fun encodeList(list: List<AgentAssistant>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        fun decodeList(raw: String): List<AgentAssistant> = try {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(fromJson(arr.getJSONObject(i))) }
        } catch (_: Exception) { emptyList() }
    }
}

data class AgentTag(
    val id: String,
    val name: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
    }
    companion object {
        fun fromJson(o: JSONObject): AgentTag = AgentTag(
            id = o.optString("id"),
            name = o.optString("name")
        )
        fun encodeList(list: List<AgentTag>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        fun decodeList(raw: String): List<AgentTag> = try {
            val arr = JSONArray(raw)
            buildList { for (i in 0 until arr.length()) add(fromJson(arr.getJSONObject(i))) }
        } catch (_: Exception) { emptyList() }
    }
}

class AssistantStore(private val prefs: SharedPreferences) {
    private val KEY_ASSISTANTS = "assistants_v1"
    private val KEY_CURRENT = "current_assistant_id_v1"

    fun load(): List<AgentAssistant> {
        val raw = prefs.getString(KEY_ASSISTANTS, null) ?: return emptyList()
        return AgentAssistant.decodeList(raw)
    }

    fun save(list: List<AgentAssistant>) {
        prefs.edit().putString(KEY_ASSISTANTS, AgentAssistant.encodeList(list)).apply()
    }

    fun currentId(): String? = prefs.getString(KEY_CURRENT, null)

    fun setCurrentId(id: String?) {
        prefs.edit().putString(KEY_CURRENT, id).apply()
    }

    fun currentAssistant(): AgentAssistant? {
        val id = currentId() ?: return null
        return load().firstOrNull { it.id == id }
    }

    fun add(assistant: AgentAssistant) {
        val list = load().toMutableList()
        list.add(assistant.copy(order = list.size))
        save(list)
        if (currentId() == null) setCurrentId(assistant.id)
    }

    fun update(updated: AgentAssistant) {
        val list = load().toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx == -1) return
        list[idx] = updated
        save(list)
    }

    fun delete(id: String) {
        val list = load().toMutableList()
        list.removeAll { it.id == id }
        save(list)
        if (currentId() == id) {
            setCurrentId(list.firstOrNull()?.id)
        }
    }

    fun duplicate(sourceId: String): String? {
        val list = load().toMutableList()
        val source = list.firstOrNull { it.id == sourceId } ?: return null
        val newId = UUID.randomUUID().toString()
        val baseName = source.name.trim()
        val existingNames = list.map { it.name }.toSet()
        var candidate = "${baseName} 副本"
        var counter = 2
        while (existingNames.contains(candidate)) {
            candidate = "${baseName} 副本 $counter"
            counter++
        }
        list.add(
            source.copy(
                id = newId,
                name = candidate,
                order = list.size
            )
        )
        save(list)
        return newId
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        val list = load().toMutableList()
        if (fromIndex < 0 || fromIndex >= list.size || toIndex < 0 || toIndex >= list.size) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        save(list)
    }

    companion object {
        fun from(context: Context): AssistantStore =
            AssistantStore(context.getSharedPreferences("agentt_assistants", Context.MODE_PRIVATE))
    }
}

class TagStore(private val prefs: SharedPreferences) {
    private val KEY_TAGS = "assistant_tags_v1"
    private val KEY_ASSIGN = "assistant_tag_map_v1"
    private val KEY_COLLAPSED = "assistant_tag_collapsed_v1"

    fun loadTags(): List<AgentTag> {
        val raw = prefs.getString(KEY_TAGS, null) ?: return emptyList()
        return AgentTag.decodeList(raw)
    }

    fun saveTags(list: List<AgentTag>) {
        prefs.edit().putString(KEY_TAGS, AgentTag.encodeList(list)).apply()
    }

    fun loadAssignment(): Map<String, String> {
        val raw = prefs.getString(KEY_ASSIGN, null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            jsonObjectKeys(o).associateWith { o.optString(it) }
        } catch (_: Exception) { emptyMap() }
    }

    fun saveAssignment(map: Map<String, String>) {
        prefs.edit().putString(KEY_ASSIGN, JSONObject().apply { map.forEach { put(it.key, it.value) } }.toString()).apply()
    }

    fun loadCollapsed(): Map<String, Boolean> {
        val raw = prefs.getString(KEY_COLLAPSED, null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            jsonObjectKeys(o).associateWith { o.optBoolean(it) }
        } catch (_: Exception) { emptyMap() }
    }

    fun saveCollapsed(map: Map<String, Boolean>) {
        prefs.edit().putString(KEY_COLLAPSED, JSONObject().apply { map.forEach { put(it.key, it.value) } }.toString()).apply()
    }

    fun createTag(name: String): String {
        val id = UUID.randomUUID().toString()
        val list = loadTags().toMutableList()
        list.add(AgentTag(id, name.trim()))
        saveTags(list)
        return id
    }

    fun renameTag(tagId: String, name: String) {
        val list = loadTags().toMutableList()
        val idx = list.indexOfFirst { it.id == tagId }
        if (idx == -1) return
        list[idx] = list[idx].copy(name = name.trim())
        saveTags(list)
    }

    fun deleteTag(tagId: String) {
        val list = loadTags().toMutableList()
        list.removeAll { it.id == tagId }
        saveTags(list)
        val assign = loadAssignment().toMutableMap()
        assign.entries.removeAll { it.value == tagId }
        saveAssignment(assign)
        val collapsed = loadCollapsed().toMutableMap()
        collapsed.remove(tagId)
        saveCollapsed(collapsed)
    }

    fun tagOfAssistant(assistantId: String): String? = loadAssignment()[assistantId]

    fun assignAssistant(assistantId: String, tagId: String?) {
        val map = loadAssignment().toMutableMap()
        if (tagId == null) map.remove(assistantId) else map[assistantId] = tagId
        saveAssignment(map)
    }

    fun toggleCollapsed(tagId: String) {
        val map = loadCollapsed().toMutableMap()
        map[tagId] = !(map[tagId] ?: false)
        saveCollapsed(map)
    }

    companion object {
        fun from(context: Context): TagStore =
            TagStore(context.getSharedPreferences("agentt_tags", Context.MODE_PRIVATE))
    }
}

/** Helper: extract keys from a JSONObject using names() for Android compatibility. */
private fun jsonObjectKeys(o: JSONObject): Set<String> {
    val names = o.names() ?: return emptySet()
    return buildSet { for (i in 0 until names.length()) add(names.optString(i)) }
}