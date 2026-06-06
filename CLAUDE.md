# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and run

- `./gradlew buildPlugin` — build the IntelliJ plugin ZIP.
- `./gradlew runIde` — launch a sandbox IntelliJ instance with the plugin installed.
- `./gradlew test` — run tests.
- `./gradlew test --tests "com.worklog.services.GitServiceTest"` — run the existing test entrypoint only.
- `./gradlew testGit` — run the custom Git verification task wired to `GitServiceTest.main()`.
- `./gradlew testGit -PtestDate=2025-12-16` — run the Git verification task for a specific date.
- `./gradlew publishPlugin` — publish the plugin.

## Repository shape

This is an IntelliJ Platform plugin named `WorkLog` (`plugin.xml`) built with Kotlin/JVM + the JetBrains Gradle plugin. The plugin depends on `Git4Idea` and uses IntelliJ application/project services rather than a Spring-style DI container.

Key runtime entrypoints:

- `src/main/resources/META-INF/plugin.xml` registers everything: tool window, settings page, project/application listeners, project services, and menu actions.
- `src/main/kotlin/com/worklog/ui/WorkLogToolWindowFactory.kt` creates the main tool window.
- `src/main/kotlin/com/worklog/actions/*.kt` contains menu-triggered actions for generating logs and reports.

## Core architecture

The codebase is organized around a small service layer with Swing-based UI on top:

- `services/GitService.kt` is the Git integration boundary. It shells out to `git log` / `git show`, filters files using settings, and can also synthesize a pseudo-commit for uncommitted changes via IntelliJ change lists.
- `services/AIService.kt` is the LLM boundary. It reads the active API config from `AppSettingsState`, builds prompts from commit data, supports both OpenAI-compatible and custom JSON APIs, and returns plain summary text.
- `services/WorkLogService.kt` orchestrates log generation and persistence: collect commits, optionally include uncommitted changes, create/load/save `WorkLog`, and write metadata.
- `services/ReminderService.kt` handles scheduled reminders inside the IDE process.
- `services/StatisticsService.kt` builds weekly/monthly/yearly aggregates from saved logs.

UI and actions are intentionally thin: they mostly collect user input, call services, and render/save results.

## Persistence model

There are two persistence layers:

1. **Plugin settings**: `settings/AppSettingsState.kt`
   - Stored by IntelliJ as `WorkLogPlugin.xml` via `PersistentStateComponent`.
   - Holds API configs, reminder settings, storage location, prompt templates, and file filtering rules.
   - The “active API config” abstraction is important: most service code reads `apiUrlCompat` / `apiKeyCompat` / `modelNameCompat`, which proxy to the currently enabled config.

2. **Generated work logs**: `utils/StorageUtil.kt`
   - Stored under the configured project-relative directory, default `.worklogs/`.
   - Each date produces `yyyy-MM-dd.md` plus `yyyy-MM-dd.json` metadata.
   - `WorkLogService` is the correct entrypoint; avoid bypassing it unless you are only changing low-level storage behavior.

## Log generation flow

The main feature flow is:

1. Action or tool window opens a generate dialog.
2. `WorkLogService.createWorkLog()` fetches commits for a date from `GitService`.
3. If generating for today with code access enabled, uncommitted changes are added as a synthetic commit.
4. `AIService.summarizeWork()` optionally generates a summary from commit metadata and truncated diffs.
5. `MarkdownUtil.generateFullWorkLog()` merges the date, AI summary, commit list, and optional code-change section into the user-configurable output template.
6. `WorkLogService.saveWorkLog()` writes markdown + JSON metadata.

If you change generation behavior, check both `MarkdownUtil.kt` and `AppSettingsState.kt` because templates and placeholders are user-configurable.

## UI and reminder behavior

- The primary UX is the right-side `WorkLog` tool window (`ui/WorkLogToolWindow.kt`), not a modal-only workflow.
- Settings live in `settings/SettingsConfigurable.kt` and are split into tabs for API config, permissions, reminders, storage/export, filters, prompts, and output templates.
- There are two shutdown hooks:
  - `listeners/ProjectCloseListener.kt` can block project close and is the real enforcement point.
  - `listeners/AppCloseListener.kt` only reminds during full IDE shutdown.
