package com.worklog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridLayout
import java.time.LocalDate
import java.time.YearMonth
import javax.swing.*

/**
 * 日期选择对话框
 */
class DatePickerDialog(project: Project) : DialogWrapper(project) {

    private var selectedDate: LocalDate = LocalDate.now()

    private val yearSpinner = JSpinner(SpinnerNumberModel(
        LocalDate.now().year,
        2000,
        2100,
        1
    ))

    private val monthSpinner = JSpinner(SpinnerNumberModel(
        LocalDate.now().monthValue,
        1,
        12,
        1
    ))

    private val daySpinner = JSpinner(SpinnerNumberModel(
        LocalDate.now().dayOfMonth,
        1,
        31,
        1
    ))

    init {
        title = "选择日期"
        init()

        // 添加监听器更新日期范围
        yearSpinner.addChangeListener { updateDayRange() }
        monthSpinner.addChangeListener { updateDayRange() }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 12))
        panel.border = JBUI.Borders.empty(16)
        panel.preferredSize = java.awt.Dimension(340, 170)

        val headerPanel = JPanel(BorderLayout(0, 4))
        headerPanel.add(JBLabel("选择日志日期").apply {
            font = font.deriveFont(Font.BOLD, 17f)
        }, BorderLayout.NORTH)
        headerPanel.add(JBLabel("选择要打开或生成工作日志的日期。").apply {
            foreground = JBColor.GRAY
        }, BorderLayout.CENTER)

        val inputPanel = JPanel(GridLayout(3, 2, 10, 8))
        inputPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            JBUI.Borders.empty(14)
        )

        inputPanel.add(JBLabel("年份"))
        inputPanel.add(yearSpinner)

        inputPanel.add(JBLabel("月份"))
        inputPanel.add(monthSpinner)

        inputPanel.add(JBLabel("日期"))
        inputPanel.add(daySpinner)

        panel.add(headerPanel, BorderLayout.NORTH)
        panel.add(inputPanel, BorderLayout.CENTER)

        return panel
    }

    private fun updateDayRange() {
        val year = yearSpinner.value as Int
        val month = monthSpinner.value as Int
        val yearMonth = YearMonth.of(year, month)
        val maxDay = yearMonth.lengthOfMonth()

        val currentDay = daySpinner.value as Int
        val model = daySpinner.model as SpinnerNumberModel
        model.setMaximum(maxDay)

        // 如果当前日期超出最大值，调整为最大值
        if (currentDay > maxDay) {
            daySpinner.value = maxDay
        }
    }

    override fun doOKAction() {
        val year = yearSpinner.value as Int
        val month = monthSpinner.value as Int
        val day = daySpinner.value as Int

        try {
            selectedDate = LocalDate.of(year, month, day)
            super.doOKAction()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                contentPane,
                "无效的日期: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    fun getSelectedDate(): LocalDate {
        return selectedDate
    }
}
