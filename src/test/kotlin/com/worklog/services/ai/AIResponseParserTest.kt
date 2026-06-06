package com.worklog.services.ai

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AIResponseParserTest {
    private val parser = AIResponseParser(Json { ignoreUnknownKeys = true })

    @Test
    fun `extracts OpenAI message content`() {
        val response = """{"choices":[{"message":{"content":"summary"}}]}"""

        assertEquals("summary", parser.extractOpenAIContent(response))
    }

    @Test
    fun `extracts custom content by simple json path`() {
        val response = """{"data":{"items":[{"text":"result"}]}}"""

        assertEquals("result", parser.extractCustomContent(response, "data.items[0].text"))
    }
}
