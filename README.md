# WorkLog - IDEA 工作日志插件

一款功能强大的 IntelliJ IDEA 工作日志管理插件，帮助开发者自动记录和管理每日工作内容。

## 主要功能

- ✅ **自动获取 Git 提交信息** - 自动读取指定日期的所有 Git 提交记录
- ✅ **AI 智能总结** - 支持调用大模型 API（OpenAI 格式或自定义格式）自动生成工作总结
- ✅ **代码访问权限控制** - 可配置是否允许读取代码内容，严格保护隐私
- ✅ **Markdown 格式** - 使用 Markdown 格式编辑和存储日志，方便阅读和分享
- ✅ **IDE 关闭提醒** - 关闭 IDEA 时自动提醒填写工作日志，避免遗忘
- ✅ **历史日志管理** - 查看、搜索和管理历史工作日志
- ✅ **定时提醒** - 可配置每天定时提醒填写日志
- ✅ **多格式导出** - 支持导出为 Markdown、HTML 或 PDF 格式

## 系统要求

### 用户使用
- IntelliJ IDEA 2023.1 或更高版本（IDEA 已内置 Java 运行时）
- Git（用于获取提交记录）

### 开发者构建
- IntelliJ IDEA 2023.1 或更高版本
- JDK 17 或更高版本（仅用于编译插件）
- Git

## 安装

### 从源码构建

1. 克隆仓库
```bash
git clone https://github.com/yourusername/workLogPlugin.git
cd workLogPlugin
```

2. 构建插件
```bash
./gradlew buildPlugin
```

3. 安装插件
   - 打开 IntelliJ IDEA
   - 进入 `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
   - 选择 `build/distributions/workLogPlugin-1.0.0.zip`

### 从 JetBrains Marketplace 安装

（待发布到 Marketplace 后补充）

## 使用指南

### 快速开始

1. **打开工作日志面板**
   - 点击右侧工具栏的 `WorkLog` 标签
   - 或使用菜单 `Tools` → `生成工作日志`

2. **生成今日日志**
   - 在工作日志面板中点击 `生成日志` 按钮
   - 选择日期（默认为今天）
   - 选择是否允许读取代码内容
   - 点击 `OK`，插件将自动获取 Git 提交记录并生成日志

3. **编辑和保存**
   - 在 Markdown 编辑器中编辑日志内容
   - 点击 `保存` 按钮保存日志

### 配置 AI API

1. 打开设置
   - `Settings` → `Tools` → `WorkLog`

2. 配置 AI API
   - **OpenAI 格式**（适用于 OpenAI、Azure OpenAI、通义千问、文心一言等）
     - API URL: 例如 `https://api.openai.com/v1/chat/completions`
     - API Key: 你的 API 密钥
     - 模型名称: 例如 `gpt-4`

   - **自定义格式**
     - 提供自定义的请求模板和响应解析路径
     - 支持任意格式的大模型 API

3. 配置代码访问权限
   - 勾选 `允许读取代码内容` 可让 AI 分析代码变更
   - 未勾选时只会读取 Git 提交信息，不会访问代码内容

### 功能详解

#### 1. 自动提醒

**IDE 关闭提醒**
- 关闭 IDEA 时，如果今日没有填写日志，会弹出提醒对话框
- 可以选择立即生成、打开编辑或稍后填写
- 可在设置中关闭此功能

**定时提醒**
- 在设置中启用定时提醒
- 配置提醒时间（默认 17:30）
- 每天到时会显示通知提醒填写日志

#### 2. 历史日志管理

- 点击 `历史记录` 按钮查看所有日志
- 支持搜索功能，可按日期或内容搜索
- 支持预览和删除操作

#### 3. 导出功能

- 选择要导出的日志
- 点击 `导出` 按钮
- 选择导出格式（Markdown/HTML/PDF）
- 选择保存位置

#### 4. 存储位置

- 默认存储在项目根目录的 `.worklogs/` 文件夹
- 每个日志对应两个文件：
  - `yyyy-MM-dd.md` - Markdown 格式的日志内容
  - `yyyy-MM-dd.json` - 元数据（提交信息、创建时间等）

## 配置选项

