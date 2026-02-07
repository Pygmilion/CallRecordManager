# 🚀 自动安装和编译脚本使用指南

## 📋 脚本说明

我为你创建了两个脚本：

### 1. `auto_install_and_build.sh` - 完整安装脚本
**功能**：自动安装所有必需工具并编译 APK

**包含内容**：
- ✅ 安装 Homebrew（如果未安装）
- ✅ 安装 Java JDK 17
- ✅ 下载 Android 命令行工具（约 150 MB）
- ✅ 安装 Android SDK（约 300-500 MB）
- ✅ 配置环境变量
- ✅ 编译 APK

**适用场景**：首次使用，电脑上没有 Android 开发环境

### 2. `quick_build.sh` - 快速编译脚本
**功能**：快速编译 APK（假设环境已配置）

**适用场景**：环境已配置好，只需要重新编译

---

## 🎯 使用方法

### 方法一：完整安装（首次使用）

#### 步骤 1：打开终端
在 macOS 上：
- 按 `Command + 空格`
- 输入 "Terminal" 或"终端"
- 按回车打开

#### 步骤 2：进入项目目录
```bash
cd /Users/natsusakai/Documents/CallRecordManager
```

#### 步骤 3：配置 API Key（重要！）
在运行脚本前，先配置 API Key：

```bash
# 编辑 local.properties 文件
nano local.properties
```

在文件中添加或修改：
```properties
STEPFUN_API_KEY=sk-你的真实API密钥
```

按 `Control + X`，然后按 `Y`，最后按回车保存。

#### 步骤 4：运行安装脚本
```bash
./auto_install_and_build.sh
```

#### 步骤 5：等待完成
脚本会自动：
1. 检查并安装 Homebrew（约 2-5 分钟）
2. 安装 Java JDK 17（约 2-3 分钟）
3. 下载 Android 工具（约 5-10 分钟）
4. 配置 SDK（约 3-5 分钟）
5. 编译 APK（约 5-10 分钟）

**总时间**：约 15-30 分钟（取决于网络速度）

---

### 方法二：快速编译（环境已配置）

如果你已经运行过完整安装脚本，之后只需：

```bash
cd /Users/natsusakai/Documents/CallRecordManager
./quick_build.sh
```

编译时间：约 2-5 分钟

---

## 📦 编译结果

编译成功后，APK 文件位置：
```
app/build/outputs/apk/debug/app-debug.apk
```

文件大小：约 10-15 MB

---

## 📱 安装到手机

### 方式 1：通过 USB 连接

#### 准备工作
1. **手机端**：
   - 进入 `设置` → `关于手机`
   - 连续点击 `版本号` 7 次
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

### 方式 2：传输文件安装

1. **找到 APK 文件**：
   ```bash
   open app/build/outputs/apk/debug
   ```
   这会在 Finder 中打开文件夹

2. **传输到手机**：
   - 通过 AirDrop（如果是 Mac + Android 不支持）
   - 通过微信/QQ 发送到手机
   - 通过云盘（百度网盘、OneDrive 等）
   - 通过 USB 复制到手机

3. **在手机上安装**：
   - 找到 APK 文件
   - 点击安装
   - 如果提示不允许安装，去设置中允许该来源

---

## ⚙️ 脚本详细说明

### auto_install_and_build.sh 做了什么？

#### 1. 安装 Homebrew
```bash
# 检查是否已安装
brew --version

# 如果没有，自动安装
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

#### 2. 安装 Java JDK 17
```bash
# 通过 Homebrew 安装
brew install openjdk@17

# 配置环境变量
export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
```

#### 3. 下载 Android 命令行工具
```bash
# 下载地址
https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip

# 解压到
~/Library/Android/sdk/cmdline-tools/latest
```

#### 4. 安装 Android SDK
```bash
# 安装必需组件
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

#### 5. 配置环境变量
自动添加到 `~/.zshrc`：
```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```

#### 6. 编译 APK
```bash
# 清理旧文件
./gradlew clean

# 编译调试版
./gradlew assembleDebug
```

---

## 🔧 常见问题

### Q1: 脚本执行失败，提示 "Permission denied"

**解决方案**：
```bash
chmod +x auto_install_and_build.sh
./auto_install_and_build.sh
```

### Q2: Homebrew 安装失败

**解决方案**：
手动安装 Homebrew：
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

如果网络问题，可以使用国内镜像：
```bash
/bin/zsh -c "$(curl -fsSL https://gitee.com/cunkai/HomebrewCN/raw/master/Homebrew.sh)"
```

### Q3: 下载速度慢

**解决方案**：
- 使用 WiFi 网络
- 避开网络高峰期
- 考虑使用代理

### Q4: 编译时提示 "SDK location not found"

**解决方案**：
检查 `local.properties` 文件，确保有：
```properties
sdk.dir=/Users/你的用户名/Library/Android/sdk
```

### Q5: 编译时提示 API Key 错误

**解决方案**：
确保 `local.properties` 中有有效的 API Key：
```properties
STEPFUN_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
```

### Q6: adb 命令找不到

**解决方案**：
```bash
# 重新加载环境变量
source ~/.zshrc

# 或者使用完整路径
~/Library/Android/sdk/platform-tools/adb devices
```

---

## 📊 资源占用

### 磁盘空间
- Homebrew: 约 100 MB
- Java JDK 17: 约 300 MB
- Android SDK: 约 1-2 GB
- 项目编译: 约 500 MB
- **总计**: 约 2-3 GB

### 网络流量
- Homebrew: 约 50 MB
- Java JDK: 约 150 MB
- Android 工具: 约 150 MB
- Android SDK: 约 300 MB
- Gradle 依赖: 约 200-300 MB
- **总计**: 约 850 MB - 1 GB

### 时间
- 首次完整安装: 15-30 分钟
- 后续编译: 2-5 分钟

---

## 🎯 成功标志

当你看到以下输出时，说明成功：

```
✅ 编译成功！

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📦 APK 文件已生成：
ℹ️  位置: app/build/outputs/apk/debug/app-debug.apk
ℹ️  大小: 12M

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
安装方法：
1️⃣  通过 USB 连接手机，然后执行：
   adb install app/build/outputs/apk/debug/app-debug.apk

2️⃣  或者将 APK 文件传输到手机，点击安装
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 💡 小贴士

1. **首次编译最慢**：需要下载很多依赖，后续会快很多
2. **使用 WiFi**：避免消耗手机流量
3. **保持网络连接**：编译过程中需要下载依赖
4. **不要中断**：让脚本完整运行完
5. **保存密钥**：如果生成发布版，保存好签名密钥

---

## 📞 需要帮助？

如果遇到问题：
1. 查看脚本输出的错误信息
2. 查看本文档的"常见问题"部分
3. 检查网络连接
4. 确认 API Key 配置正确

---

## 🔄 后续使用

环境配置好后，以后只需：

```bash
cd /Users/natsusakai/Documents/CallRecordManager
./quick_build.sh
```

或者直接使用 Gradle：

```bash
# 编译调试版
./gradlew assembleDebug

# 编译发布版
./gradlew assembleRelease

# 清理
./gradlew clean
```

---

## 🎉 总结

**首次使用**：
```bash
cd /Users/natsusakai/Documents/CallRecordManager
nano local.properties  # 配置 API Key
./auto_install_and_build.sh
```

**后续使用**：
```bash
cd /Users/natsusakai/Documents/CallRecordManager
./quick_build.sh
```

**安装到手机**：
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

**祝你编译成功！** 🚀
