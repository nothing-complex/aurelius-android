package com.greyloop.aurelius.domain.model

data class Chat(
    val id: String,
    val title: String,
    val preview: String,
    val createdAt: Long,
    val updatedAt: Long,
    val parentBranchId: String? = null,
    val personaId: String = "default",
    val summary: String? = null
)

data class Message(
    val id: String,
    val chatId: String,
    val role: Role,
    val content: String,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val videoUrl: String? = null,
    val attachmentName: String? = null,
    val attachmentType: AttachmentType? = null,
    val timestamp: Long,
    val parentMessageId: String? = null
)

enum class Role {
    USER, ASSISTANT
}

enum class AttachmentType {
    IMAGE, PDF, TEXT
}
