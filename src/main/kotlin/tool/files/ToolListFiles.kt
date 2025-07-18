package com.dumch.tool.files

import com.dumch.tool.ToolSetup

object ToolListFiles : ToolSetup<ToolListFiles.Input> {
    override val name = TODO()
    override val description = TODO()

    override fun invoke(input: Input): String {
        TODO()
    }

    data class Input(
        val path: String = "."
    )
}
