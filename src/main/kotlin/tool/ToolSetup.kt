package com.dumch.tool

/**
 * [Input] should be a data class with all the properties annotated with the [InputParamDescription]
 */
interface ToolSetup<Input> {

    val name: String
    val description: String

    operator fun invoke(input: Input): String
}

class BadInputException(msg: String): Exception(msg)
