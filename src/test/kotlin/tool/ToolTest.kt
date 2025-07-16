package tool

import com.dumch.tool.BadInputException
import com.dumch.tool.files.ToolDeleteFile
import com.dumch.tool.files.ToolListFiles
import com.dumch.tool.files.ToolModifyFile
import com.dumch.tool.files.ToolNewFile
import com.dumch.tool.files.ToolReadFile
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolTest {

    @Test
    fun `test ToolReadFile`() {
        println(File("src/test/resources/test.txt").readText())
        val result = ToolReadFile(ToolReadFile.Input("src/test/resources/test.txt"))
        assertEquals("Test content\n", result)
    }

    @Test
    fun `test ToolListFiles`() {
        val result = ToolListFiles(ToolListFiles.Input("gradle/wrapper"))
        assertEquals("[gradle-wrapper.jar,gradle-wrapper.properties]", result)

        val resources = ToolListFiles(ToolListFiles.Input("src/test/resources"))
        assertEquals("[directory/,directory/file.txt,test.txt]", resources)
        println(resources)
    }

    @Test
    fun `test ToolNewFile, ToolModifyFile, ToolDeleteFile lifecycle`() {
        val content = "Test"
        val path = "src/test/resources/${UUID.randomUUID()}.txt"

        // create new file
        ToolNewFile(ToolNewFile.Input(path, text = content))
        val fileContent = ToolReadFile(ToolReadFile.Input(path))
        assertEquals(content, fileContent)

        // modify new
        val newContent = "New"
        ToolModifyFile(ToolModifyFile.Input(path, oldText = content, newText = newContent))
        val fileContentNew = ToolReadFile(ToolReadFile.Input(path))
        assertEquals(newContent, fileContentNew)

        // delete
        ToolDeleteFile(ToolDeleteFile.Input(path))
        assertThrows<BadInputException> { ToolReadFile(ToolReadFile.Input(path)) }
    }
}