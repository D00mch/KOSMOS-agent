package com.dumch.giga

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

object GigaResponse {

    data class Token(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("expires_at") val expiresAt: Date
    )

    sealed interface Chat {
        data class Ok(val choices: List<Choice>, val created: Long, val model: String) : Chat
        data class Error(val status: Int, val message: String) : Chat
    }

    data class Choice(
        val message: Message,
        val index: Int,
        @JsonProperty("finish_reason")
        val finishReason: String
    )

    data class Message(
        val content: String,
        val role: GigaMessageRole,
        @JsonProperty("function_call")
        val functionCall: FunctionCall? = null,
        @JsonProperty("functions_state_id")
        val functionsStateId: String?
    )

    data class FunctionCall(
        val name: String,
        val arguments: Map<String, Any>
    )

    data class Embeddings(
        val data: List<Embedding>,
        val model: String,
        @JsonProperty("object") val objectType: String,
    )

    data class Embedding(
        val embedding: List<Double>,
        val index: Int,
        @JsonProperty("object") val objectType: String? = null,
    )
}

object GigaRequest {
    data class Chat(
        val model: String = "GigaChat-Max",
        val messages: List<Message>,
        @JsonProperty("function_call")
        val functionCall: String = "auto",
        val functions: List<Function>? = null,
    )

    data class Message(
        val role: GigaMessageRole,
        val content: String, // Could be String or FunctionCall object
        @JsonProperty("functions_state_id")
        val functionsStateId: String? = null
    )

    data class Function(
        val name: String,
        val description: String,
        val parameters: Parameters
    )

    data class Parameters(
        val type: String,
        val properties: Map<String, Property>
    )

    data class Property(
        val type: String,
        val description: String? = null
    )

    data class Embeddings(
        val model: String = "Embeddings",
        val input: List<String>,
    )
}

@Suppress("EnumEntryName")
enum class GigaMessageRole { system, user, assistant, function }

const val MAX_TOKENS = 8192

enum class GigaModel(val alias: String, val maxTokens: Int) {
    Lite("GigaChat-2", MAX_TOKENS),
    Pro("GigaChat-Pro", MAX_TOKENS),
    Max("GigaChat-Max", MAX_TOKENS),
}

fun String.toSystemPromptMessage() = GigaRequest.Message(
    role = GigaMessageRole.system,
    content = this
)