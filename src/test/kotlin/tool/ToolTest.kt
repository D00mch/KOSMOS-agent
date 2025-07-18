package tool

import com.dumch.tool.files.ToolListFiles
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolTest {

    @Test
    fun `test ToolListFiles`() {
        val result = ToolListFiles(ToolListFiles.Input("gradle/wrapper"))
        assertEquals("[gradle-wrapper.jar,gradle-wrapper.properties]", result)

        val resources = ToolListFiles(ToolListFiles.Input("src/test/resources"))
        assertEquals("[directory/,directory/file.txt,test.txt]", resources)
        println(resources)
    }
}