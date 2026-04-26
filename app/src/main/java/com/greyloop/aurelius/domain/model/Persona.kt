package com.greyloop.aurelius.domain.model

data class Persona(
    val id: String,
    val name: String,
    val description: String,
    val avatarUrl: String?,
    val systemPrompt: String
)

object DefaultPersonas {
    val DEFAULT = Persona(
        id = "default",
        name = "Aurelius",
        description = "A wise and helpful AI assistant",
        avatarUrl = null,
        systemPrompt = "You are Aurelius, a helpful and knowledgeable AI assistant."
    )

    val CREATIVE = Persona(
        id = "creative",
        name = "Muse",
        description = "An imaginative creative companion",
        avatarUrl = null,
        systemPrompt = "You are Muse, a creative and imaginative AI companion."
    )

    val TECHNICAL = Persona(
        id = "technical",
        name = "Forge",
        description = "A technical expert for coding and engineering",
        avatarUrl = null,
        systemPrompt = "You are Forge, a technical expert specializing in code and engineering."
    )
}
