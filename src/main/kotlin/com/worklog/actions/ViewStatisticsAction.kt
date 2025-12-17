package com.worklog.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.worklog.ui.StatisticsDialog

/**
 * 查看统计信息的 Action
 */
class ViewStatisticsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = StatisticsDialog(project)
        dialog.show()
    }
}
