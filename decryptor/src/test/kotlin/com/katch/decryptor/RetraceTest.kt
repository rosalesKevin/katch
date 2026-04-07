package com.katch.decryptor

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RetraceTest {

    @Test
    fun `deobfuscates class and method names using mapping`() {
        val mapping = tempMapping(
            """
            com.example.MyActivity -> p1.a:
                void onCreate() -> b
            com.example.Util -> p1.k:
                void doWork() -> a
            """.trimIndent()
        )

        val obfuscated = """
            java.lang.RuntimeException: test crash
            	at p1.k.a(Unknown Source:1)
            	at p1.a.b(Unknown Source:5)
        """.trimIndent()

        val result = retrace(mapping.absolutePath, obfuscated)

        assertTrue("Expected real class name", result.contains("com.example.Util"))
        assertTrue("Expected real method name", result.contains("doWork"))
        assertTrue("Expected real activity name", result.contains("com.example.MyActivity"))
    }

    @Test
    fun `preserves non-obfuscated exception message`() {
        val mapping = tempMapping("com.example.Foo -> p.F:")
        val input = "java.lang.NullPointerException: null"

        val result = retrace(mapping.absolutePath, input)

        assertTrue(result.contains("NullPointerException"))
    }

    @Test(expected = Exception::class)
    fun `throws when mapping file does not exist`() {
        retrace("/nonexistent/path/mapping.txt", "java.lang.Exception: test")
    }

    private fun tempMapping(content: String): File =
        Files.createTempFile("katch-test-mapping", ".txt").toFile()
            .also { it.writeText(content); it.deleteOnExit() }
}
