package com.worklog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Tool Window 工厂类
 */
class WorkLogToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val workLogToolWindow = WorkLogToolWindow(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(workLogToolWindow.getContent(), "", false)

        // 注册 Disposable，在窗口关闭时自动清理资源
        Disposer.register(toolWindow.disposable, workLogToolWindow)

        toolWindow.contentManager.addContent(content)
    }
}
