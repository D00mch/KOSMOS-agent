package com.dumch.anth

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import com.dumch.tool.files.ToolDeleteFile
import com.dumch.tool.files.ToolFindTextInFiles
import com.dumch.tool.files.ToolListFiles
import com.dumch.tool.files.ToolModifyFile
import com.dumch.tool.files.ToolNewFile
import com.dumch.tool.files.ToolReadFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.collections.iterator

class AnthropicAgent(
    private val client: AnthropicClient,
    private val tools: Map<String, AnthropicToolSetup>,
    private val userMessages: Flow<String>,
) {
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var messagesJob: Job? = null

    fun run(): Flow<String> {
        stop()
        val results = MutableSharedFlow<String>(replay = 1)
        messagesJob = ioScope.launch {
            processMessages(results)
        }
        return results
    }

    fun stop() {
        messagesJob?.cancel()
    }

    private suspend fun processMessages(flow: MutableSharedFlow<String>) {
        val conversation = ArrayList<MessageParam>()
        userMessages.collect { userText ->
            val userMessageParam = MessageParam.Companion.builder()
                .role(MessageParam.Role.USER)
                .content(userText)
                .build()
            conversation.add(userMessageParam)

            repeat(times = 20) { // infinite loop protection
                val response = continueChat(conversation)
                conversation.add(response.toParam())

                val toolResults = ArrayList<ToolResultBlockParam>()
                for (content in response.content()) {
                    when {
                        content.isToolUse() -> toolResults.add(executeTool(content.asToolUse()))
                        content.isText() -> flow.emit(content.asText().text())
                    }
                }
                if (toolResults.isEmpty()) return@repeat
                val toolContentBlockParams = toolResults.map(ContentBlockParam.Companion::ofToolResult)
                val toolUseResultMessageParam = MessageParam.Companion.builder()
                    .role(MessageParam.Role.USER)
                    .content(MessageParam.Content.ofBlockParams(toolContentBlockParams))
                    .build()
                conversation.add(toolUseResultMessageParam)
            }
        }
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
            .model(Model.Companion.CLAUDE_3_5_SONNET_20241022)
            .maxTokens(1024)
            .temperature(1.0)
            .messages(conversation)

        for ((_, toolDesc) in tools) {
            val tool = Tool.Companion.builder()
                .name(toolDesc.name)
                .description(toolDesc.description)
                .inputSchema(toolDesc.inputSchema)
                .build()
            paramsBuilder.addTool(tool)
        }

        if (conversation.count() <= 1) {
            paramsBuilder.system("Help me fix the coding problem with kotlin project.")
        }
        return client.messages().create(paramsBuilder.build())
    }

    companion object {
        fun instance(userInputFlow: Flow<String>): AnthropicAgent {
            val client: AnthropicClient = AnthropicOkHttpClient.fromEnv()
            val tools: Map<String, AnthropicToolSetup> = listOf(
                ToolReadFile.toAnthropic(),
                ToolListFiles.toAnthropic(),
                ToolNewFile.toAnthropic(),
                ToolDeleteFile.toAnthropic(),
                ToolModifyFile.toAnthropic(),
                ToolFindTextInFiles.toAnthropic(),
            ).associateBy { it.name }
            return AnthropicAgent(client, tools, userInputFlow)
        }
    }
}