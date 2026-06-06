package com.worklog.services.ai

import com.worklog.models.GitCommit
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class WorkLogPromptBuilderTest {
    @Test
    fun `includes code block only when code diff is available`() {
        val commit = GitCommit(
            hash = "abcdef",
            shortHash = "abcdef",
            message = "Add feature",
            author = "Alice",
            authorEmail = "alice@example.com",
            timestamp = Instant.EPOCH,
            files = listOf("src/App.kt"),
            diff = "diff content"
        )
        val template = """
            commits:
            {{commits}}
            {{#if hasCodeAccess}}
            code:
            {{code_diff}}
            {{/if}}
        """.trimIndent()

        val prompt = WorkLogPromptBuilder.build(listOf(commit), includeCode = true, userPromptTemplate = template)

        assertContains(prompt, "提交: Add feature")
        assertContains(prompt, "diff content")
    }

    @Test
    fun `strips code block when code access is disabled`() {
        val prompt = WorkLogPromptBuilder.build(
            commits = emptyList(),
            includeCode = false,
            userPromptTemplate = "{{#if hasCodeAccess}}secret {{code_diff}}{{/if}}"
        )

        assertFalse(prompt.contains("secret"))
    }
}
