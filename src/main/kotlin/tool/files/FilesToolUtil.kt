package com.dumch.tool.files

import java.io.File

object FilesToolUtil {
    private val projectRoot = File(".").canonicalFile

    fun isPathSafe(file: File): Boolean {
        val canonicalPath = file.canonicalFile
        return canonicalPath.startsWith(projectRoot)
    }
}