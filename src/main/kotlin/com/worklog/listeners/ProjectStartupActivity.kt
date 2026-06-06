package com.worklog.listeners

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.worklog.services.PreCommitHookService
import com.worklog.services.ReminderService

/**
 * 项目启动时初始化需要自动运行的服务。
 */
class ProjectStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(ReminderService::class.java).start()
        runCatching {
            project.getService(PreCommitHookService::class.java).installOrUpdateHookIfNeeded()
        }
    }
}
