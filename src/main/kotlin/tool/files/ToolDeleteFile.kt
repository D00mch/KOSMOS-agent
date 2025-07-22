package com.dumch.tool.files

import com.dumch.tool.BadInputException
import com.dumch.tool.InputParamDescription
import com.dumch.tool.ToolSetup
import java.io.File

object ToolDeleteFile : ToolSetup<ToolDeleteFile.Input> {
    override val name = "DeleteFile"
    override val description = "Deletes a file at the given path."

    override fun invoke(input: Input): String {
        val file = File(input.path)
        if (!file.exists() || file.isDirectory) {
            throw BadInputException("Invalid file path: ${input.path}")
        }
        if (!FilesToolUtil.isPathSafe(file)) {
            throw BadInputException("Access denied: File path must be within project directory")
        }
        file.delete()
        return "File deleted at ${input.path}"
    }

    data class Input(
        @InputParamDescription("The path of the file to delete")
        val path: String
    )
}
