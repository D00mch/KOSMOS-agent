package com.dumch.agent

import com.dumch.agent.engine.*
import com.dumch.agent.node.NodesCommon
import com.dumch.agent.node.NodesLLM
import com.dumch.db.VectorDB
import com.dumch.giga.*
import io.ktor.util.logging.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil

class GraphBasedAgent(
    private val model: String,
    private val llmApi: GigaChatAPI,
    private val tools: Map<String, GigaToolSetup> = GigaAgent.tools,
    private val vectorDb: VectorDB,
) {
    private val l = LoggerFactory.getLogger(GraphBasedAgent::class.java)
    private val nodesLLM = NodesLLM(llmApi)

    // Make sure summarization only happens after all tool requests from LLM are answered
    private val nodeSummarize: Node<GigaResponse.Chat, String> by graph(name = "Go to user") {
        nodeInput.edgeTo { ctx -> if (ctx.historyIsTooBig()) nodesLLM.summarize else NodesCommon.respToString }
        nodesLLM.summarize.edgeTo(NodesCommon.respToString)
        NodesCommon.respToString.edgeTo(nodeFinish)
    }

    private val nodeAppendAdditionalData: Node<String, String> = Node("appendActualInformation") { ctx ->
        val additionalMessage = appendActualInformation(ctx.input)
        if (additionalMessage == null) {
            ctx
        } else {
            val history = ArrayList(ctx.history).apply { add(additionalMessage) }
            ctx.map(history = history)
        }
    }

    private val settings = AgentSettings(
        model = model,
        temperature = 0.7f,
        tools = tools
    )
    private val allFunctions: List<GigaRequest.Function> = settings.tools.values.map { it.fn }
    private val initialCtx = AgentContext(
        input = "",
        settings = settings,
        history = emptyList(),
        tools = allFunctions,
        systemPrompt = SYSTEM_PROMPT
    )

    private val _ctx: MutableStateFlow<AgentContext<String>> = MutableStateFlow(initialCtx)
    val currentContext: StateFlow<AgentContext<String>> = _ctx

    private val runningJob = AtomicReference<Deferred<*>>()

    fun clearContext(): Boolean {
        cancelActiveJob()
        return _ctx.tryEmit(initialCtx)
    }

    fun cancelActiveJob() {
        runningJob.get()?.cancel(CancellationException("Cleared by force"))
    }

    /** Execute one job at a time */
    suspend fun execute(input: String): String {
        cancelActiveJob()
        val ctx = currentContext.value.copy(input = input)
        val result: Deferred<AgentContext<String>> = coroutineScope {
            async {
                buildGraph().start(ctx) { step, node, ctx ->
                    l.debug { "Step: $step, node: ${node.name}, input: ${ctx.input}, history: ${ctx.history.size}" }
                }
            }
        }
        runningJob.set(result)
        val newContext = result.await()
        _ctx.emit(newContext)
        return newContext.input
    }

    private fun buildGraph(): Graph<String, String> = buildGraph(name = "Agent") {
        nodeInput.edgeTo(nodeAppendAdditionalData)
        nodeAppendAdditionalData.edgeTo(NodesCommon.stringToReq)
        NodesCommon.stringToReq.edgeTo(nodesLLM.chat)
        nodesLLM.chat.edgeTo { ctx ->
            when (val output = ctx.input) {
                is GigaResponse.Chat.Error -> nodeSummarize
                is GigaResponse.Chat.Ok -> if (isToolUse(output)) NodesCommon.toolUse else nodeSummarize
            }
        }
        NodesCommon.toolUse.edgeTo(nodesLLM.chat)
        nodeSummarize.edgeTo(nodeFinish)
    }

    private fun isToolUse(input: GigaResponse.Chat.Ok): Boolean = input.choices.any { it.message.functionCall != null }

    private suspend fun appendActualInformation(userText: String): GigaRequest.Message? {
        if (userText.isBlank()) return null

        val embedding = llmApi.embeddings(GigaRequest.Embeddings(input = listOf(userText)))
        val result = vectorDb.searchSimilar(embedding.data.first().embedding, limit = 2)
            .joinToString(
                prefix = "Найденные в локальном хранилище данные:\n",
                separator = "\n",
            )

        return GigaRequest.Message(
            role = GigaMessageRole.user,
            content = result,
        )
    }

    private fun AgentContext<GigaResponse.Chat>.historyIsTooBig(
        threshold: Double = HISTORY_SUMMARIZE_THRESHOLD,
    ): Boolean {
        val model = GigaModel.entries.firstOrNull { it.alias == settings.model }
        val contextWindow = model?.maxTokens ?: MAX_TOKENS
        val estimatedTokens = systemPrompt.estimateTokenCount() +
                history.sumOf { it.content.estimateTokenCount() }
        return estimatedTokens >= contextWindow * threshold
    }

    private fun String.estimateTokenCount(): Int = ceil(length / APPROX_CHARS_PER_TOKEN).toInt()
}

private const val HISTORY_SUMMARIZE_THRESHOLD = 0.8
private const val APPROX_CHARS_PER_TOKEN = 4.0
private val SYSTEM_PROMPT = """
Ты программист-помощник, 10 лет пишешь код на Kotlin, Android и Backend. Стараешься писать простой и поддерживаемый код.
""".trimIndent()
