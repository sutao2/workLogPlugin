package com.worklog.listeners

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager
import com.worklog.services.WorkLogService
import com.worklog.settings.AppSettingsState
import com.worklog.ui.GenerateWorkLogDialog
import com.worklog.ui.ShutdownReminderDialog
import java.time.LocalDate

/**
 * 应用程序关闭监听器
 * 在 IDE 关闭时提醒用户填写工作日志
 */
class AppCloseListener : AppLifecycleListener {

    override fun appClosing() {
        val settings = AppSettingsState.getInstance()

        // 如果禁用了关闭提醒，直接返回
        if (!settings.closeReminderEnabled) {
            return
        }

        // 获取所有打开的项目
        val projects = ProjectManager.getInstance().openProjects

        // 检查每个项目是否有今日日志
        for (project in projects) {
            if (project.isDisposed) continue

            val workLogService = project.getService(WorkLogService::class.java)
            if (workLogService == null || workLogService.hasTodayWorkLog()) {
                continue  // 已有今日日志，跳过
            }

            // 显示提醒对话框（阻塞式）
            ApplicationManager.getApplication().invokeAndWait {
                showReminderDialog(project, workLogService)
            }

            // 只提醒一个项目即可
            break
        }
    }

    private fun showReminderDialog(project: Project, workLogService: WorkLogService) {
        val dialog = ShutdownReminderDialog(project)
        if (!dialog.showAndGet()) {
            // 用户点击了取消或关闭对话框，什么都不做
            return
        }

        when (dialog.getUserChoice()) {
            ShutdownReminderDialog.UserChoice.GENERATE -> {
                // 立即生成日志 - 打开工具窗口让用户填写
                openWorkLogToolWindow(project)
                // 自动触发生成对话框
                javax.swing.SwingUtilities.invokeLater {
                    triggerGenerateWorkLog(project)
                }
            }
            ShutdownReminderDialog.UserChoice.OPEN -> {
                // 打开工作日志窗口让用户手动填写
                openWorkLogToolWindow(project)
            }
            ShutdownReminderDialog.UserChoice.LATER -> {
                // 稍后填写，什么都不做
            }
            ShutdownReminderDialog.UserChoice.NEVER -> {
                // 不再提醒，更新设置
                val settings = AppSettingsState.getInstance()
                settings.closeReminderEnabled = false
            }
        }
    }

    private fun triggerGenerateWorkLog(project: Project) {
        // 这个方法会在工具窗口打开后调用，触发生成对话框
        // 具体实现将由 WorkLogToolWindow 提供
    }

    private fun openWorkLogToolWindow(project: Project) {
        try {
            val toolWindowManager = ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow("WorkLog")
            toolWindow?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
