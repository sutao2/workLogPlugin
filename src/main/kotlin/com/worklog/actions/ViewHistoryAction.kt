package com.worklog.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.worklog.ui.HistoryViewDialog
import com.worklog.ui.WorkLogToolWindow

/**
 * 查看历史日志的动作
 */
class ViewHistoryAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 显示历史查看对话框
        val dialog = HistoryViewDialog(project)
        dialog.show()

        // 如果用户选择了日期，打开工作日志窗口并加载该日志
        val selectedDate = dialog.getSelectedDate()
        if (selectedDate != null) {
            val toolWindowManager = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            val toolWindow = toolWindowManager.getToolWindow(WorkLogToolWindow.ID)
            toolWindow?.show()
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
}
