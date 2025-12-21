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
 * 这是唯一能真正阻止关闭的地方（通过返回 false）
 */
class ProjectCloseListener : ProjectCloseHandler {

    override fun canClose(project: Project): Boolean {
        // 使用共享状态管理器防止重复弹窗
        if (!CloseReminderState.tryAcquireDialogLock()) {
            return true  // 已经显示过对话框，允许关闭
        }

        val settings = AppSettingsState.getInstance()

        // 如果禁用了关闭提醒，允许关闭
        if (!settings.closeReminderEnabled) {
            CloseReminderState.releaseDialogLock()
            return true
        }

        val workLogService = project.getService(WorkLogService::class.java)
        if (workLogService == null) {
            CloseReminderState.releaseDialogLock()
            return true
        }

        // 检查是否已有今日日志
        val hasTodayLog = workLogService.hasTodayWorkLog()

        try {
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
                    Messages.getQuestionIcon()  // 使用问号图标
                )

                return when (result) {
                    0 -> {
                        // 复制到剪贴板
                        CopyPasteManager.getInstance().setContents(StringSelection(content))
                        Messages.showInfoMessage(project, "工作日志已复制到剪贴板", "复制成功")
                        true  // 允许关闭
                    }
                    1 -> {
                        // 打开工作日志窗口
                        val toolWindowManager = ToolWindowManager.getInstance(project)
                        val toolWindow = toolWindowManager.getToolWindow("WorkLog")
                        toolWindow?.show()
                        false  // 阻止关闭
                    }
                    2 -> {
                        // 直接关闭
                        true  // 允许关闭
                    }
                    else -> true  // 默认允许关闭
                }
            } else {
                // 如果没有日志，询问是否填写
                val options = arrayOf("立即填写", "稍后再说", "不再提醒")
                val result = Messages.showDialog(
                    project,
                    "今天还没有填写工作日志，是否现在填写？\n\n" +
                    "选择\"立即填写\"将打开工作日志窗口并保持 IDE 运行。",
                    "工作日志提醒",
                    options,
                    0,
                    Messages.getWarningIcon()  // 使用警告图标
                )

                return when (result) {
                    0 -> {
                        // 立即填写 - 打开工作日志窗口
                        val toolWindowManager = ToolWindowManager.getInstance(project)
                        val toolWindow = toolWindowManager.getToolWindow("WorkLog")
                        toolWindow?.show()
                        false  // 阻止关闭，保持 IDE 运行
                    }
                    1 -> {
                        // 稍后再说
                        true  // 允许关闭
                    }
                    2 -> {
                        // 不再提醒
                        settings.closeReminderEnabled = false
                        true  // 允许关闭
                    }
                    else -> true  // 默认允许关闭
                }
            }
        } finally {
            CloseReminderState.releaseDialogLock()
        }
    }
}
