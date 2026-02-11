# 🌐 GitHub Actions 云端编译 - 完整操作指南

## ✅ 已完成的准备工作

我已经为你完成了以下工作：

1. ✅ Git 仓库已初始化
2. ✅ 所有代码文件已添加
3. ✅ GitHub Actions 配置文件已创建（`.github/workflows/android-build.yml`）
4. ✅ .gitignore 文件已配置
5. ✅ API Key 已配置在 `local.properties`

---

## 🚀 现在需要你完成的步骤

### 第一步：创建 GitHub 账号（如果没有）

1. 访问：https://github.com
2. 点击 "Sign up"
3. 填写邮箱、密码、用户名
4. 验证邮箱
5. 完成注册

---

### 第二步：创建新仓库

#### 方式 A：通过网页创建（推荐）

1. 登录 GitHub
2. 点击右上角的 "+" 按钮
3. 选择 "New repository"
4. 填写信息：
   - **Repository name**: `CallRecordManager`
   - **Description**: `通话录音管理与AI纪要生成应用`
   - **Public** 或 **Private**（选择 Public 可以免费使用 Actions）
   - ⚠️ **不要勾选** "Initialize this repository with a README"
5. 点击 "Create repository"

#### 方式 B：通过命令行创建

如果你安装了 GitHub CLI：
```bash
gh repo create CallRecordManager --public --source=. --remote=origin
```

---

### 第三步：配置 API Key Secret

这一步很重要！必须配置 API Key，否则编译会失败。

1. 在你的 GitHub 仓库页面
2. 点击 "Settings"（设置）
3. 左侧菜单点击 "Secrets and variables" → "Actions"
4. 点击 "New repository secret"
5. 填写：
   - **Name**: `STEPFUN_API_KEY`
   - **Secret**: `your_api_key_here`

> ⚠️ **注意**：v1.1.0 起 API Key 已改为在 App 设置页面中配置，GitHub Actions Secret 仅用于编译兼容，实际使用时请在 App 内设置。
6. 点击 "Add secret"

---

### 第四步：推送代码到 GitHub

#### 方式 A：使用 GitHub Desktop（最简单）

1. **下载 GitHub Desktop**
   - 访问：https://desktop.github.com/
   - 下载并安装

2. **登录账号**
   - 打开 GitHub Desktop
   - File → Options → Sign in
   - 登录你的 GitHub 账号

3. **添加本地仓库**
   - File → Add Local Repository
   - 选择：`/Users/natsusakai/Documents/CallRecordManager`
   - 点击 "Add Repository"

4. **发布到 GitHub**
   - 点击 "Publish repository"
   - 确认仓库名称：`CallRecordManager`
   - 取消勾选 "Keep this code private"（如果想用免费 Actions）
   - 点击 "Publish Repository"

5. **推送代码**
   - 确认所有文件已添加
   - 输入 Commit 信息："Initial commit: Complete Android app"
   - 点击 "Commit to main"
   - 点击 "Push origin"

#### 方式 B：使用命令行

在终端中执行以下命令：

```bash
# 进入项目目录
cd /Users/natsusakai/Documents/CallRecordManager

# 添加所有文件
git add .

# 提交
git commit -m "Initial commit: Complete Android app"

# 添加远程仓库（替换 YOUR_USERNAME 为你的 GitHub 用户名）
git remote add origin https://github.com/YOUR_USERNAME/CallRecordManager.git

# 推送到 GitHub
git branch -M main
git push -u origin main
```

**注意**：第一次推送时会要求输入 GitHub 用户名和密码（或 Personal Access Token）

---

### 第五步：触发自动编译

代码推送到 GitHub 后，GitHub Actions 会自动开始编译！

#### 查看编译进度

1. 在 GitHub 仓库页面
2. 点击 "Actions" 标签
3. 可以看到正在运行的工作流
4. 点击工作流查看详细日志
5. 编译通常需要 5-10 分钟

#### 手动触发编译

如果没有自动触发，可以手动触发：

1. 在 "Actions" 页面
2. 左侧选择 "Android CI - 自动编译 APK"
3. 点击 "Run workflow"
4. 选择分支（main）
5. 点击 "Run workflow"

---

### 第六步：下载编译好的 APK

#### 编译完成后

1. 在 "Actions" 页面
2. 点击已完成的工作流运行（绿色勾号）
3. 滚动到页面底部
4. 在 "Artifacts" 部分
5. 点击 "app-debug" 下载
6. 会下载一个 ZIP 文件

