package com.worklog.listeners

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseHandler
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.worklog.services.WorkLogService
import com.worklog.settings.AppSettingsState
import java.awt.datatransfer.StringSelection
import java.time.LocalDate

/**
 * 项目关闭处理器
 * 在项目关闭时检查是否需要填写工作日志
 */
class ProjectCloseListener : ProjectCloseHandler {

    companion object {
        @Volatile
        private var isShowingDialog = false
    }

    override fun canClose(project: Project): Boolean {
        // 防止重复弹窗
        if (isShowingDialog) {
            return false
        }

        val settings = AppSettingsState.getInstance()

        // 如果禁用了关闭提醒，允许关闭
        if (!settings.closeReminderEnabled) {
            return true
        }

        val workLogService = project.getService(WorkLogService::class.java) ?: return true

        // 检查是否已有今日日志
        val hasTodayLog = workLogService.hasTodayWorkLog()

        try {
            isShowingDialog = true

            if (hasTodayLog) {
                // 如果已有日志，提供复制选项
                val workLog = workLogService.loadWorkLog(LocalDate.now())
                val content = workLog?.content ?: ""

                val options = arrayOf("复制并关闭", "查看编辑", "直接关闭")
                val result = Messages.showDialog(
                    project,
                    "今天的工作日志已生成，是否需要复制到剪贴板？",
                    "工作日志提醒",
                    options,
                    0,
                    Messages.getQuestionIcon()
                )

                when (result) {
                    0 -> {
                        // 复制到剪贴板
                        CopyPasteManager.getInstance().setContents(StringSelection(content))
                        Messages.showInfoMessage(project, "工作日志已复制到剪贴板", "复制成功")
                        return true
                    }
                    1 -> {
                        // 打开工作日志窗口
                        val toolWindowManager = ToolWindowManager.getInstance(project)
                        val toolWindow = toolWindowManager.getToolWindow("WorkLog")
                        toolWindow?.show()
                        return false
                    }
                    2 -> {
                        // 直接关闭
                        return true
                    }
                }
            } else {
                // 如果没有日志，询问是否填写
                val result = Messages.showYesNoDialog(
                    project,
                    "今天还没有填写工作日志，是否现在填写？\n" +
                    "选择\"是\"将打开工作日志窗口，选择\"否\"将直接关闭项目。",
                    "工作日志提醒",
                    "立即填写",
                    "稍后再说",
                    Messages.getQuestionIcon()
                )

                if (result == Messages.YES) {
                    // 打开工作日志窗口
                    val toolWindowManager = ToolWindowManager.getInstance(project)
                    val toolWindow = toolWindowManager.getToolWindow("WorkLog")
                    toolWindow?.show()
                    return false
                }
            }

            return true
        } finally {
            isShowingDialog = false
        }
    }
}
