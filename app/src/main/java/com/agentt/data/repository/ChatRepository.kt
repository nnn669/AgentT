package com.agentt.data.repository

import com.agentt.data.model.AIProvider
import com.agentt.data.model.ChatSession
import com.agentt.data.model.Message
import com.agentt.data.model.MessageRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatRepository {
    private val welcomeSession = ChatSession(
        title = "AgentT 界面预览",
        lastMessage = "先完成 TIN 风格界面壳，再逐步接入功能。",
        updatedAt = System.currentTimeMillis()
    )
    private val designSession = ChatSession(
        title = "聊天窗口设计",
        lastMessage = "消息气泡、输入栏和抽屉均可操作。",
        updatedAt = System.currentTimeMillis() - 3_600_000
    )

    private val _sessions = MutableStateFlow(listOf(welcomeSession, designSession))
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _messages = mutableMapOf(
        welcomeSession.id to MutableStateFlow(
            listOf(
                Message(
                    sessionId = welcomeSession.id,
                    role = MessageRole.ASSISTANT,
                    content = "这是 AgentT 的 TIN 风格 Android 界面壳。当前版本用于确认布局、配色和操作流程。"
                ),
                Message(
                    sessionId = welcomeSession.id,
                    role = MessageRole.USER,
                    content = "先把聊天窗口和抽屉交互做好。"
                ),
                Message(
                    sessionId = welcomeSession.id,
                    role = MessageRole.ASSISTANT,
                    content = "已加入会话列表、聊天气泡、胶囊输入框、供应商抽屉和设置抽屉。"
                )
            )
        ),
        designSession.id to MutableStateFlow(
            listOf(
                Message(
                    sessionId = designSession.id,
                    role = MessageRole.USER,
                    content = "聊天页要保持 TIN 的轻量卡片和低对比度背景。"
                ),
                Message(
                    sessionId = designSession.id,
                    role = MessageRole.ASSISTANT,
                    content = "当前壳使用 TIN 的主色、14dp 卡片圆角、浅灰输入区和紧凑排版。"
                )
            )
        )
    )

    private val _providers = MutableStateFlow<List<AIProvider>>(emptyList())
    val providers: StateFlow<List<AIProvider>> = _providers.asStateFlow()

    fun getMessages(sessionId: String): StateFlow<List<Message>> =
        _messages.getOrPut(sessionId) { MutableStateFlow(emptyList()) }

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
        val flow = _messages.getOrPut(sessionId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value + message
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(
                    lastMessage = message.content.take(50),
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                session
            }
        }
    }

    fun addProvider(provider: AIProvider) {
        _providers.value = _providers.value + provider
    }

    fun removeProvider(providerId: String) {
        _providers.value = _providers.value.filter { it.id != providerId }
    }

    fun updateProvider(provider: AIProvider) {
        _providers.value = _providers.value.map { if (it.id == provider.id) provider else it }
    }
}
