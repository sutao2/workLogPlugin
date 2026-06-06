package com.worklog.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.worklog.services.WorkLogService
import com.worklog.utils.StorageUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.*

/**
 * 历史日志查看对话框
 */
class HistoryViewDialog(private val project: Project) : DialogWrapper(project) {

    private val workLogService = project.getService(WorkLogService::class.java)
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd (EEEE)")

    private val searchField = JBTextField()
    private val dateListModel = DefaultListModel<LocalDate>()
    private val dateList = JBList(dateListModel)
    private val previewArea = JTextArea()

    private var selectedDate: LocalDate? = null

    init {
        title = "历史工作日志"
        init()
        loadDates()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 12))
        panel.preferredSize = Dimension(860, 620)
        panel.border = JBUI.Borders.empty(12)

        val headerPanel = JPanel(BorderLayout(0, 4))
        headerPanel.add(JBLabel("历史工作日志").apply {
            font = font.deriveFont(Font.BOLD, 17f)
        }, BorderLayout.NORTH)
        headerPanel.add(JBLabel("搜索、预览并打开已保存的 Markdown 工作日志。").apply {
            foreground = JBColor.GRAY
        }, BorderLayout.CENTER)

        // 顶部搜索栏
        val searchPanel = JPanel(BorderLayout(8, 0))
        searchPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            JBUI.Borders.empty(10)
        )
        searchPanel.add(JBLabel("搜索").apply {
            font = font.deriveFont(Font.BOLD)
        }, BorderLayout.WEST)
        searchField.addActionListener {
            filterDates()
        }
        searchPanel.add(searchField, BorderLayout.CENTER)
        val searchButton = JButton("搜索")
        searchButton.margin = JBUI.insets(3, 12)
        searchButton.isFocusPainted = false
        searchButton.addActionListener {
            filterDates()
        }
        searchPanel.add(searchButton, BorderLayout.EAST)

        val topPanel = JPanel(BorderLayout(0, 10))
        topPanel.add(headerPanel, BorderLayout.NORTH)
        topPanel.add(searchPanel, BorderLayout.CENTER)
        panel.add(topPanel, BorderLayout.NORTH)

        // 中间分割面板
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT)
        splitPane.border = BorderFactory.createEmptyBorder()
        splitPane.dividerSize = JBUI.scale(7)

        // 左侧日期列表
        dateList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        dateList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): JComponent {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JComponent
                if (value is LocalDate) {
                    text = value.format(dateFormatter)
                }
                return component
            }
        }
        dateList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                previewSelectedDate()
            }
        }

        val listPanel = JPanel(BorderLayout(0, 8))
        listPanel.border = JBUI.Borders.empty(0, 0, 0, 10)
        listPanel.add(JBLabel("日志日期").apply {
            font = font.deriveFont(Font.BOLD, 15f)
        }, BorderLayout.NORTH)
        val listScrollPane = JBScrollPane(dateList)
        listScrollPane.border = BorderFactory.createLineBorder(JBColor.border())
        listScrollPane.preferredSize = Dimension(260, 0)
        listPanel.add(listScrollPane, BorderLayout.CENTER)
        splitPane.leftComponent = listPanel

        // 右侧预览区域
        previewArea.isEditable = false
        previewArea.lineWrap = true
        previewArea.wrapStyleWord = true
        previewArea.margin = JBUI.insets(14)
        previewArea.font = Font("Monospaced", Font.PLAIN, 13)
        previewArea.background = JBColor.namedColor("EditorPane.background", JBColor.PanelBackground)
        val previewPanel = JPanel(BorderLayout(0, 8))
        previewPanel.add(JBLabel("日志预览").apply {
            font = font.deriveFont(Font.BOLD, 15f)
        }, BorderLayout.NORTH)
        val previewScrollPane = JBScrollPane(previewArea)
        previewScrollPane.border = BorderFactory.createLineBorder(JBColor.border())
        previewPanel.add(previewScrollPane, BorderLayout.CENTER)
        splitPane.rightComponent = previewPanel

        splitPane.dividerLocation = JBUI.scale(280)
        panel.add(splitPane, BorderLayout.CENTER)

        return panel
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : DialogWrapperAction("打开") {
                override fun doAction(e: java.awt.event.ActionEvent) {
                    selectedDate = dateList.selectedValue
                    close(OK_EXIT_CODE)
                }
            },
            object : DialogWrapperAction("删除") {
                override fun doAction(e: java.awt.event.ActionEvent) {
                    deleteSelectedDate()
                }
            },
            cancelAction
        )
    }

    private fun loadDates() {
        dateListModel.clear()
        val dates = workLogService.getAllWorkLogDates()
        dates.forEach { date ->
            dateListModel.addElement(date)
        }

        if (!dateListModel.isEmpty) {
            dateList.selectedIndex = 0
        }
    }

    private fun filterDates() {
        val keyword = searchField.text.trim().lowercase()
        if (keyword.isEmpty()) {
            loadDates()
            return
        }

        dateListModel.clear()
        val allDates = workLogService.getAllWorkLogDates()

        allDates.forEach { date ->
            val content = StorageUtil.readWorkLog(project, date) ?: ""
            if (date.toString().contains(keyword) || content.lowercase().contains(keyword)) {
                dateListModel.addElement(date)
            }
        }
    }

    private fun previewSelectedDate() {
        val date = dateList.selectedValue ?: return
        val content = StorageUtil.readWorkLog(project, date) ?: "无内容"

        // 限制预览长度
        val preview = if (content.length > 5000) {
            content.substring(0, 5000) + "\n\n... (内容过长，已截断)"
        } else {
            content
        }

        previewArea.text = preview
        previewArea.caretPosition = 0
    }

    private fun deleteSelectedDate() {
        val date = dateList.selectedValue ?: return

        val result = JOptionPane.showConfirmDialog(
            contentPane,
            "确定要删除 ${date.format(dateFormatter)} 的工作日志吗？\n此操作不可恢复！",
            "确认删除",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (result == JOptionPane.YES_OPTION) {
            workLogService.deleteWorkLog(date)
            loadDates()
            previewArea.text = ""
        }
    }

    /**
     * 获取用户选择的日期
     */
    fun getSelectedDate(): LocalDate? {
        return selectedDate
    }
}
