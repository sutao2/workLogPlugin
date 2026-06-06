package com.worklog.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.worklog.services.WorkLogService
import com.worklog.ui.GenerateWorkLogDialog
import com.worklog.utils.MarkdownUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.JOptionPane

/**
 * 生成工作日志的动作
 */
class GenerateWorkLogAction : AnAction() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 显示生成对话框
        val dialog = GenerateWorkLogDialog(project)
        if (dialog.showAndGet()) {
            val date = dialog.getSelectedDate()
            val includeCode = dialog.isIncludeCode()
            val includeUncommitted = dialog.isIncludeUncommitted()

            // 异步生成日志
            scope.launch {
                try {
                    val workLogService = project.getService(WorkLogService::class.java)
                    val workLog = workLogService.createWorkLog(date, includeCode, includeUncommitted)

                    // 如果配置了 AI，调用 AI 生成总结
                    val settings = com.worklog.settings.AppSettingsState.getInstance()
                    val aiSummary = if (settings.apiKeyCompat.isNotBlank() && workLog.gitCommits.isNotEmpty()) {
                        try {
                            val aiService = project.getService(com.worklog.services.AIService::class.java)
                            aiService.summarizeWork(workLog.gitCommits, includeCode)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }

                    // 生成完整内容
                    val fullContent = MarkdownUtil.generateFullWorkLog(
                        workLog = workLog,
                        aiSummary = aiSummary,
                        includeCodeDiff = includeCode
                    )

                    workLog.content = fullContent
                    workLogService.saveWorkLog(workLog)

                    // 打开工作日志窗口
                    val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                    val toolWindow = toolWindowManager.getToolWindow("WorkLog")
                    toolWindow?.show()

                    JOptionPane.showMessageDialog(
                        null,
                        "工作日志生成成功！",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } catch (ex: Exception) {
                    JOptionPane.showMessageDialog(
                        null,
                        "生成失败: ${ex.message}",
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
}
