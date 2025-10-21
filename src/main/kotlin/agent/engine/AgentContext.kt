package com.dumch.agent.engine

import com.dumch.giga.GigaRequest
import com.dumch.giga.GigaToolSetup

// Immutable context threaded through the graph
data class AgentContext<I>(
    val input: I,
    val settings: AgentSettings,
    val history: List<GigaRequest.Message>,
    val tools: List<GigaRequest.Function>,
    val systemPrompt: String,
) {
    inline fun <reified O> map(
        settings: AgentSettings = this.settings,
        history: List<GigaRequest.Message> = this.history,
        activeTools: List<GigaRequest.Function> = this.tools,
        systemPrompt: String = this.systemPrompt,
        transform: (I) -> O = { it as O },
    ): AgentContext<O> = AgentContext(input = transform(input), settings, history, activeTools, systemPrompt)
}

data class AgentSettings(
    val model: String,
    val temperature: Float,
    val tools: Map<String, GigaToolSetup>
)
