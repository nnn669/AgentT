package com.agentt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentt.data.model.AIProvider
import com.agentt.data.model.ChatSession
import com.agentt.data.model.Message
import com.agentt.data.model.MessageRole
import com.agentt.data.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val repository = ChatRepository()

    val sessions: StateFlow<List<ChatSession>> = repository.sessions
    val providers: StateFlow<List<AIProvider>> = repository.providers

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    val currentMessages: StateFlow<List<Message>> = _selectedSessionId
        .map { id -> if (id == null) MutableStateFlow(emptyList()) else repository.getMessages(id) }
        .flatMapLatest { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedSession: StateFlow<ChatSession?> = combine(
        _selectedSessionId,
        sessions
    ) { id, allSessions -> allSessions.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun selectSession(sessionId: String) {
        _selectedSessionId.value = sessionId
    }

    fun createSession() {
        _selectedSessionId.value = repository.createSession().id
    }

    fun deleteSession(sessionId: String) {
        repository.deleteSession(sessionId)
        if (_selectedSessionId.value == sessionId) _selectedSessionId.value = null
    }

    fun sendMessage(content: String) {
        val sessionId = _selectedSessionId.value ?: return
        if (content.isBlank() || _isLoading.value) return

        repository.addMessage(
            sessionId,
            Message(sessionId = sessionId, role = MessageRole.USER, content = content)
        )
        _isLoading.value = true
        viewModelScope.launch {
            delay(450)
            repository.addMessage(
                sessionId,
                Message(
                    sessionId = sessionId,
                    role = MessageRole.ASSISTANT,
                    content = "这是界面壳的本地演示回复。真实模型、工具调用和数据持久化将在后续阶段接入。"
                )
            )
            _isLoading.value = false
        }
    }

    fun addProvider(provider: AIProvider) = repository.addProvider(provider)
    fun removeProvider(providerId: String) = repository.removeProvider(providerId)
    fun updateProvider(provider: AIProvider) = repository.updateProvider(provider)

    fun backToSessionList() {
        _selectedSessionId.value = null
    }
}
