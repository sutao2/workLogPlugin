package com.worklog.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * WorkLog 插件图标
 */
object WorkLogIcons {
    /**
     * 工具窗口图标 (13x13)
     */
    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/toolWindowIcon.svg", WorkLogIcons::class.java)
}
