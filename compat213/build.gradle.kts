plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

group = "com.worklog"
version = "1.0.0-compat213"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("org.xhtmlrenderer:flying-saucer-pdf:9.4.1")
    implementation("org.commonmark:commonmark:0.21.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

intellij {
    version.set("2021.3.3")
    type.set("IC")
    plugins.set(listOf("Git4Idea"))
    updateSinceUntilBuild.set(false)
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("../src/main/kotlin"))
        resources.setSrcDirs(listOf("../src/main/resources", "../src/legacy/resources"))
        java.exclude(
            "com/worklog/listeners/ProjectStartupActivity.kt",
            "com/worklog/listeners/CodeReviewCheckinHandlerFactory.kt",
            "com/worklog/actions/RunCodeReviewAction.kt",
            "com/worklog/actions/RunCommitHistoryReviewAction.kt"
        )
    }
    named("test") {
        java.setSrcDirs(listOf("../src/test/kotlin"))
        resources.setSrcDirs(listOf("../src/test/resources"))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "11"
    targetCompatibility = "11"
    options.release.set(11)
    options.encoding = "UTF-8"
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/plugin.xml")
    from("../src/legacy/resources")
}

tasks.patchPluginXml {
    sinceBuild.set("213")
    untilBuild.set("213.*")
}

tasks.buildPlugin {
    archiveFileName.set("WorkLog-compat213-1.0.0-compat213.zip")
}

tasks.register("printCompat213Help") {
    group = "help"
    description = "Show how to build the dedicated compat213 package"
    doLast {
        println("Use: ./gradlew -p compat213 printCompat213Help")
        println("Build: ./gradlew -p compat213 buildPlugin")
        println("This is a legacy scaffold for IntelliJ IDEA 2021.3 support.")
    }
}
