# 🌐 方案 3：GitHub Actions 云端自动编译

## 📋 简介

使用 GitHub Actions 在云端自动编译 Android APK，无需本地安装任何工具。

## ✨ 优势

- ✅ **零本地配置** - 不需要安装 Android Studio 或 SDK
- ✅ **完全免费** - GitHub Actions 对公开仓库免费
- ✅ **自动化** - 每次提交代码自动编译
- ✅ **多平台** - 在 GitHub 服务器上编译，不占用本地资源
- ✅ **可靠** - 使用标准化的编译环境

## 🚀 使用步骤

### 第一步：创建 GitHub 仓库

#### 1. 访问 GitHub
打开浏览器，访问：https://github.com

#### 2. 登录账号
- 如果没有账号，点击 "Sign up" 注册
- 如果有账号，点击 "Sign in" 登录

#### 3. 创建新仓库
- 点击右上角的 "+" 按钮
- 选择 "New repository"
- 填写信息：
  - Repository name: `CallRecordManager`
  - Description: `通话录音管理与纪要生成应用`
  - 选择 Public（公开）或 Private（私有）
  - 不要勾选 "Initialize this repository with a README"
- 点击 "Create repository"

---

### 第二步：配置 API Key（Secret）

#### 1. 进入仓库设置
- 在你的仓库页面
- 点击 "Settings"（设置）

#### 2. 添加 Secret
- 左侧菜单点击 "Secrets and variables"
- 点击 "Actions"
- 点击 "New repository secret"
- 填写：
  - Name: `STEPFUN_API_KEY`
  - Secret: `1FYEJ99v11hFMCS06Xoomi0GTxiEcwjB3fq1WjbLLwP2mdHO426dOt7IlAlvhGQtI`
- 点击 "Add secret"

---

### 第三步：上传代码到 GitHub

#### 方式 A：使用 GitHub Desktop（推荐，最简单）

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
   - 选择你刚创建的仓库
   - 点击 "Publish Repository"

5. **推送代码**
   - 输入 Commit 信息："Initial commit"
   - 点击 "Commit to main"
   - 点击 "Push origin"

#### 方式 B：使用命令行

在终端中执行：

```bash
cd /Users/natsusakai/Documents/CallRecordManager

# 初始化 Git 仓库
git init

# 添加所有文件
git add .

# 提交
git commit -m "Initial commit"

# 添加远程仓库（替换 YOUR_USERNAME 为你的 GitHub 用户名）
git remote add origin https://github.com/YOUR_USERNAME/CallRecordManager.git

# 推送到 GitHub
git branch -M main
git push -u origin main
```

---

### 第四步：触发自动编译

#### 自动触发
代码推送到 GitHub 后，GitHub Actions 会自动开始编译。

#### 手动触发
1. 在 GitHub 仓库页面
2. 点击 "Actions" 标签
3. 左侧选择 "Android CI - 自动编译 APK"
4. 点击 "Run workflow"
5. 选择分支（main）
6. 点击 "Run workflow"

---

### 第五步：查看编译进度

#### 1. 进入 Actions 页面
- 在仓库页面点击 "Actions" 标签

#### 2. 查看工作流运行
- 可以看到正在运行或已完成的工作流
- 点击某个运行记录查看详情

#### 3. 查看日志
- 点击具体的 job（build）
- 可以看到每个步骤的执行日志
- 绿色勾号表示成功，红色叉号表示失败

#### 4. 编译时间
- 通常需要 5-10 分钟
- 首次编译可能需要更长时间（下载依赖）

---

### 第六步：下载 APK

#### 1. 编译完成后
- 在 Actions 页面
- 点击完成的工作流运行

#### 2. 下载 Artifacts
- 滚动到页面底部
- 在 "Artifacts" 部分
- 点击 "app-debug" 下载
- 会下载一个 ZIP 文件

#### 3. 解压
- 解压 ZIP 文件
- 得到 `app-debug.apk`

#### 4. 安装到手机
- 将 APK 传输到手机
- 点击安装

---

## 📊 工作流说明

### 触发条件
- 推送代码到 main/master/develop 分支
- 创建 Pull Request
- 手动触发

### 编译步骤
1. 检出代码
2. 设置 JDK 17
3. 配置 local.properties
4. 授予 Gradle 执行权限
5. 清理项目
6. 编译调试版 APK
7. 上传 APK 作为 Artifact

### 编译环境
- 操作系统：Ubuntu Latest
- Java：JDK 17 (Temurin)
- Android SDK：自动安装

---

## 🔧 自定义配置

### 修改触发分支
编辑 `.github/workflows/android-build.yml`：

```yaml
on:
  push:
    branches: [ main, master, develop, your-branch ]
```

### 编译发布版
在工作流中添加：

```yaml
- name: 🔨 编译发布版 APK
  run: ./gradlew assembleRelease
  
- name: 📦 上传发布版 APK
  uses: actions/upload-artifact@v4
  with:
    name: app-release
    path: app/build/outputs/apk/release/app-release.apk
```

### 添加签名
1. 创建签名密钥
2. 将密钥文件转为 Base64
3. 添加到 GitHub Secrets
4. 在工作流中配置签名

---

## 🎯 优化建议

### 1. 缓存依赖
已自动配置 Gradle 缓存，加快编译速度。

### 2. 并发控制
限制同时运行的工作流数量：

```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

### 3. 定时编译
每天自动编译一次：

```yaml
on:
  schedule:
    - cron: '0 0 * * *'  # 每天 UTC 0:00
```

---

## 📱 下载和安装

### 从 GitHub Actions 下载
1. 进入 Actions 页面
2. 选择成功的运行记录
3. 下载 Artifacts
4. 解压得到 APK

### 从 GitHub Releases 发布
可以创建 Release 并附加 APK：

1. 在仓库页面点击 "Releases"
2. 点击 "Create a new release"
3. 填写版本号和说明
4. 上传 APK 文件
5. 点击 "Publish release"

---

## 🔍 故障排查

### Q1: 工作流失败
**查看日志**：
- 点击失败的运行记录
- 查看红色叉号的步骤
- 阅读错误信息

**常见原因**：
- API Key 未配置
- 代码有语法错误
- 依赖下载失败

### Q2: 找不到 Artifacts
**原因**：
- 编译失败，没有生成 APK
- Artifacts 保留期已过（默认 90 天）

**解决**：
- 重新运行工作流
- 检查编译日志

### Q3: 编译时间过长
**原因**：
- 首次编译需要下载依赖
- GitHub Actions 服务器负载高

**解决**：
- 耐心等待
- 启用缓存（已配置）

---

## 💡 小贴士

1. **私有仓库**：GitHub Actions 对私有仓库有使用限制
2. **保护 Secret**：不要在代码中硬编码 API Key
3. **定期更新**：保持依赖库和工具版本最新
4. **查看日志**：编译失败时仔细查看日志
5. **本地测试**：推送前在本地测试编译

---

## 📚 相关资源

- GitHub Actions 文档：https://docs.github.com/actions
- Android CI/CD 最佳实践：https://developer.android.com/studio/build
- Gradle 文档：https://docs.gradle.org/

---

## 🎉 总结

使用 GitHub Actions 的优势：
- ✅ 无需本地环境
- ✅ 自动化编译
- ✅ 免费使用
- ✅ 可靠稳定

**适合场景**：
- 不想安装大型开发工具
- 需要持续集成/持续部署
- 团队协作开发
- 多平台编译

---

**现在你可以在任何地方，只需推送代码，就能自动编译出 APK！** 🚀
