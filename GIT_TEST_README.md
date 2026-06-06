# Git 功能测试文档

## 测试目的

测试 GitService 是否能正确获取指定日期的 Git 提交记录。

## 如何运行测试

### 方法 1: 使用 Gradle 命令（推荐）

在项目根目录下运行：

```bash
# 测试默认日期（2025-12-16）
./gradlew testGit

# 测试自定义日期
./gradlew testGit -PtestDate=2025-12-15
```

### 方法 2: 在 IDE 中运行

1. 打开文件：`src/test/kotlin/com/worklog/services/GitServiceTest.kt`
2. 找到 `main` 方法
3. 右键点击 `main` 方法，选择 "Run GitServiceTest"

## 测试报告说明

测试会输出详细的报告，包括：

### 1. Git 仓库检查
```
【测试 1: Git 仓库检查】
当前工作目录: /path/to/project
✓ Git 仓库路径: .git
```

### 2. Git 用户配置
```
【测试 2: Git 用户配置】
✓ 用户 email: your@email.com
✓ 用户名称: Your Name
```

### 3. Git 提交记录测试
```
【测试 3: Git log 命令】
执行命令:
  git log --all --author=your@email.com --since=2025-12-16 00:00:00 ...

✓ Git 命令执行成功
```

## 测试结果分析

### 成功找到提交

如果测试日期有提交记录，会显示：

```
Git 原始输出:
--------------------------------------------------------------------------------
abc123|abc|修复登录bug|Your Name|your@email.com|1734336000
src/main/Login.kt
--------------------------------------------------------------------------------

解析的提交记录:
提交 #1:
  Hash: abc
  消息: 修复登录bug
  作者: Your Name <your@email.com>
  时间: 2025-12-16T10:30:00Z

✓ 成功找到 1 条提交记录
```

### 没有找到提交（email 不匹配）

如果没有找到提交，但该日期有其他用户的提交：

```
⚠ 在 2025-12-16 没有找到当前用户的提交记录

【额外测试：查询所有用户的提交】
该日期存在以下用户的提交:
  - other-user@company.com
      feat: 添加新功能
  - team-member@company.com
      fix: 修复bug

⚠ 您的 email (your@email.com) 与上述邮箱不匹配
  可能原因:
  1. Git 配置的 email 与提交时使用的 email 不一致
  2. 提交是在不同的机器/环境下完成的

  解决方案:
  - 如果 your@email.com 是正确的邮箱，请确保提交时使用了这个邮箱
  - 如果上述某个邮箱是您的，请更新 git config:
    git config user.email "correct@email.com"
```

### 该日期确实没有提交

```
⚠ 在 2025-12-16 没有找到当前用户的提交记录

【额外测试：查询所有用户的提交】
✓ 该日期所有用户都没有提交记录
```

## 常见问题

### Q1: 为什么找不到我的提交记录？

**A:** 可能的原因：

1. **Email 不匹配**
   - 查看测试报告中的 "Git 用户配置" 部分
   - 确认 email 与您提交代码时使用的 email 一致
   - 如果不一致，更新配置：`git config user.email "正确的email"`

2. **日期选择错误**
   - 确认测试日期是否正确
   - 注意时区问题

3. **分支问题**
   - 提交可能在其他分支上
   - 测试会查询 `--all`（所有分支），理论上应该能找到

### Q2: 如何修改测试日期？

**A:** 两种方式：

1. **命令行参数**：
   ```bash
   ./gradlew testGit -PtestDate=2025-12-15
   ```

2. **修改测试代码**：
   编辑 `GitServiceTest.kt` 文件，修改默认日期：
   ```kotlin
   val testDate = LocalDate.of(2025, 12, 15)
   ```

### Q3: 测试在哪个目录运行？

**A:** 测试会在当前工作目录（项目根目录）下运行，检查该目录的 Git 仓库。

### Q4: 我的项目有多个 Git 仓库怎么办？

**A:** 测试只检查项目根目录的 Git 仓库。如果您有子模块，需要在子模块目录下单独运行测试。

## 测试示例

### 示例 1: 测试昨天的提交

```bash
# 假设今天是 2025-12-17
./gradlew testGit -PtestDate=2025-12-16
```

### 示例 2: 测试一周前的提交

```bash
./gradlew testGit -PtestDate=2025-12-10
```

### 示例 3: 测试特定日期

```bash
./gradlew testGit -PtestDate=2025-11-25
```

## 文件位置

- **测试代码**: `src/test/kotlin/com/worklog/services/GitServiceTest.kt`
- **被测试的服务**: `src/main/kotlin/com/worklog/services/GitService.kt`
- **构建配置**: `build.gradle.kts`（包含 testGit 任务定义）

## 下一步

如果测试发现问题，可以：

1. 检查 Git 配置是否正确
2. 确认测试日期是否有提交
3. 查看测试报告中的详细信息
4. 根据提示修复配置问题
