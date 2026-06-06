package com.worklog.services.git

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitFileFilterTest {
    private val rules = GitFileFilterRules(
        excludedExtensions = setOf("png", "jar"),
        excludedDirectories = listOf("/build/", "/node_modules/"),
        maxSizeBytes = 1024
    )

    @Test
    fun `includes normal source file`() {
        assertTrue(GitFileFilter.shouldIncludePath("src/main/kotlin/App.kt", rules))
    }

    @Test
    fun `excludes configured file extension`() {
        assertFalse(GitFileFilter.shouldIncludePath("src/main/resources/logo.png", rules))
    }

    @Test
    fun `excludes configured directory`() {
        assertFalse(GitFileFilter.shouldIncludePath("frontend/node_modules/lib/index.js", rules))
    }
}
