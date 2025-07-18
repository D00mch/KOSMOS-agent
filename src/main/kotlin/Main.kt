package com.dumch

import com.dumch.anth.AnthropicAgent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


suspend fun main() {
    val agent = AnthropicAgent.instance(userInputFlow())
    val output = agent.run()
    output.collect { text -> println(text) }
}

private fun userInputFlow(): Flow<String> = flow {
    emit("Type `exit` to quit")
    while (true) {
        emit("> ")
        val input = readLine() ?: break
        if (input.lowercase() == "exit") break
        emit(input)
    }
}