- `listeners/CloseReminderState.kt` exists to prevent duplicate close dialogs; preserve that coordination if you touch shutdown behavior.

## Reporting and exports

- Weekly/monthly/yearly report actions are separate action classes, but the report content is assembled in `StatisticsService.kt`.
- Export helpers live in `utils/ExportUtil.kt`; markdown formatting helpers live in `utils/MarkdownUtil.kt`.

## Practical notes for future edits

- This plugin mixes IntelliJ APIs with raw `Runtime.exec(...)` Git calls; changes around Git behavior usually need both repository-path handling and timeout/error handling reviewed together.
- The project currently has very light automated test coverage; `src/test/kotlin/com/worklog/services/GitServiceTest.kt` is more of an executable verification harness than a conventional unit test suite.
- A Gradle task listing attempt failed in this environment because of a TLS handshake while resolving Gradle/plugin artifacts, so if Gradle commands fail, verify network/TLS access before assuming the build script is wrong.

## 总结
本次提交主要是功能扩展（支持未提交代码、跨项目日志复制）以及对 Git 调用的实现方式进行重构。整体思路合理，未见明显的逻辑错误或安全隐患。但存在 **1 项潜在的编译/运行时风险** 需要确认。

## 问题

| 严重级别 | 文件 | 问题说明 | 修改建议 |
|----------|------|----------|----------|
| 高 | `src/main/kotlin/com/worklog/services/WorkLogService.kt`（未显示的改动） | `GenerateWorkLogAction` 现在调用 `WorkLogService.createWorkLog(date, includeCode, includeUncommitted)`，但代码库中未提供 `createWorkLog` 的新签名实现。如果对应方法仍保持旧的两个参数签名，将导致编译错误或运行时 `NoSuchMethodError`。 | 确认并更新 `WorkLogService` 中 `createWorkLog` 方法的声明与实现，使其接受 `includeUncommitted: Boolean` 参数；若已修改，请确保所有调用方均已同步更新。 |
| 中 | `src/main/kotlin/com/worklog/listeners/AppCloseListener.kt` | 当所有项目都有日志时，复制到剪贴板的提示使用 `Messages.showDialog(null, …)`，父组件为 `null`。在某些平台（尤其是 macOS）可能导致对话框位置异常或无法聚焦。 | 可以传入 `projects.firstOrNull { !it.isDisposed }` 作为父 `Component/Project`，保持与其它对话框一致的行为。 |
| 中 | `src/main/kotlin/com/worklog/listeners/CloseReminderState.kt` | `tryAcquireDialogLock()` 在获取锁后立即把 `currentDecision` 设为 `CANCELLED`，而 `recordCloseDecision(PROCEED)` 才会把时间戳更新。若在显示对话框前就因异常提前退出（例如 UI 初始化失败），`currentDecision` 会保持 `CANCELLED`，导致后续关闭流程不受冷却时间限制，可能出现频繁弹窗。 | 在异常路径里（如对话框未成功显示）调用 `recordCloseDecision(CloseDecision.PROCEED)` 或显式恢复 `currentDecision`，避免冷却失效。 |
| 低 | `src/main/kotlin/com/worklog/services/GitService.kt` | `executeGitCommand` 使用 `thread` 启动两个守护线程读取 stdout / stderr。若进程输出极大且在超时后被强制销毁，两个线程仍可能在阻塞的 `readLine()` 上卡住，导致线程泄漏。 | 改为使用 `process.inputStream.bufferedReader().useLines { … }` 或在 `finally` 中关闭流，确保线程能够及时结束。 |
| 低 | `src/main/kotlin/com/worklog/services/ReminderService.kt`（仅看到新增 `ScheduledFuture` 导入） | 若新增的 `ScheduledFuture` 成员未在类销毁时取消，可能导致后台线程在插件卸载后仍然运行。 | 在插件或服务的 `dispose`/`shutdown` 方法中调用 `future?.cancel(false)`。 |

> **未发现明显的功能缺陷或安全漏洞**，上述问题主要是潜在的编译/运行时风险或细节上的稳健性提升。请根据建议进行相应确认或修正。