# 📱 Android 应用编译与安装完整指南

## 目录
1. [准备工作](#准备工作)
2. [方法一：Android Studio 编译（推荐）](#方法一android-studio-编译推荐)
3. [方法二：命令行编译](#方法二命令行编译)
4. [安装到手机](#安装到手机)
5. [常见问题](#常见问题)

---

## 准备工作

### 1. 安装 Android Studio

#### 下载
访问：https://developer.android.com/studio

选择适合你系统的版本：
- macOS (Intel / Apple Silicon)
- Windows
- Linux

#### 安装
- **macOS**: 下载 DMG 文件，拖到 Applications 文件夹
- **Windows**: 下载 EXE 文件，双击安装
- **Linux**: 下载 tar.gz 文件，解压运行

#### 首次启动配置
1. 启动 Android Studio
2. 选择 "Standard" 安装类型
3. 等待下载 Android SDK（约 2-3 GB）
4. 完成后重启

### 2. 配置 API Key

> ⚠️ **v1.1.0 起**：API Key 已改为在 App 设置页面中配置。
> 打开 App → 点击右上角⚙️齿轮图标 → 填写你的 API Key

获取 API Key：
1. 访问：https://platform.stepfun.com/
2. 注册账号（可用手机号：18565659040）
3. 创建 API Key
4. 复制并粘贴到 App 设置页面

---

## 方法一：Android Studio 编译（推荐）

### 步骤 1：打开项目

1. 启动 Android Studio
2. 点击 `File` → `Open`
3. 选择项目文件夹：`/Users/natsusakai/Documents/CallRecordManager`
4. 点击 `OK`

### 步骤 2：等待 Gradle 同步

- 项目打开后会自动开始同步
- 底部会显示进度："Gradle sync in progress..."
- 首次同步需要下载依赖，约 5-10 分钟
- 完成后会显示："Gradle sync finished"

如果没有自动同步：
- 点击顶部工具栏的 🐘 图标（Sync Project with Gradle Files）

### 步骤 3：编译 APK

#### 方式 A：调试版 APK（快速测试）

1. 点击菜单：`Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
2. 等待编译（首次约 3-5 分钟）
3. 编译完成后会弹出提示框
4. 点击 `locate` 查看 APK 文件

**APK 位置**：
```
app/build/outputs/apk/debug/app-debug.apk
```

#### 方式 B：发布版 APK（正式使用）

1. 点击菜单：`Build` → `Generate Signed Bundle / APK`
2. 选择 `APK`，点击 `Next`
3. 创建密钥库（首次）：
   - 点击 `Create new...`
   - 填写信息：
     - Key store path: 选择保存位置
     - Password: 设置密码（记住！）
     - Alias: 输入别名（如 mykey）
     - Password: 设置密钥密码
     - Validity: 25（年）
     - 填写证书信息（姓名、组织等）
   - 点击 `OK`
4. 选择 `release` 构建类型
5. 勾选 `V1` 和 `V2` 签名
6. 点击 `Finish`
7. 等待编译完成

**APK 位置**：
```
app/build/outputs/apk/release/app-release.apk
```

---

## 方法二：命令行编译

### 前提条件

1. 已安装 Android Studio（包含 Android SDK）
2. 已配置环境变量

#### macOS/Linux 配置环境变量

编辑 `~/.zshrc` 或 `~/.bash_profile`：

```bash
# Android SDK
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

然后执行：
```bash
source ~/.zshrc
```

#### Windows 配置环境变量

1. 右键 "此电脑" → "属性"
2. "高级系统设置" → "环境变量"
3. 添加：
   - `ANDROID_HOME`: `C:\Users\你的用户名\AppData\Local\Android\Sdk`
   - `Path` 中添加：
     - `%ANDROID_HOME%\tools`
     - `%ANDROID_HOME%\platform-tools`

### 编译步骤

#### 1. 进入项目目录

```bash
cd /Users/natsusakai/Documents/CallRecordManager
```

#### 2. 清理旧的构建文件

**macOS/Linux**:
```bash
./gradlew clean
```

**Windows**:
```cmd
gradlew.bat clean
```

#### 3. 编译调试版 APK

**macOS/Linux**:
```bash
./gradlew assembleDebug
```

**Windows**:
```cmd
gradlew.bat assembleDebug
```

#### 4. 编译发布版 APK

**macOS/Linux**:
```bash
./gradlew assembleRelease
```

**Windows**:
```cmd
gradlew.bat assembleRelease
```

#### 5. 查看编译结果

编译成功后，APK 文件位置：

- **调试版**：`app/build/outputs/apk/debug/app-debug.apk`
- **发布版**：`app/build/outputs/apk/release/app-release.apk`

### 使用编译脚本（macOS/Linux）

我已经为你创建了一个编译脚本：

```bash
# 进入项目目录
cd /Users/natsusakai/Documents/CallRecordManager

# 运行编译脚本
./build.sh
```

脚本会自动：
1. 检查配置
2. 清理旧文件
3. 编译 APK
4. 显示 APK 位置

---

## 安装到手机

### 方法 1：通过 USB 连接安装（推荐）

#### 准备工作

1. **手机端设置**：
   - 进入 `设置` → `关于手机`
   - 连续点击 `版本号` 7 次，启用开发者选项
   - 返回 `设置` → `开发者选项`
   - 启用 `USB 调试`

2. **连接手机**：
   - 用 USB 线连接手机和电脑
   - 手机上点击 `允许 USB 调试`

#### 安装命令

```bash
# 检查设备是否连接
adb devices

# 安装 APK
adb install app/build/outputs/apk/debug/app-debug.apk

# 如果已安装，覆盖安装
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### 在 Android Studio 中直接运行

1. 连接手机
2. 顶部设备选择器选择你的手机
3. 点击绿色的 Run 按钮（▶️）
4. 应用会自动安装并启动

### 方法 2：传输 APK 文件到手机

#### 步骤 1：找到 APK 文件

在电脑上找到编译好的 APK：
```
app/build/outputs/apk/debug/app-debug.apk
```

#### 步骤 2：传输到手机

**方式 A：通过 USB**
- 连接手机到电脑
- 将 APK 文件复制到手机的 `Download` 文件夹

**方式 B：通过云盘**
- 上传 APK 到云盘（如百度网盘、OneDrive）
- 在手机上下载

**方式 C：通过 AirDrop（macOS + iPhone）**
- 不适用，iPhone 不支持 APK

**方式 D：通过微信/QQ**
- 将 APK 发送到微信文件传输助手
- 在手机上下载

#### 步骤 3：在手机上安装

1. 在手机上找到 APK 文件
2. 点击 APK 文件
3. 如果提示"不允许安装未知来源应用"：
   - 点击 `设置`
   - 允许该来源（如"文件管理器"）安装应用
   - 返回继续安装
4. 点击 `安装`
5. 等待安装完成
6. 点击 `打开` 启动应用

### 方法 3：通过 Android Studio 无线调试（Android 11+）

1. 手机和电脑连接同一 WiFi
2. 手机开启无线调试：
   - `设置` → `开发者选项` → `无线调试`
   - 点击 `使用配对码配对设备`
3. Android Studio 中：
   - `Tools` → `Device Manager`
   - 点击 `Pair using pairing code`
   - 输入手机上显示的配对码
4. 配对成功后，可以无线安装和调试

---

## 常见问题

### Q1: Gradle 同步失败

**问题**：下载依赖失败

**解决方案**：
```bash
# 清理 Gradle 缓存
rm -rf ~/.gradle/caches/

# 重新同步
./gradlew clean build --refresh-dependencies
```

或在 Android Studio 中：
- `File` → `Invalidate Caches / Restart`
- 选择 `Invalidate and Restart`

### Q2: 编译错误 "SDK location not found"

**问题**：找不到 Android SDK

**解决方案**：
在项目根目录创建或编辑 `local.properties`：

```properties
sdk.dir=/Users/你的用户名/Library/Android/sdk
```

macOS 默认路径：
```
/Users/你的用户名/Library/Android/sdk
```

Windows 默认路径：
```
C:\Users\你的用户名\AppData\Local\Android\Sdk
```

### Q3: 编译错误 "BuildConfig.STEPFUN_API_KEY"

**问题**：API Key 未配置

**解决方案**：
> v1.1.0 起 API Key 已改为在 App 设置页面中配置，无需在编译时配置。

### Q4: 手机无法安装 APK

**问题**：提示"解析包时出现问题"

**解决方案**：
1. 检查手机系统版本 >= Android 7.0
2. 重新下载 APK（可能传输损坏）
3. 清理手机存储空间
4. 重启手机后再试

### Q5: 应用闪退

**问题**：安装后打开立即闪退

**解决方案**：
1. 检查 API Key 是否正确配置
2. 授予应用所需权限：
   - 存储权限
   - 网络权限
3. 查看崩溃日志：
```bash
adb logcat | grep "com.callrecord.manager"
```

### Q6: 编译速度慢

**问题**：每次编译需要很长时间

**解决方案**：
1. 启用 Gradle 守护进程：
   在 `gradle.properties` 中添加：
   ```properties
   org.gradle.daemon=true
   org.gradle.parallel=true
   org.gradle.caching=true
   ```

2. 增加 Gradle 内存：
   ```properties
   org.gradle.jvmargs=-Xmx4096m
   ```

3. 使用增量编译（默认已启用）

### Q7: 找不到 APK 文件

**问题**：编译成功但找不到 APK

**解决方案**：
在项目根目录执行：
```bash
find . -name "*.apk"
```

或在 Android Studio 中：
- 点击 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
- 编译完成后点击弹出通知中的 `locate`

---

## 📊 APK 文件说明

### 调试版 vs 发布版

| 特性 | 调试版 (debug) | 发布版 (release) |
|------|---------------|-----------------|
| 文件名 | app-debug.apk | app-release.apk |
| 签名 | 自动签名 | 需要手动签名 |
| 大小 | 较大 | 较小（已优化） |
| 性能 | 较慢 | 较快 |
| 调试 | 可调试 | 不可调试 |
| 用途 | 开发测试 | 正式发布 |

### APK 大小

- **未压缩**：约 15-20 MB
- **压缩后**：约 8-12 MB
- **安装后**：约 30-40 MB

---

## 🎯 推荐流程

### 第一次编译（学习测试）

1. ✅ 安装 Android Studio
2. ✅ 配置 API Key
3. ✅ 打开项目
4. ✅ 同步 Gradle
5. ✅ 编译调试版 APK
6. ✅ 通过 USB 安装到手机
7. ✅ 测试功能

### 正式使用

1. ✅ 编译发布版 APK
2. ✅ 签名 APK
3. ✅ 安装到手机
4. ✅ 授予权限
5. ✅ 配置 API Key（如果需要）
6. ✅ 开始使用

---

## 📱 安装后的首次使用

### 1. 授予权限

首次打开应用时，需要手动授予权限：

1. 进入 `设置` → `应用` → `通话录音管理`
2. 点击 `权限`
3. 授予以下权限：
   - ✅ 存储权限（读取录音文件）
   - ✅ 网络权限（调用 API）
   - ✅ 联系人权限（显示联系人名称，可选）

### 2. 准备测试数据

在手机上准备一些通话录音文件：
- 放到 `/Recordings` 或 `/CallRecordings` 目录
- 支持格式：mp3, m4a, wav, amr, 3gp

### 3. 开始使用

1. 打开应用
2. 点击"扫描录音"按钮
3. 等待扫描完成
4. 选择一个录音
5. 点击"转写并生成纪要"
6. 等待处理（约 1-2 分钟）
7. 在"纪要"标签页查看结果

---

## 🎉 成功标志

当你看到以下内容时，说明编译安装成功：

✅ Android Studio 编译无错误
✅ 生成了 APK 文件
✅ APK 成功安装到手机
✅ 应用可以正常打开
✅ 可以扫描到录音文件
✅ 转写功能正常工作
✅ 纪要生成成功

---

## 💡 小贴士

1. **首次编译**需要下载很多依赖，请耐心等待
2. **调试版 APK** 适合快速测试，无需签名
3. **发布版 APK** 需要签名，但性能更好
4. **保存好签名密钥**，后续更新需要用同一个密钥
5. 建议使用 **WiFi** 下载依赖，避免消耗流量
6. **真机测试** 比模拟器更准确

---

## 📞 需要帮助？

如果遇到问题：
1. 查看本文档的"常见问题"部分
2. 查看 Android Studio 的 Build 输出
3. 搜索错误信息
4. 查阅官方文档

---

**祝你编译成功！** 🚀
