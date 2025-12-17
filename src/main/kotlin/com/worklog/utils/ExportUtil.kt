package com.worklog.utils

import com.worklog.models.ExportFormat
import com.worklog.models.WorkLog
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.xhtmlrenderer.pdf.ITextRenderer
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.time.format.DateTimeFormatter

/**
 * 导出工具类
 * 负责将工作日志导出为不同格式
 */
object ExportUtil {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 导出工作日志
     */
    fun export(workLog: WorkLog, format: ExportFormat, outputDir: Path): File {
        return when (format) {
            ExportFormat.MARKDOWN -> exportToMarkdown(workLog, outputDir)
            ExportFormat.HTML -> exportToHtml(workLog, outputDir)
            ExportFormat.PDF -> exportToPdf(workLog, outputDir)
        }
    }

    /**
     * 导出为 Markdown（原生格式）
     */
    private fun exportToMarkdown(workLog: WorkLog, outputDir: Path): File {
        val fileName = "worklog-${workLog.date.format(DATE_FORMATTER)}.md"
        val file = outputDir.resolve(fileName).toFile()

        val content = MarkdownUtil.generateFullWorkLog(
            workLog = workLog,
            includeCodeDiff = workLog.hasCodeAccess
        )

        file.writeText(content)
        return file
    }

    /**
     * 导出为 HTML
     */
    fun exportToHtml(workLog: WorkLog, outputDir: Path): File {
        val fileName = "worklog-${workLog.date.format(DATE_FORMATTER)}.html"
        val file = outputDir.resolve(fileName).toFile()

        // 将 Markdown 转换为 HTML
        val markdownContent = MarkdownUtil.generateFullWorkLog(
            workLog = workLog,
            includeCodeDiff = workLog.hasCodeAccess
        )

        val parser = Parser.builder().build()
        val document = parser.parse(markdownContent)
        val renderer = HtmlRenderer.builder().build()
        val htmlBody = renderer.render(document)

        // 添加 CSS 样式
        val fullHtml = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>工作日志 - ${workLog.date}</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 900px;
                        margin: 0 auto;
                        padding: 20px;
                        background-color: #f5f5f5;
                    }
                    .container {
                        background-color: white;
                        padding: 40px;
                        border-radius: 8px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
                    h1 {
                        color: #2c3e50;
                        border-bottom: 3px solid #3498db;
                        padding-bottom: 10px;
                    }
                    h2 {
                        color: #34495e;
                        margin-top: 30px;
                        border-left: 4px solid #3498db;
                        padding-left: 12px;
                    }
                    h3 {
                        color: #555;
                    }
                    code {
                        background-color: #f4f4f4;
                        padding: 2px 6px;
                        border-radius: 3px;
                        font-family: "Courier New", monospace;
                    }
                    pre {
                        background-color: #f4f4f4;
                        padding: 15px;
                        border-radius: 5px;
                        overflow-x: auto;
                    }
                    ul, ol {
                        padding-left: 25px;
                    }
                    li {
                        margin: 8px 0;
                    }
                    .footer {
                        margin-top: 40px;
                        padding-top: 20px;
                        border-top: 1px solid #ddd;
                        text-align: center;
                        color: #999;
                        font-size: 14px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    $htmlBody
                    <div class="footer">
                        <p>生成时间: ${java.time.LocalDateTime.now()}</p>
                        <p>由 WorkLog 插件生成</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        file.writeText(fullHtml)
        return file
    }

    /**
     * 导出为 PDF
     */
    fun exportToPdf(workLog: WorkLog, outputDir: Path): File {
        // 先生成 HTML
        val htmlFile = exportToHtml(workLog, outputDir)

        val pdfFileName = "worklog-${workLog.date.format(DATE_FORMATTER)}.pdf"
        val pdfFile = outputDir.resolve(pdfFileName).toFile()

        // 将 HTML 转换为 PDF
        try {
            val outputStream = FileOutputStream(pdfFile)
            val renderer = ITextRenderer()
            renderer.setDocumentFromString(htmlFile.readText())
            renderer.layout()
            renderer.createPDF(outputStream)
            outputStream.close()

            // 删除临时 HTML 文件
            htmlFile.delete()

            return pdfFile
        } catch (e: Exception) {
            throw RuntimeException("PDF 导出失败: ${e.message}", e)
        }
    }

    /**
     * 批量导出多个工作日志
     */
    fun exportBatch(workLogs: List<WorkLog>, format: ExportFormat, outputDir: Path): List<File> {
        return workLogs.map { export(it, format, outputDir) }
    }
}
