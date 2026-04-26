package com.greyloop.aurelius.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val preview: String,
    val createdAt: Long,
    val updatedAt: Long,
    val parentBranchId: String? = null,
    val personaId: String = "default",
    val summary: String? = null
)
