package com.dumch

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.dumch.anth.AnthropicAgent
import com.dumch.anth.AnthropicToolSetup
import com.dumch.anth.toAnthropic
import com.dumch.tool.files.ToolDeleteFile
import com.dumch.tool.files.ToolListFiles
import com.dumch.tool.files.ToolModifyFile
import com.dumch.tool.files.ToolNewFile
import com.dumch.tool.files.ToolReadFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


suspend fun main() {
    val client: AnthropicClient = AnthropicOkHttpClient.fromEnv()
    val tools: Map<String, AnthropicToolSetup> = listOf(
        ToolReadFile.toAnthropic(),
        ToolListFiles.toAnthropic(),
        ToolNewFile.toAnthropic(),
        ToolDeleteFile.toAnthropic(),
        ToolModifyFile.toAnthropic(),
    ).associateBy { it.name }
    val agent = AnthropicAgent(client, tools, userInputFlow())
    val output = agent.run()
    output.collect { text -> println(text) }
}

private fun userInputFlow(): Flow<String> = flow {
    println("Type `exit` to quit")
    while (true) {
        print("> ")
        val input = readLine() ?: break
        if (input.lowercase() == "exit") break
        emit(input)
    }
}