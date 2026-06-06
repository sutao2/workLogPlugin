# WorkLog - IDEA 工作日志插件

一款面向 IntelliJ 系 IDE 的工作日志插件：从 Git 记录出发，可选结合 AI 生成总结与代码评审，用 Markdown 落盘，并支持统计与周报/月报/年报。

## 主要功能

- **自动获取 Git 提交信息** — 按所选日期汇总仓库中的提交；生成**当日**日志且**允许读取代码**时，会把**本地未提交变更**合成额外条目一并参与展示与总结（仍受文件过滤规则约束）。
- **AI 智能总结** — 支持 OpenAI Chat Completions 兼容接口或完全自定义请求/响应解析；可多套 API 配置切换。
- **AI 代码评审** — 对**暂存区**或**历史提交**做变更评审；可选**提交前自动评审**（需在设置中开启，且需允许读取代码）。
- **代码访问权限** — 未授权时不读取 diff/工作区内容；与日志总结、代码评审联动。
- **Markdown 编辑与存储** — 默认写入项目下配置目录；支持可定制的**输出模板**（占位符拼装最终 Markdown）。
- **提醒** — **项目关闭**时可拦截并提醒补写今日日志（主要 enforcement）；**退出 IDE** 时也可提醒；支持每日定时提醒。
- **历史与导出** — 浏览、搜索历史日志；导出 Markdown / HTML / PDF。
- **工作统计与报告** — 「工作统计」看板；一键生成**周报 / 月报 / 年报**（基于已保存的日志与元数据）。

## 系统要求

### 最终用户

- **IntelliJ IDEA / IntelliJ 系 IDE**：与本插件构建配置一致，当前兼容约 **2024.2（build 242）— 2026.1.x**（以 `gradle.properties` 中 `pluginSinceBuild` / `pluginUntilBuild` 为准）。
- **Git**：已安装并在 PATH 中可用；仓库需能被 IDE 识别为 Git 项目。
- **Git4Idea**：插件依赖 JetBrains 自带的 Git 集成（通常已随 IDE 启用）。

### 开发者构建

- JDK **17+**（用于编译；运行沙箱仍由 Gradle IntelliJ 插件拉取平台）。
- Git。
- 推荐与本仓库相同的 **Gradle** 版本（见 `gradle/wrapper`）。

## 依赖说明

- 平台模块：`com.intellij.modules.platform`、`com.intellij.modules.vcs`。
- **Git4Idea**：提交历史、变更列表等与 VCS 的集成均依赖该插件。

## 安装

### 从源码构建

1. 克隆仓库（请将下方地址换成你的 fork 或上游仓库）。

```bash
git clone https://github.com/sutao1/workLogPlugin.git
cd workLogPlugin
```

2. 构建插件 ZIP：

```bash
./gradlew buildPlugin
```

3. 在 IDE 中安装：`Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`，选择
   `build/distributions/workLog-<版本号>.zip`（版本号与 `gradle.properties` 中 `pluginVersion` 一致，例如 `1.0.0`）。

### 从 JetBrains Marketplace 安装

（上架后可在此补充插件页链接与版本说明。）

## 使用指南

### 入口一览

| 入口 | 说明 |
|------|------|
| 右侧 **WorkLog** 工具窗口 | 主界面：生成、编辑、历史、导出等 |
| `Tools` 菜单 | **生成工作日志**、**查看历史日志**、**工作统计**、**生成周报/月报/年报**、**代码评审** |
| **提交（Commit）** 工具窗口 | 提交消息区域可触发 **代码评审**（评审暂存区） |
| **Git Log** 右键菜单 | **评审提交代码**（针对所选历史提交） |
| `Settings` → `Tools` → **WorkLog** | 全部选项与提示词模板 |

### 快速开始

1. 打开右侧 **WorkLog** 工具窗口，或使用 `Tools` → `生成工作日志`。
2. 选择日期，按需勾选「允许读取代码」等选项后生成。
3. 在编辑器中修改 Markdown，保存后内容写入配置的存储目录。

### 配置 AI API

1. `Settings` → `Tools` → `WorkLog` → **AI API 配置**。
2. 可新增多套配置：**OpenAI 格式**（填写 URL / Key / 模型）或**自定义格式**（请求 JSON 模板 + 响应字段路径）。
3. 使用 **测试 AI 连接** 校验；用 **设为当前** 切换正在使用的配置。
4. 在 **代码访问权限** 中控制是否允许读取 diff；代码评审的自动提交前流程也依赖「允许读取代码」。

### 工作统计与周报 / 月报 / 年报

- **工作统计**：`Tools` → `工作统计`，按周/月等范围查看提交数、活跃日、按星期分布等（数据来自已保存的工作日志）。
- **周报 / 月报 / 年报**：对应菜单项会汇总周期内的日志内容，便于汇报与归档。

### AI 代码评审

**手动评审**

- **暂存区**：`Tools` → `代码评审`，或在提交窗口使用同一动作，对当前 **staged** 变更调用 AI。
- **历史提交**：在 **Git Log** 中选提交 → 右键 **评审提交代码**。

**提交前自动评审**

1. `Settings` → `Tools` → `WorkLog` → **代码评审**。
2. 勾选 **启用代码评审功能**、**提交时自动触发代码评审**。
3. 同时需在 **代码访问权限** 中允许读取代码，否则自动评审不会执行。
4. 可配置 **最大 diff 字符数**（超出部分截断）、系统/用户提示词；用户模板支持 `{{files}}`、`{{diff}}`、`{{commit_message}}`。

评审结束可在结果对话框中查看 Markdown 报告，并选择是否继续提交。

