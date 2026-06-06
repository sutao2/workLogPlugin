package com.worklog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.time.LocalDate
import javax.swing.*

/**
 * IDE 关闭时的提醒对话框
 */
class ShutdownReminderDialog(private val project: Project) : DialogWrapper(project) {

    private val dontRemindCheckBox = JBCheckBox("今天不再提醒")
    private var userChoice: UserChoice = UserChoice.LATER

    enum class UserChoice {
        GENERATE,    // 立即生成日志
        OPEN,        // 打开日志编辑
        LATER,       // 稍后填写
        NEVER        // 不再提醒
    }

    init {
        title = "工作日志提醒"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 12))
        panel.border = JBUI.Borders.empty(16)
        panel.preferredSize = Dimension(480, 160)

        val contentPanel = JPanel(BorderLayout(0, 10))
        contentPanel.add(
            WorkLogUi.header(
                "今日工作日志尚未填写",
                "可以现在生成日志，也可以打开编辑器手动填写。日期: ${LocalDate.now()}"
            ),
            BorderLayout.NORTH
        )
        contentPanel.add(
            WorkLogUi.helpLabel("关闭 IDE 前保留一个轻量提醒，避免当天日志遗漏。", 420),
            BorderLayout.CENTER
        )

        panel.add(WorkLogUi.section(contentPanel, 14), BorderLayout.CENTER)
        dontRemindCheckBox.border = JBUI.Borders.empty(0, 2, 0, 0)
        panel.add(dontRemindCheckBox, BorderLayout.SOUTH)

        return panel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : DialogWrapperAction("立即生成") {
                init {
                    putValue(DEFAULT_ACTION, true)
                }

                override fun doAction(e: java.awt.event.ActionEvent) {
                    userChoice = if (dontRemindCheckBox.isSelected) {
                        UserChoice.NEVER
                    } else {
                        UserChoice.GENERATE
                    }
                    close(OK_EXIT_CODE)
                }
            },
            object : DialogWrapperAction("打开编辑") {
                override fun doAction(e: java.awt.event.ActionEvent) {
                    userChoice = UserChoice.OPEN
                    close(OK_EXIT_CODE)
                }
            },
            object : DialogWrapperAction("稍后填写") {
                override fun doAction(e: java.awt.event.ActionEvent) {
                    userChoice = UserChoice.LATER
                    close(CANCEL_EXIT_CODE)
                }
            }
        )
    }

    /**
     * 获取用户选择
     */
    fun getUserChoice(): UserChoice {
        return userChoice
    }

    /**
     * 是否选择了不再提醒
     */
    fun isDontRemindSelected(): Boolean {
        return dontRemindCheckBox.isSelected
    }
}
