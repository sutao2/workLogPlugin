package com.worklog.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.worklog.models.ReviewResult

@Service(Service.Level.PROJECT)
class PreCommitAutoReviewService(private val project: Project) {

    @Volatile
    private var lastCompletedResult: ReviewResult? = null

    private val reviewService: CodeReviewService
        get() = project.getService(CodeReviewService::class.java)

    fun getLastResult(): ReviewResult? = lastCompletedResult

    fun cacheResult(result: ReviewResult) {
        lastCompletedResult = result
    }

    fun clearCache() {
        lastCompletedResult = null
    }
}
