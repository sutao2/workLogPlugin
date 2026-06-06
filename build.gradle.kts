import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Kotlin standard library
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Kotlin serialization for JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // HTTP client for AI API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // HTML to PDF (for export functionality)
    implementation("org.xhtmlrenderer:flying-saucer-pdf:9.4.1")
    implementation("org.commonmark:commonmark:0.21.0")

    testImplementation(kotlin("test"))

    // IntelliJ Platform dependencies
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin Dependencies - Git4Idea already includes VCS dependencies
        bundledPlugin("Git4Idea")

        // Required for plugin development
        instrumentationTools()
        pluginVerifier()
    }
}

// Configure Gradle IntelliJ Platform Plugin
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            providers.gradleProperty("pluginUntilBuild").orNull?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { untilBuild = it }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }
}


// Git 功能测试任务
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