#### 解压并安装

1. 解压下载的 ZIP 文件
2. 得到 `app-debug.apk`
3. 传输到手机
4. 在手机上点击安装

---

## 📊 编译状态说明

### ✅ 成功标志

在 Actions 页面看到：
- 绿色勾号 ✓
- "Build" 步骤全部成功
- 底部有 "Artifacts" 可下载

### ❌ 失败原因

如果编译失败，常见原因：
1. API Key 未配置或配置错误
2. 代码有语法错误
3. 网络问题导致依赖下载失败

**解决方法**：
- 点击失败的步骤查看详细日志
- 根据错误信息修复问题
- 重新推送代码或手动触发

---

## 🎯 完整流程图

```
1. 创建 GitHub 账号
   ↓
2. 创建新仓库
   ↓
3. 配置 API Key Secret
   ↓
4. 推送代码到 GitHub
   ↓
5. GitHub Actions 自动编译
   ↓
6. 下载 APK
   ↓
7. 安装到手机
   ↓
8. 完成！🎉
```

---

## 💡 推荐工具

### GitHub Desktop（最简单）

- 下载：https://desktop.github.com/
- 图形界面，最容易操作
- 不需要记命令
- 适合新手

### 命令行（适合熟悉终端的用户）

- 更灵活
- 更快速
- 需要记住命令

---

## 📱 编译结果

### APK 信息
- **文件名**：`app-debug.apk`
- **大小**：约 10-15 MB
- **类型**：调试版
- **支持系统**：Android 7.0+

### 功能特性
- ✅ 录音文件扫描和管理
- ✅ 语音转文字（说话人分离）
- ✅ AI 智能纪要生成
- ✅ 结构化输出（标题、摘要、要点、待办）
- ✅ 本地数据库存储

---

## 🔧 故障排查

### Q1: 推送代码时要求密码

**问题**：GitHub 不再支持密码认证

**解决**：使用 Personal Access Token
1. GitHub 设置 → Developer settings → Personal access tokens
2. Generate new token
3. 勾选 `repo` 权限
4. 复制 token
5. 推送时用 token 代替密码

### Q2: Actions 没有自动运行

**原因**：可能是仓库设置问题

**解决**：
1. Settings → Actions → General
2. 确保 "Allow all actions" 已启用
3. 手动触发一次工作流

### Q3: 编译失败 - API Key 错误

**原因**：Secret 未配置或名称错误

**解决**：
1. 确认 Secret 名称是 `STEPFUN_API_KEY`
2. 确认 Secret 值正确
3. 重新运行工作流

### Q4: 找不到 Artifacts

**原因**：编译失败，没有生成 APK

**解决**：
1. 查看 Actions 日志
2. 找到失败的步骤
3. 根据错误信息修复
4. 重新推送代码

---

## 📞 需要帮助？

### 查看日志
- 在 Actions 页面点击工作流
- 点击具体的 job
- 查看每个步骤的输出

### 常见错误信息

**"API Key not found"**
→ 检查 Secret 配置

**"Gradle build failed"**
→ 查看详细日志，可能是代码问题

**"No space left on device"**
→ GitHub Actions 空间不足，清理缓存

---

## ✅ 检查清单

在推送代码前，确认：

- [ ] GitHub 账号已创建
- [ ] 新仓库已创建
- [ ] API Key Secret 已配置
- [ ] Git 用户信息已配置
- [ ] 所有文件已添加到 Git
- [ ] 代码已提交
- [ ] 远程仓库已添加
- [ ] 代码已推送

推送后：

- [ ] Actions 已自动运行
- [ ] 编译成功（绿色勾号）
- [ ] Artifacts 已生成
- [ ] APK 已下载
- [ ] APK 已安装到手机

---

## 🎉 成功后

恭喜！你已经成功使用 GitHub Actions 云端编译了 Android 应用！

**下次更新代码**：
1. 修改代码
2. 推送到 GitHub
3. 自动重新编译
4. 下载新的 APK

**完全自动化！** 🚀

---

## 📚 相关资源

- GitHub Actions 文档：https://docs.github.com/actions
- GitHub Desktop 教程：https://docs.github.com/desktop
- Git 基础教程：https://git-scm.com/book/zh/v2

---

**现在开始第一步：创建 GitHub 账号或登录！** ✨
