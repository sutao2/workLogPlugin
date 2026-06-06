tasks.register<JavaExec>("testGit") {
    group = "verification"
    description = "Test Git functionality"

    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.worklog.services.GitServiceTest")

    // 从命令行参数获取测试日期
    args = if (project.hasProperty("testDate")) {
        listOf(project.property("testDate").toString())
    } else {
        listOf("2025-12-16")
    }
}
