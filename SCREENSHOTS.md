# 插件截图说明

## 如何添加插件介绍图片

### 方法一：使用 GitHub 托管图片（推荐）

1. **在项目根目录创建 screenshots 文件夹**：
   ```bash
   mkdir screenshots
   ```

2. **准备以下截图**（建议尺寸：宽度 600-800px）：
   - `main-interface.png` - 主界面截图
   - `ai-summary.png` - AI 智能总结功能演示
   - `api-config.png` - API 配置管理界面

3. **将截图放到 screenshots 文件夹**：
   ```
   workLogPlugin/
   ├── screenshots/
   │   ├── main-interface.png
   │   ├── ai-summary.png
   │   └── api-config.png
   ```

4. **提交到 GitHub**：
   ```bash
   git add screenshots/
   git commit -m "Add plugin screenshots"
   git push origin main
   ```

5. **图片 URL 格式**：
   ```
   https://raw.githubusercontent.com/sutao1/workLogPlugin/main/screenshots/main-interface.png
   ```

### 方法二：使用图床服务

如果不想使用 GitHub，也可以使用图床服务：

1. **上传图片到图床**（推荐使用）：
   - [imgur.com](https://imgur.com)
   - [sm.ms](https://sm.ms)
   - [路过图床](https://imgse.com)

2. **获取图片直链**，替换 plugin.xml 中的 URL

3. **示例**：
   ```html
   <img src="https://i.imgur.com/xxxxx.png" alt="截图" width="600"/>
   ```

### 方法三：使用本地文件（仅限本地测试）

1. **将图片放到资源目录**：
   ```
   src/main/resources/screenshots/
   ├── main-interface.png
   ├── ai-summary.png
   └── api-config.png
   ```

2. **在 plugin.xml 中引用**：
   ```html
   <img src="/screenshots/main-interface.png" alt="截图" width="600"/>
   ```

   ⚠️ **注意**：这种方法只在 IDE 内部显示，发布到 JetBrains Marketplace 后不会显示。

## 当前配置

plugin.xml 中已经配置了以下图片：

```html
<!-- 主界面 -->
<img src="https://raw.githubusercontent.com/sutao1/workLogPlugin/main/screenshots/main-interface.png"
     alt="主界面截图" width="600" border="0"/>

<!-- AI 智能总结 -->
<img src="https://raw.githubusercontent.com/sutao1/workLogPlugin/main/screenshots/ai-summary.png"
     alt="AI智能总结" width="600" border="0"/>

<!-- API 配置管理 -->
<img src="https://raw.githubusercontent.com/sutao1/workLogPlugin/main/screenshots/api-config.png"
     alt="API配置管理" width="600" border="0"/>
```

## 截图建议

### 内容建议
- **主界面**：展示插件的整体界面，包括日期选择、按钮等
- **AI 总结**：展示 AI 生成的工作日志示例
- **API 配置**：展示多 API 配置的表格界面

### 技术要求
- 格式：PNG 或 JPG
- 宽度：600-800px（推荐 600px）
- 大小：每张图片建议不超过 500KB
- 清晰度：确保文字清晰可读

### 截图技巧
1. 使用浅色主题（Light Theme）截图，效果更好
2. 确保窗口大小适中，不要太大或太小
3. 可以使用 macOS 的截图工具（Cmd+Shift+4）或 Windows 的截图工具
4. 可以使用 IDEA 自带的截图功能：菜单栏 → View → Appearance → Presentation Assistant

## 测试

构建插件后，在 IDEA 的插件管理界面可以看到图片效果：
```bash
./gradlew buildPlugin
```

然后在 IDEA 中：Settings → Plugins → 齿轮图标 → Install Plugin from Disk → 选择生成的 zip 文件