### AI API 设置
- API URL
- API Key
- 模型名称
- API 格式（OpenAI / 自定义）

### 自定义 API 格式
- 请求模板（JSON）
- 响应 JSON 路径

### 代码访问权限
- 允许读取代码内容
- 记住我的选择

### 提醒设置
- 启用定时提醒
- 提醒时间
- IDE 关闭时提醒

### 存储和导出
- 默认导出格式
- 存储路径

### 提示词模板
- 系统提示词
- 用户提示词模板

ceshi
## 隐私和安全

- **代码访问控制**：严格遵守用户设置，未授权时绝不读取代码内容
- **本地存储**：所有日志存储在本地项目目录，不会上传到任何服务器
- **API Key 加密**：API Key 在配置文件中加密存储
- **透明操作**：所有操作都会显示在 UI 中，用户可完全控制

## 日志格式示例

```markdown
# 工作日志 - 2025年12月16日 星期一

## 🤖 AI 工作总结

今日主要完成了以下工作：
1. 实现了工作日志插件的核心功能
2. 添加了 Git 提交信息获取模块
3. 集成了大模型 API 用于自动总结

## 💾 Git 提交记录

共 5 次提交：

### 1. 实现 GitService 核心功能
- **提交哈希**: `a1b2c3d`
- **作者**: Zhang San <zhangsan@example.com>
- **时间**: 10:30:25
- **文件数**: 3
- **修改文件**:
  - `src/main/kotlin/com/worklog/services/GitService.kt`
  - `src/main/kotlin/com/worklog/models/GitCommit.kt`

...

## 📝 详细内容

今天主要完成了工作日志插件的开发...
```

## 开发

### 项目结构

```
workLogPlugin/
├── src/main/kotlin/com/worklog/
│   ├── actions/          # 动作相关
│   ├── ui/               # UI 组件
│   ├── services/         # 核心服务
│   ├── models/           # 数据模型
│   ├── settings/         # 配置管理
│   ├── listeners/        # 事件监听器
│   └── utils/            # 工具类
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml    # 插件配置
├── build.gradle.kts
└── gradle.properties
```

### 构建和测试

```bash
# 构建插件
./gradlew buildPlugin

# 运行测试
./gradlew test

# 在 IDE 中运行插件
./gradlew runIde
```

### 发布

```bash
# 构建发布版本
./gradlew buildPlugin

# 发布到 JetBrains Marketplace
./gradlew publishPlugin
```

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

[MIT License](LICENSE)

## 常见问题

### Q: 使用插件需要安装 Java 吗？
A: **不需要**！只要你的 IDEA 版本是 2023.1 或更高，就可以直接使用插件。
- IntelliJ IDEA 自带完整的 Java 运行环境
- 插件运行在 IDEA 的 JVM 中
- 只有开发插件时才需要 JDK 17+

### Q: 插件如何获取 Git 提交信息？
A: 插件使用 IntelliJ IDEA 的 Git4Idea API 来访问 Git 仓库，不需要额外的 Git 命令行工具。

### Q: AI 总结支持哪些大模型？
A: 支持所有兼容 OpenAI API 格式的大模型，包括：
- OpenAI GPT-4/GPT-3.5
- Azure OpenAI
- 阿里云通义千问
- 百度文心一言
- 以及其他自定义格式的 API

### Q: 不使用 AI 功能可以吗？
A: 完全可以！AI 功能是可选的。即使不配置 AI API，插件仍然会：
- 自动获取 Git 提交记录
- 生成格式化的日志模板
- 提供 Markdown 编辑器
- 支持历史日志管理和导出

### Q: 日志存储在哪里？
A: 默认存储在项目根目录的 `.worklogs/` 文件夹中。你可以：
- 将这个文件夹加入 Git 版本控制，与团队共享日志
- 加入 `.gitignore`，保持日志私密
- 在设置中修改存储路径

### Q: 如何卸载插件？
A: `Settings` → `Plugins` → 找到 `WorkLog` → 点击 `⚙️` → `Uninstall`

## 联系方式

- GitHub: https://github.com/yourusername/workLogPlugin
- Email: support@worklog.com
- Issue: https://github.com/yourusername/workLogPlugin/issues
