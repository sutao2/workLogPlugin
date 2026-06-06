package com.worklog.utils

import com.intellij.openapi.project.Project
import com.worklog.settings.AppSettingsState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

/**
 * 文件存储工具类
 * 负责工作日志的文件读写操作
 */
object StorageUtil {
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * 获取工作日志存储目录
     */
    fun getWorkLogDir(project: Project): Path {
        val settings = AppSettingsState.getInstance()
        val baseDir = project.basePath ?: throw IllegalStateException("项目路径不存在")
        val basePath = Paths.get(baseDir)
        val resolved = basePath.resolve(settings.storageLocation).normalize()
        // 防止路径遍历：确保解析后的路径仍在项目目录下
        if (!resolved.startsWith(basePath)) {
            throw IllegalStateException("存储路径不能指向项目目录之外: ${settings.storageLocation}")
        }
        return resolved
    }

    /**
     * 获取指定日期的工作日志文件路径
     */
    fun getWorkLogFile(project: Project, date: LocalDate): Path {
        val dir = getWorkLogDir(project)
        val fileName = "${date.format(DATE_FORMATTER)}.md"
        return dir.resolve(fileName)
    }

    /**
     * 获取指定日期的元数据文件路径
     */
    fun getMetadataFile(project: Project, date: LocalDate): Path {
        val dir = getWorkLogDir(project)
        val fileName = "${date.format(DATE_FORMATTER)}.json"
        return dir.resolve(fileName)
    }

    /**
     * 确保目录存在
     */
    fun ensureDirectoryExists(path: Path) {
        if (!path.exists()) {
            Files.createDirectories(path)
        }
    }

    /**
     * 读取工作日志内容
     */
    fun readWorkLog(project: Project, date: LocalDate): String? {
        val file = getWorkLogFile(project, date)
        return if (file.exists()) {
            file.readText()
        } else {
            null
        }
    }

    /**
     * 写入工作日志内容
     */
    fun writeWorkLog(project: Project, date: LocalDate, content: String) {
        val dir = getWorkLogDir(project)
        ensureDirectoryExists(dir)

        val file = getWorkLogFile(project, date)
        file.writeText(content)
    }

    /**
     * 读取日志元数据
     */
    fun readMetadata(project: Project, date: LocalDate): WorkLogMetadata? {
        val file = getMetadataFile(project, date)
        return if (file.exists()) {
            try {
                json.decodeFromString<WorkLogMetadata>(file.readText())
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * 写入日志元数据
     */
    fun writeMetadata(project: Project, date: LocalDate, metadata: WorkLogMetadata) {
        val dir = getWorkLogDir(project)
        ensureDirectoryExists(dir)

        val file = getMetadataFile(project, date)
        file.writeText(json.encodeToString(metadata))
    }

    /**
     * 获取所有工作日志日期列表
     */
    fun getAllWorkLogDates(project: Project): List<LocalDate> {
        val dir = getWorkLogDir(project)
        if (!dir.exists()) {
            return emptyList()
        }

        return dir.listDirectoryEntries("*.md")
            .mapNotNull { file ->
                try {
                    val fileName = file.nameWithoutExtension
                    LocalDate.parse(fileName, DATE_FORMATTER)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedDescending()
    }

    /**
     * 删除指定日期的工作日志
     */
    fun deleteWorkLog(project: Project, date: LocalDate) {
        val mdFile = getWorkLogFile(project, date)
        val jsonFile = getMetadataFile(project, date)

        if (mdFile.exists()) {
            mdFile.deleteExisting()
        }
        if (jsonFile.exists()) {
            jsonFile.deleteExisting()
        }
    }

    /**
     * 检查指定日期是否存在工作日志
     */
    fun hasWorkLog(project: Project, date: LocalDate): Boolean {
        return getWorkLogFile(project, date).exists()
    }

    /**
     * 获取报告导出目录
     */
    fun getReportsDir(project: Project): Path {
        return getWorkLogDir(project).resolve("reports")
    }

    /**
     * 获取相对于项目根目录的路径
     */
    fun getProjectRelativePath(project: Project, path: Path): String {
        val baseDir = project.basePath ?: return path.toString()
        val basePath = Paths.get(baseDir)
        return try {
            basePath.relativize(path).toString()
        } catch (e: Exception) {
            path.toString()
        }
    }
}

/**
 * 工作日志元数据
 */
@Serializable
data class WorkLogMetadata(
    val date: String,
    val createdAt: String,
    val updatedAt: String,
    val hasCodeAccess: Boolean,
    val commitCount: Int,
    val gitCommits: List<GitCommitMetadata> = emptyList()
)

/**
 * Git 提交元数据
 */
@Serializable
data class GitCommitMetadata(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val authorEmail: String,
    val timestamp: String,
    val files: List<String> = emptyList(),
    val filesCount: Int
)
