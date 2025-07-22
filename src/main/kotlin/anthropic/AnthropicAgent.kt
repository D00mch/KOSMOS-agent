package com.dumch.anth

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.*
import com.dumch.tool.files.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class AnthropicAgent(
    private val client: AnthropicClient,
    private val tools: Map<String, AnthropicToolSetup>,
    private val model: Model = Model.CLAUDE_3_5_SONNET_20241022,
    private val userMessages: Flow<String>,
) {
    private val anthropicTools: List<ToolUnion> = tools.map { (_, tool) ->
        ToolUnion.ofTool(tool.tool)
    }

    fun run(): Flow<String> = channelFlow {
        val conversation = ArrayList<MessageParam>()
        userMessages.collect { userText ->
            trySummarize(conversation)
            val userMessageParam = MessageParam.Companion.builder()
                .role(MessageParam.Role.USER)
                .content(userText)
                .build()
            conversation.add(userMessageParam)

            for (i in 1..10) { // infinite loop protection
                if (!isActive) break
                val response = withContext(Dispatchers.IO) {
                    continueChat(conversation)
                }
                conversation.add(response.toParam())

                val toolAwaits = ArrayList<Deferred<ToolResultBlockParam>>()
                for (content in response.content()) {
                    when {
                        content.isToolUse() -> {
                            val deferred = async(Dispatchers.IO) { executeTool(content.asToolUse()) }
                            toolAwaits.add(deferred)
                        }

                        content.isText() -> send(content.asText().text())
                    }
                }
                if (toolAwaits.isEmpty()) break
                val toolResults = toolAwaits.awaitAll()
                val toolContentBlockParams = toolResults.map(ContentBlockParam.Companion::ofToolResult)
                val toolUseResultMessageParam = MessageParam.Companion.builder()
                    .role(MessageParam.Role.USER)
                    .content(MessageParam.Content.ofBlockParams(toolContentBlockParams))
                    .build()
                conversation.add(toolUseResultMessageParam)
            }
        }
    }

    private suspend fun trySummarize(conversation: ArrayList<MessageParam>) {
        if (conversation.size <= 2) return
        val msg = MessageCountTokensParams.builder().model(model).messages(conversation).build()
        val inputTokens: Long = client.messages().countTokens(msg).inputTokens()
        if (inputTokens < MAX_TOKENS * THRESHOLD_PCT) return

        val summary = withContext(Dispatchers.IO) {
            client.messages().create(
                MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(512)
                    .temperature(0.7)
                    .messages(conversation)
                    .system("Summarize the conversation so far")
                    .build()
            )
        }

        val lastMessage = conversation.last()
        conversation.clear()
        conversation.add(summary.toParam())
        conversation.add(lastMessage)
    }

    private fun executeTool(toolBlock: ToolUseBlock): ToolResultBlockParam {
        val name = toolBlock.name()
        val tool = tools[name] ?: return ToolResultBlockParam.Companion.builder()
            .content("Tool $name not found")
            .isError(true)
            .build()
        return tool.invoke(toolBlock)
    }

    private fun continueChat(conversation: List<MessageParam>): Message {
        val paramsBuilder = MessageCreateParams.Companion.builder()
            .model(model)
            .maxTokens(1024)
            .temperature(1.0)
            .messages(conversation)

        paramsBuilder.tools(anthropicTools)
        return client.messages().create(paramsBuilder.build())
    }

    companion object {
        private const val MAX_TOKENS = 8192 // TODO: get value based on the model
        private const val THRESHOLD_PCT = 0.9 // Start summarization on 90% fill up

        fun instance(
            userInputFlow: Flow<String>,
            model: Model = Model.CLAUDE_3_5_SONNET_20241022,
        ): AnthropicAgent {
            val client: AnthropicClient = AnthropicOkHttpClient.fromEnv()
            val tools: Map<String, AnthropicToolSetup> = listOf(
                ToolReadFile.toAnthropic(),
                ToolListFiles.toAnthropic(),
                ToolNewFile.toAnthropic(),
                ToolDeleteFile.toAnthropic(),
                ToolModifyFile.toAnthropic(),
                ToolFindTextInFiles.toAnthropic(),
            ).associateBy { it.tool.name() }
            return AnthropicAgent(client, tools, model, userInputFlow)
        }
    }
}