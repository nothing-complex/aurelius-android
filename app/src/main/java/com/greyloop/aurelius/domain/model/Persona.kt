package com.greyloop.aurelius.domain.model

data class Persona(
    val id: String,
    val name: String,
    val description: String,
    val avatarUrl: String?,
    val systemPrompt: String
)

object DefaultPersonas {
    val ASSISTANT = Persona(
        id = "assistant",
        name = "Aurelius",
        description = "A wise and helpful AI assistant",
        avatarUrl = null,
        systemPrompt = "You are Aurelius, a helpful and knowledgeable AI assistant."
    )

    val CREATIVE_WRITER = Persona(
        id = "creative_writer",
        name = "Muse",
        description = "An imaginative creative companion for writing",
        avatarUrl = null,
        systemPrompt = "You are Muse, a creative and imaginative AI companion specializing in creative writing, storytelling, and artistic expression."
    )

    val CODE_HELPER = Persona(
        id = "code_helper",
        name = "Forge",
        description = "A technical expert for coding and engineering",
        avatarUrl = null,
        systemPrompt = "You are Forge, a technical expert specializing in code, programming, software engineering, and problem-solving."
    )

    val DEBATE_PARTNER = Persona(
        id = "debate_partner",
        name = "Socratic",
        description = "A debate partner for exploring ideas through dialogue",
        avatarUrl = null,
        systemPrompt = "You are Socratic, a debate partner who helps explore ideas through thoughtful dialogue, challenging assumptions, and presenting multiple perspectives."
    )

    fun getById(id: String): Persona = when (id) {
        "assistant" -> ASSISTANT
        "creative_writer" -> CREATIVE_WRITER
        "code_helper" -> CODE_HELPER
        "debate_partner" -> DEBATE_PARTNER
        else -> ASSISTANT
    }

    val all = listOf(ASSISTANT, CREATIVE_WRITER, CODE_HELPER, DEBATE_PARTNER)
}
