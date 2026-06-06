package com.worklog.services

import java.time.LocalDate

/**
 * GitService 测试类
 * 用于验证 Git 提交记录获取功能
 *
 * 运行方式：
 * 1. 在 IDE 中右键点击这个文件，选择 "Run GitServiceTest"
 * 2. 或者在终端运行: ./gradlew test --tests GitServiceTest
 */
class GitServiceTest {

    companion object {
        /**
         * 主测试方法 - 可以直接运行
         */
        @JvmStatic
        fun main(args: Array<String>) {
            println("=".repeat(80))
            println("Git Service 功能测试")
            println("=".repeat(80))
            println()

            // 测试日期，可以修改为你想测试的日期
            val testDate = if (args.isNotEmpty()) {
                LocalDate.parse(args[0])
            } else {
                LocalDate.of(2025, 12, 16)  // 默认测试 12月16日
            }

            println("测试日期: $testDate")
            println()

            testGitRepository()
            println()

            testGitUserConfig()
            println()

            testGitLogCommand(testDate)
            println()

            println("=".repeat(80))
            println("测试完成")
            println("=".repeat(80))
        }

        /**
         * 测试 1: Git 仓库检查
         */
        private fun testGitRepository() {
            println("【测试 1: Git 仓库检查】")

            val currentDir = System.getProperty("user.dir")
            println("当前工作目录: $currentDir")

            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("git", "rev-parse", "--git-dir"),
                    null,
                    java.io.File(currentDir)
                )

                val gitDir = process.inputStream.bufferedReader().readText().trim()
                val error = process.errorStream.bufferedReader().readText().trim()
                process.waitFor()

                if (process.exitValue() == 0) {
                    println("✓ Git 仓库路径: $gitDir")
                } else {
                    println("✗ 不是 Git 仓库")
                    println("  错误: $error")
                }
            } catch (e: Exception) {
                println("✗ 检查失败: ${e.message}")
            }
        }

        /**
         * 测试 2: Git 用户配置
         */
        private fun testGitUserConfig() {
            println("【测试 2: Git 用户配置】")

            val currentDir = System.getProperty("user.dir")

            // 测试 user.email
            try {
                val emailProcess = Runtime.getRuntime().exec(
                    arrayOf("git", "config", "user.email"),
                    null,
                    java.io.File(currentDir)
                )

                val email = emailProcess.inputStream.bufferedReader().readText().trim()
                emailProcess.waitFor()

                if (emailProcess.exitValue() == 0 && email.isNotEmpty()) {
                    println("✓ 用户 email: $email")
                } else {
                    println("✗ 未配置 user.email")
                    println("  提示: 运行 git config user.email \"your@email.com\"")
                }

                // 测试 user.name
                val nameProcess = Runtime.getRuntime().exec(
                    arrayOf("git", "config", "user.name"),
                    null,
                    java.io.File(currentDir)
                )

                val name = nameProcess.inputStream.bufferedReader().readText().trim()
                nameProcess.waitFor()

                if (nameProcess.exitValue() == 0 && name.isNotEmpty()) {
                    println("✓ 用户名称: $name")
                } else {
                    println("⚠ 未配置 user.name")
                }
            } catch (e: Exception) {
                println("✗ 配置检查失败: ${e.message}")
            }
        }

        /**
         * 测试 3: Git log 命令
         */
        private fun testGitLogCommand(testDate: LocalDate) {
            println("【测试 3: Git log 命令】")

            val currentDir = System.getProperty("user.dir")

            // 先获取用户 email
            val emailProcess = Runtime.getRuntime().exec(
                arrayOf("git", "config", "user.email"),
                null,
                java.io.File(currentDir)
            )
            val userEmail = emailProcess.inputStream.bufferedReader().readText().trim()
            emailProcess.waitFor()

            if (userEmail.isEmpty()) {
                println("✗ 无法获取用户 email，跳过此测试")
                return
            }

            val dateStr = testDate.toString()
            val nextDateStr = testDate.plusDays(1).toString()

            // 构建 git log 命令
            val gitCommand = arrayOf(
                "git", "log",
                "--all",
                "--author=$userEmail",
                "--since=$dateStr 00:00:00",
                "--until=$nextDateStr 00:00:00",
                "--pretty=format:%H|%h|%s|%an|%ae|%ct",
                "--name-only"
            )

            println("执行命令:")
            println("  ${gitCommand.joinToString(" ")}")
            println()

            try {
                val process = Runtime.getRuntime().exec(
                    gitCommand,
                    null,
                    java.io.File(currentDir)
                )

                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                process.waitFor()

                if (process.exitValue() != 0) {
                    println("✗ Git 命令执行失败")
                    println("  错误: $error")
                    return
                }

                println("✓ Git 命令执行成功")
                println()

                if (output.isBlank()) {
                    println("⚠ 在 $testDate 没有找到当前用户的提交记录")
                    println()

                    // 额外测试：查询所有用户的提交
                    println("【额外测试：查询所有用户的提交】")
                    val allCommitsCommand = arrayOf(
                        "git", "log",
                        "--all",
                        "--since=$dateStr 00:00:00",
                        "--until=$nextDateStr 00:00:00",
                        "--pretty=format:%ae|%s",
                        "--no-merges"
                    )

                    val allProcess = Runtime.getRuntime().exec(
                        allCommitsCommand,
                        null,
                        java.io.File(currentDir)
                    )

                    val allOutput = allProcess.inputStream.bufferedReader().readText()
                    allProcess.waitFor()

                    if (allOutput.isNotBlank()) {
                        println("该日期存在以下用户的提交:")
                        val commits = allOutput.lines().filter { it.isNotBlank() }
                        val authors = commits.map { it.split("|")[0] }.distinct()

                        authors.forEach { author ->
                            println("  - $author")
                            val authorCommits = commits.filter { it.startsWith(author) }
                            authorCommits.take(3).forEach { commit ->
                                val message = commit.split("|").getOrNull(1) ?: ""
                                println("      $message")
                            }
                        }
                        println()
                        println("⚠ 您的 email ($userEmail) 与上述邮箱不匹配")
                        println("  可能原因:")
                        println("  1. Git 配置的 email 与提交时使用的 email 不一致")
                        println("  2. 提交是在不同的机器/环境下完成的")
                        println()
                        println("  解决方案:")
                        println("  - 如果 $userEmail 是正确的邮箱，请确保提交时使用了这个邮箱")
                        println("  - 如果上述某个邮箱是您的，请更新 git config:")
                        println("    git config user.email \"correct@email.com\"")
                    } else {
                        println("✓ 该日期所有用户都没有提交记录")
                    }
                } else {
                    println("Git 原始输出:")
                    println("-".repeat(80))
                    println(output)
                    println("-".repeat(80))
                    println()

                    // 解析输出
                    val lines = output.lines().filter { it.isNotBlank() }
                    var commitCount = 0

                    println("解析的提交记录:")
                    lines.forEach { line ->
                        if (line.contains("|")) {
                            commitCount++
                            val parts = line.split("|")
                            if (parts.size >= 6) {
                                println("提交 #$commitCount:")
                                println("  Hash: ${parts[1]}")
                                println("  消息: ${parts[2]}")
                                println("  作者: ${parts[3]} <${parts[4]}>")
                                println("  时间: ${java.time.Instant.ofEpochSecond(parts[5].toLong())}")
                                println()
                            }
                        }
                    }

                    println("✓ 成功找到 $commitCount 条提交记录")
                }

            } catch (e: Exception) {
                println("✗ 执行失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
