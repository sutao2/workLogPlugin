package com.worklog.services.review

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewResultParserTest {
    private val parser = ReviewResultParser(Json { ignoreUnknownKeys = true })

    @Test
    fun `parses structured review issue`() {
        val content = """
            发现问题。

            ${ReviewPromptBuilder.STRUCTURED_RESULT_START}
            {
              "issues": [
                {
                  "file": "App.kt",
                  "line": 42,
                  "severity": "HIGH",
                  "title": "空指针风险",
                  "message": "需要先判空"
                }
              ]
            }
            ${ReviewPromptBuilder.STRUCTURED_RESULT_END}
        """.trimIndent()

        val issues = parser.parse(content, listOf("src/main/App.kt"))

        assertEquals(1, issues.size)
        assertEquals("src/main/App.kt", issues.first().filePath)
        assertEquals(42, issues.first().line)
        assertEquals("HIGH", issues.first().severity)
    }

    @Test
    fun `keeps structured issue line when it exists in locator diff`() {
        val content = """
            发现问题。

            ${ReviewPromptBuilder.STRUCTURED_RESULT_START}
            {
              "issues": [
                {
                  "file": "App.kt",
                  "line": 42,
                  "severity": "HIGH",
                  "title": "空指针风险",
                  "message": "需要先判空"
                }
              ]
            }
            ${ReviewPromptBuilder.STRUCTURED_RESULT_END}
        """.trimIndent()
        val locatorDiff = """
            diff --git a/src/main/App.kt b/src/main/App.kt
            +++ b/src/main/App.kt
            @@ -40,3 +40,3 @@
            >> src/main/App.kt:42
            +dangerousCall()
        """.trimIndent()

        val issues = parser.parse(content, listOf("src/main/App.kt"), locatorDiff)

        assertEquals(42, issues.first().line)
    }

    @Test
    fun `clears structured issue line when it is not in locator diff`() {
        val content = """
            发现问题。

            ${ReviewPromptBuilder.STRUCTURED_RESULT_START}
            {
              "issues": [
                {
                  "file": "App.kt",
                  "line": 99,
                  "severity": "HIGH",
                  "title": "空指针风险",
                  "message": "需要先判空"
                }
              ]
            }
            ${ReviewPromptBuilder.STRUCTURED_RESULT_END}
        """.trimIndent()
        val locatorDiff = """
            diff --git a/src/main/App.kt b/src/main/App.kt
            +++ b/src/main/App.kt
            @@ -40,3 +40,3 @@
            >> src/main/App.kt:42
            +dangerousCall()
        """.trimIndent()

        val issues = parser.parse(content, listOf("src/main/App.kt"), locatorDiff)

        assertEquals(null, issues.first().line)
    }

    @Test
    fun `returns no issues when no finding marker is present`() {
        val issues = parser.parse("未发现明显问题", listOf("src/main/App.kt"))

        assertTrue(issues.isEmpty())
    }
}
