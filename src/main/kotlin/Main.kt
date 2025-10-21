package com.dumch

import com.dumch.agent.GraphBasedAgent
import com.dumch.db.VectorDB
import com.dumch.giga.GigaAuth
import com.dumch.giga.GigaChatAPI
import com.dumch.giga.GigaModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val AGENT_ALIAS = "🪐"

suspend fun main() {
    val agent = GraphBasedAgent(
        model = GigaModel.Max.alias,
        llmApi = GigaChatAPI(GigaAuth),
        vectorDb = VectorDB,
    )
    userInputFlow().collect { text ->
        val result = agent.execute(text)
        println(AGENT_ALIAS + result)
    }
}

private fun userInputFlow(): Flow<String> = flow {
    println("Type `exit` to quit")
    while (true) {
        print("> ")
        val input = readlnOrNull() ?: break
        if (input.lowercase() == "exit") break
        emit(input)
        println("\n")
    }
}