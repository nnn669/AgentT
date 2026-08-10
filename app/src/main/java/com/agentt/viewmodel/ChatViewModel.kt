package com.agentt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentt.data.model.*
import com.agentt.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    // 聊天会话列表
    val sessions: StateFlow<List<ChatSession>> = repository.sessions

    // AI 供应商列表
    val providers: StateFlow<List<AIProvider>> = repository.providers

    // 当前选中的会话
    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    // 当前会话的消息
    val currentMessages: StateFlow<List<Message>> = _selectedSessionId
        .map { id -> if (id != null) repository.getMessages(id) else MutableStateFlow(emptyList()) }
        .flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前选中的会话
    val selectedSession: StateFlow<ChatSession?> = combine(
        _selectedSessionId, sessions
    ) { id, list -> list.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 是否正在加载
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 选择会话
    fun selectSession(sessionId: String) {
        _selectedSessionId.value = sessionId
    }

    // 创建新会话
    fun createSession() {
        val session = repository.createSession()
        _selectedSessionId.value = session.id
    }

    // 删除会话
    fun deleteSession(sessionId: String) {
        repository.deleteSession(sessionId)
        if (_selectedSessionId.value == sessionId) {
            _selectedSessionId.value = null
        }
    }

    // 发送消息
    fun sendMessage(content: String) {
        val sessionId = _selectedSessionId.value ?: return
        if (content.isBlank()) return

        val userMessage = Message(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = content
        )
        repository.addMessage(sessionId, userMessage)

        // 模拟 AI 回复
        _isLoading.value = true
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            val aiMessage = Message(
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "你好！我是 AgentT AI 助手。你刚才说：\"$content\"\n\n目前我正处于开发阶段，完整功能即将上线。"
            )
            repository.addMessage(sessionId, aiMessage)
            _isLoading.value = false
        }
    }

    // 添加供应商
    fun addProvider(provider: AIProvider) {
        repository.addProvider(provider)
    }

    // 删除供应商
    fun removeProvider(providerId: String) {
        repository.removeProvider(providerId)
    }

    // 更新供应商
    fun updateProvider(provider: AIProvider) {
        repository.updateProvider(provider)
    }

    // 返回会话列表
    fun backToSessionList() {
        _selectedSessionId.value = null
    }
}
