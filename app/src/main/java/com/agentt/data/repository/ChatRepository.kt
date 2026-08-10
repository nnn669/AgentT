package com.agentt.data.repository

import com.agentt.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatRepository {

    private val _sessions = MutableStateFlow<List<ChatSession>>(
        listOf(
            ChatSession(title = "欢迎使用 AgentT", lastMessage = "你好！我是 AgentT，你的 AI 助手"),
            ChatSession(title = "示例对话", lastMessage = "这是一个示例消息")
        )
    )
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _messages = mutableMapOf<String, MutableStateFlow<List<Message>>>()

    private val _providers = MutableStateFlow<List<AIProvider>>(emptyList())
    val providers: StateFlow<List<AIProvider>> = _providers.asStateFlow()

    fun getMessages(sessionId: String): StateFlow<List<Message>> {
        return _messages.getOrPut(sessionId) {
            MutableStateFlow(emptyList())
        }
    }

    fun createSession(title: String = "新对话"): ChatSession {
        val session = ChatSession(title = title)
        _sessions.value = listOf(session) + _sessions.value
        _messages[session.id] = MutableStateFlow(emptyList())
        return session
    }

    fun deleteSession(sessionId: String) {
        _sessions.value = _sessions.value.filter { it.id != sessionId }
        _messages.remove(sessionId)
    }

    fun addMessage(sessionId: String, message: Message) {
        val flow = _messages.getOrPut(sessionId) {
            MutableStateFlow(emptyList())
        }
        flow.value = flow.value + message

        // 更新会话的 lastMessage
        _sessions.value = _sessions.value.map {
            if (it.id == sessionId) {
                it.copy(
                    lastMessage = message.content.take(50),
                    updatedAt = System.currentTimeMillis()
                )
            } else it
        }
    }

    fun addProvider(provider: AIProvider) {
        _providers.value = _providers.value + provider
    }

    fun removeProvider(providerId: String) {
        _providers.value = _providers.value.filter { it.id != providerId }
    }

    fun updateProvider(provider: AIProvider) {
        _providers.value = _providers.value.map {
            if (it.id == provider.id) provider else it
        }
    }
}