### 提醒行为说明

- **项目关闭**：若开启「IDE 关闭时提醒」类选项，在**关闭项目**时若当日尚无日志，可能弹出提醒并**可阻止关闭**（具体以当前版本实现为准）。
- **应用退出**：退出整个 IDE 时也可提示补写日志，避免仅依赖关项目习惯的用户漏填。
- **定时提醒**：在 **提醒设置** 中配置时间，到点气球通知。

### 存储位置

- 默认目录：项目根下 **`.worklogs/`**（可在 **存储和导出** 中改为其他相对路径）。
- 每个日期通常包含：`yyyy-MM-dd.md`（正文）与 `yyyy-MM-dd.json`（元数据，如提交列表摘要）。

## 配置选项（设置页签摘要）

| 页签 | 内容 |
|------|------|
| AI API 配置 | 多配置、格式、测试连接、当前配置 |
| 代码访问权限 | 是否允许读代码、记住选择 |
| 代码评审 | 总开关、提交前自动评审、diff 长度、评审提示词 |
| 提醒设置 | 定时提醒、关闭/退出相关提醒 |
| 存储和导出 | 默认导出格式、日志存储相对路径 |
| 文件过滤 | 排除扩展名、排除目录、单文件 diff 大小上限（KB） |
| 提示词模板 | 工作总结用的系统/用户模板（含 `{{commits}}`、`{{code_diff}}`、`{{#if hasCodeAccess}}` 等） |
| 输出模板 | 最终 Markdown 骨架：`{{date}}`、`{{ai_summary}}`、`{{git_commits}}`、`{{code_changes}}` 等 |

## 隐私和安全

- **代码与 diff**：仅在用户允许读取代码时采集；文件过滤可减少敏感或大文件进入提示词。
- **本地存储**：日志与元数据默认只在本地项目目录；除非你自己调用 AI API，否则第三方不会收到仓库内容。
- **API Key**：由 IDE 设置持久化组件保存（具体加密策略以平台为准）；请勿将含 Key 的配置文件提交到公开仓库。

## 日志格式示例

```markdown
# 工作日志 - 2026年4月4日 星期六

## 🤖 AI 工作总结

今日主要完成了以下工作：
1. …

## 💾 Git 提交记录

共 N 次提交：

### 1. 示例提交说明
- **提交哈希**: `a1b2c3d`
- **作者**: Zhang San <zhangsan@example.com>
- **时间**: 10:30:25
- **文件数**: 3
- **修改文件**:
  - `src/main/kotlin/...`

## 📝 详细内容

（自由书写）
```

## 开发

### 项目结构（摘要）

```
workLogPlugin/
├── src/main/kotlin/com/worklog/
│   ├── actions/          # 菜单动作（生成日志、报告、评审等）
│   ├── ui/               # 对话框与工具窗口
│   ├── services/         # Git、工作日志、AI、统计、评审等
│   ├── models/
│   ├── settings/
│   ├── listeners/      # 关闭提醒、提交 CheckinHandler、启动活动等
│   └── utils/
├── src/main/resources/META-INF/plugin.xml
├── build.gradle.kts
└── gradle.properties
```

### 常用 Gradle 命令

```bash
./gradlew buildPlugin    # 打插件 ZIP
./gradlew runIde         # 沙箱 IDE 调试
./gradlew test           # 单元测试
./gradlew publishPlugin  # 发布到 Marketplace（需事先配置令牌等）
```

本地 Git 行为快速验证：`./gradlew testGit`（可选 `-PtestDate=yyyy-MM-dd`，见 `build.gradle.kts` / `CLAUDE.md`）。

## 贡献

欢迎 Issue 与 Pull Request。

## 许可证

[MIT License](LICENSE)

## 常见问题

### 必须使用 IntelliJ IDEA 吗？

任意基于 IntelliJ 平台且满足版本范围、并启用 Git/VCS 的产品均可尝试；本仓库默认以 **IntelliJ Community** 平台版本构建（`gradle.properties` 中 `platformType`）。

### 没有配置 AI 能否使用？

可以。不配置 API 时仍可拉取 Git 提交、编辑 Markdown、使用历史与导出；统计与报告依赖你已保存的日志文件。

### 代码评审失败或超时？

检查网络与 API 配额；适当减小 **最大 diff 字符数**；在 **文件过滤** 中排除大文件与生成物目录。

### 生成总结时报 HTTP 400？

多为请求体过大：收紧文件过滤、降低单文件大小上限、排除模型权重等大文件目录。

### 日志应该提交到 Git 吗？

由团队策略决定：可纳入仓库便于备份与统计，也可加入 `.gitignore` 仅本地保留。

### 如何卸载？

`Settings` → `Plugins` → 找到 **WorkLog** → 卸载。

## 更新日志

### 当前开发版（相对于仅 v1.0.0 叙述的增量能力）

- AI **代码评审**（暂存区 / 历史提交 / 可选提交前自动执行）。
- **工作统计**与 **周报 / 月报 / 年报**。
- 可配置 **输出模板** 与更丰富的提示词占位符。
- 依赖 **Git4Idea**，与 VCS 提交流程集成（CheckinHandler）。
- 平台兼容区间与构建属性以 `gradle.properties` 为准。

### v1.0.0

- 自动获取 Git 提交、可选 AI 工作总结、多 API 配置、Markdown 编辑与存储、历史与搜索、关闭/定时提醒、文件过滤、多格式导出。

## 联系方式

- 上游仓库：<https://github.com/sutao1/workLogPlugin>
- 问题反馈：使用仓库 **Issues**（将 Email 等替换为你实际维护的联系方式即可）。
