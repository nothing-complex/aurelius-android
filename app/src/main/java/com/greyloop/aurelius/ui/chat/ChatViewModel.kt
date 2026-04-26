package com.greyloop.aurelius.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greyloop.aurelius.domain.model.Chat
import com.greyloop.aurelius.domain.model.Message
import com.greyloop.aurelius.domain.model.Role
import com.greyloop.aurelius.domain.usecase.CreateChatUseCase
import com.greyloop.aurelius.domain.usecase.GetChatUseCase
import com.greyloop.aurelius.domain.usecase.GetMessagesUseCase
import com.greyloop.aurelius.domain.usecase.SendMessageUseCase
import com.greyloop.aurelius.domain.usecase.UpdateChatUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val chat: Chat? = null,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val streamingContent: String = "",
    val error: String? = null
)

sealed class ChatEvent {
    data class Error(val message: String) : ChatEvent()
    data object ScrollToBottom : ChatEvent()
}

class ChatViewModel(
    private val chatId: String?,
    private val getChatUseCase: GetChatUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val createChatUseCase: CreateChatUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var currentChatId: String? = chatId

    init {
        if (chatId != null) {
            loadChat(chatId)
            observeMessages(chatId)
        }
    }

    private fun loadChat(id: String) {
        viewModelScope.launch {
            getChatUseCase(id)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
                .collect { chat ->
                    _uiState.value = _uiState.value.copy(chat = chat)
                }
        }
    }

    private fun observeMessages(id: String) {
        viewModelScope.launch {
            getMessagesUseCase(id)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
                .collect { messages ->
                    _uiState.value = _uiState.value.copy(messages = messages)
                }
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val content = _uiState.value.inputText.trim()
        if (content.isEmpty()) return

        val id = currentChatId ?: return

        _uiState.value = _uiState.value.copy(
            inputText = "",
            isLoading = true,
            streamingContent = ""
        )

        viewModelScope.launch {
            sendMessageUseCase(
                chatId = id,
                content = content,
                onStreamingUpdate = { content ->
                    _uiState.value = _uiState.value.copy(streamingContent = content)
                },
                onComplete = { message ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + message,
                        isLoading = false,
                        streamingContent = ""
                    )
                    viewModelScope.launch { _events.emit(ChatEvent.ScrollToBottom) }
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error
                    )
                    viewModelScope.launch { _events.emit(ChatEvent.Error(error)) }
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
