package com.worklog.services.git

import kotlin.test.Test
import kotlin.test.assertEquals

class GitLogParserTest {
    @Test
    fun `parses commits and applies file filter`() {
        val separator = GitLogParser.COMMIT_FIELD_SEPARATOR
        val output = """
            abcdef123${separator}abcdef1${separator}Add feature${separator}Alice${separator}alice@example.com${separator}1700000000
            src/App.kt
            build/output.txt

            123456789${separator}1234567${separator}Fix bug${separator}Alice${separator}alice@example.com${separator}1700000100
            src/Bug.kt
        """.trimIndent()

        val commits = GitLogParser.parseCommits(output) { !it.startsWith("build/") }

        assertEquals(2, commits.size)
        assertEquals("Add feature", commits[0].message)
        assertEquals(listOf("src/App.kt"), commits[0].files)
        assertEquals("Fix bug", commits[1].message)
        assertEquals(listOf("src/Bug.kt"), commits[1].files)
    }
}
