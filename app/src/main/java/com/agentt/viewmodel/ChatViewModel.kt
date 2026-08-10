package com.agentt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentt.core.api.ApiFactory
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

    // 错误提示消息
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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

    // 清除提示消息
    fun clearMessage() {
        _message.value = null
    }

    // 发送消息（调用真实 AI API）
    fun sendMessage(content: String) {
        val sessionId = _selectedSessionId.value ?: return
        if (content.isBlank()) return

        val userMessage = Message(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = content
        )
        repository.addMessage(sessionId, userMessage)

        _isLoading.value = true
        viewModelScope.launch {
            try {
                // 获取可用的供应商
                val provider = repository.providers.value.firstOrNull { it.isEnabled }
                if (provider == null) {
                    _message.value = "请先在左侧抽屉中添加 AI 供应商"
                    repository.addMessage(
                        sessionId,
                        Message(
                            sessionId = sessionId,
                            role = MessageRole.ASSISTANT,
                            content = "⚠️ 还没有配置 AI 供应商\n\n请点击左上角菜单 → 添加供应商，填入 API Key 后即可开始对话。"
                        )
                    )
                    return@launch
                }

                // 校验 API Key
                if (provider.apiKey.isBlank()) {
                    _message.value = "供应商 ${provider.name} 的 API Key 未填写"
                    repository.addMessage(
                        sessionId,
                        Message(
                            sessionId = sessionId,
                            role = MessageRole.ASSISTANT,
                            content = "⚠️ 供应商 \"${provider.name}\" 还没有填写 API Key\n\n请到左侧抽屉中编辑该供应商，填入 API Key 后再试。"
                        )
                    )
                    return@launch
                }

                // 调用真实 API
                val client = ApiFactory.createClient(provider.type)
                val history = repository.getMessages(sessionId).value
                val model = provider.models.firstOrNull() ?: "gpt-4o"

                val reply = client.chatCompletion(
                    apiKey = provider.apiKey,
                    baseUrl = provider.baseUrl,
                    model = model,
                    messages = history
                )

                repository.addMessage(
                    sessionId,
                    Message(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        content = reply
                    )
                )
            } catch (e: Exception) {
                val errMsg = e.message?.take(300) ?: "未知错误"
                _message.value = "调用 AI 失败"
                repository.addMessage(
                    sessionId,
                    Message(
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT,
                        content = "⚠️ 调用 AI 失败：\n\n$errMsg"
                    )
                )
            } finally {
                _isLoading.value = false
            }
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
