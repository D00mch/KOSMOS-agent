package com.dumch

import com.dumch.giga.GigaAgent
import com.dumch.giga.GigaAuth
import com.dumch.giga.GigaChatAPI
import com.dumch.giga.GigaToolSetup
import com.dumch.giga.toGiga
import com.dumch.tool.files.ToolDeleteFile
import com.dumch.tool.files.ToolFindTextInFiles
import com.dumch.tool.files.ToolListFiles
import com.dumch.tool.files.ToolModifyFile
import com.dumch.tool.files.ToolNewFile
import com.dumch.tool.files.ToolReadFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private val tools: Map<String, GigaToolSetup> = listOf(
    ToolReadFile.toGiga(),
    ToolListFiles.toGiga(),
    ToolNewFile.toGiga(),
    ToolDeleteFile.toGiga(),
    ToolModifyFile.toGiga(),
    ToolFindTextInFiles.toGiga(),
).associateBy { it.fn.name }

suspend fun main() {
    val chat = GigaChatAPI(GigaAuth)
    val agent = GigaAgent(
        userInputFlow(),
        api = chat,
        tools = tools
    )
    agent.run().collect { text -> print("agent: $text") }
}

private fun userInputFlow(): Flow<String> = flow {
    println("Type `exit` to quit")
    while (true) {
        print("> ")
        val input = readLine() ?: break
        if (input.lowercase() == "exit") break
        emit(input)
        println("\n")
    }
}