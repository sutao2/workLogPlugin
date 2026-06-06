package com.worklog.listeners

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.worklog.services.WorkLogService
import com.worklog.settings.AppSettingsState
import java.awt.datatransfer.StringSelection
import java.time.LocalDate

/**
 * 应用程序关闭监听器
 * 在 IDE 关闭时提醒用户填写工作日志
 * 注意：此监听器只能做提醒，无法阻止 IDE 关闭
 * 要阻止关闭请在 ProjectCloseListener 中处理
 */
class AppCloseListener : AppLifecycleListener {

    override fun appWillBeClosed(isRestart: Boolean) {
        CloseReminderState.withDialogLock {
            val settings = AppSettingsState.getInstance()

            // 如果禁用了关闭提醒，直接返回
            if (!settings.closeReminderEnabled) {
                return@withDialogLock
            }

            // 获取所有打开的项目
            val projects = ProjectManager.getInstance().openProjects

            // 检查是否有项目没有今日日志
            var hasProjectWithoutLog = false
            var hasProjectWithLog = false

            for (project in projects) {
                if (project.isDisposed) continue

                val workLogService = project.getService(WorkLogService::class.java)
                if (workLogService == null) continue

                if (workLogService.hasTodayWorkLog()) {
                    hasProjectWithLog = true
                } else {
                    hasProjectWithoutLog = true
                }
            }

            // 找到一个可用的 project 作为对话框父组件
            val activeProject = projects.firstOrNull { !it.isDisposed }

            // 在EDT线程中显示对话框
            ApplicationManager.getApplication().invokeAndWait {
                if (hasProjectWithoutLog) {
                    val options = arrayOf("知道了", "不再提醒")
                    val result = Messages.showDialog(
                        activeProject,
                        "今天还没有填写工作日志！\n\n" +
                        "建议：下次关闭项目时会提示您填写日志。\n" +
                        "您也可以随时通过工具窗口手动填写。",
                        "工作日志提醒",
                        options,
                        0,
                        Messages.getWarningIcon()
                    )

                    if (result == 1) {
                        settings.closeReminderEnabled = false
                    }
                } else if (hasProjectWithLog) {
                    if (activeProject != null) {
                        val workLogService = activeProject.getService(WorkLogService::class.java)
                        val workLog = workLogService?.loadWorkLog(LocalDate.now())
                        val content = workLog?.content ?: ""

                        if (content.isNotEmpty()) {
                            val options = arrayOf("复制并关闭", "直接关闭")
                            val result = Messages.showDialog(
                                activeProject,
                                "今天的工作日志已生成，是否需要复制到剪贴板？",
                                "工作日志提醒",
                                options,
                                0,
                                Messages.getQuestionIcon()
                            )

                            if (result == 0) {
                                CopyPasteManager.getInstance().setContents(StringSelection(content))
                                Messages.showInfoMessage(activeProject, "工作日志已复制到剪贴板", "复制成功")
                            }
                        }
                    }
                }
            }
        }
    }
}
