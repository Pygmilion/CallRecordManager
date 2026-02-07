# 🚀 快速启动指南

## 第一步：检查环境

### 必需软件
- [ ] Android Studio Hedgehog (2023.1.1+)
- [ ] JDK 17+
- [ ] Android SDK 34

### 检查命令
```bash
# 检查 JDK
java -version

# 应该看到类似输出：
# java version "17.0.x"
```

---

## 第二步：配置 API Key

### 1. 获取阶跃星辰 API Key
访问：https://platform.stepfun.com/
- 注册账号（可用手机号：18565659040）
- 创建 API Key
- 复制密钥

### 2. 配置到项目
编辑文件：`CallRecordManager/local.properties`

```properties
STEPFUN_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
```

⚠️ **重要**：将 `sk-xxxxxxxxxxxxxxxxxxxxxxxx` 替换为你的真实 API Key

---

## 第三步：导入项目

### 1. 打开 Android Studio
启动 Android Studio

### 2. 导入项目
- 点击 `File` → `Open`
- 选择 `CallRecordManager` 文件夹
- 点击 `OK`

### 3. 等待 Gradle 同步
- 首次打开会自动下载依赖
- 可能需要 5-10 分钟
- 底部会显示进度

---

## 第四步：准备设备

### 方式 A：使用真机（推荐）

#### Android 手机设置
1. 进入 `设置` → `关于手机`
2. 连续点击 `版本号` 7 次，启用开发者选项
3. 返回 `设置` → `开发者选项`
4. 启用 `USB 调试`
5. 用 USB 线连接电脑
6. 手机上点击 `允许 USB 调试`

#### Android Studio 中
- 顶部设备选择器应该显示你的手机
- 如果没有，点击刷新按钮

### 方式 B：使用模拟器

#### 创建模拟器
1. 点击 `Tools` → `Device Manager`
2. 点击 `Create Device`
3. 选择 `Pixel 6` 或其他设备
4. 选择 `API 34` 系统镜像
5. 点击 `Download`（首次需要下载）
6. 点击 `Finish`

#### 启动模拟器
- 在 Device Manager 中点击播放按钮
- 等待模拟器启动（约 1-2 分钟）

---

## 第五步：运行应用

### 1. 点击 Run 按钮
- 绿色三角形按钮
- 或按快捷键 `Shift + F10`

### 2. 等待编译
- 首次编译需要 3-5 分钟
- 后续编译会更快

### 3. 应用启动
- 应用会自动安装到设备
- 自动启动应用

---

## 第六步：测试功能

### 1. 准备测试录音
在手机上准备一些通话录音文件，或者：
- 下载测试音频文件
- 放到手机的 `/Recordings` 或 `/CallRecordings` 目录

### 2. 扫描录音
- 打开应用
- 点击右上角的刷新按钮
- 或点击底部的 `+` 按钮
- 等待扫描完成

### 3. 转写录音
- 点击某个录音项
- 点击右上角的三点菜单
- 选择 `转写并生成纪要`
- 等待处理（约 1-2 分钟）

### 4. 查看纪要
- 切换到 `纪要` 标签页
- 查看生成的会谈纪要
- 包含标题、摘要、要点等

---

## 🎉 成功！

如果你看到了录音列表和生成的纪要，说明应用运行成功！

---

## ❓ 常见问题

### Q1: Gradle 同步失败
**A:** 
```bash
# 清理项目
./gradlew clean

# 或在 Android Studio 中
Build → Clean Project
Build → Rebuild Project
```

### Q2: 找不到设备
**A:** 
- 检查 USB 线是否连接
- 检查是否启用了 USB 调试
- 尝试重新插拔 USB 线
- 重启 ADB：`adb kill-server && adb start-server`

### Q3: 编译错误
**A:** 
- 检查 JDK 版本是否为 17+
- 检查 Android SDK 是否安装完整
- 尝试 `File` → `Invalidate Caches / Restart`

### Q4: 应用闪退
**A:** 
- 检查 API Key 是否配置正确
- 查看 Logcat 日志查找错误信息
- 确保手机系统版本 >= Android 7.0

### Q5: 扫描不到录音
**A:** 
- 检查是否授予了存储权限
- 确认录音文件确实存在
- 检查文件格式是否支持（mp3, m4a, wav, amr, 3gp）

### Q6: API 调用失败
**A:** 
- 检查网络连接
- 确认 API Key 是否有效
- 查看 Logcat 中的详细错误信息
- 确认 API 额度是否充足

---

## 📱 权限说明

应用需要以下权限：
- ✅ 存储权限 - 读取录音文件
- ✅ 网络权限 - 调用 API
- ✅ 联系人权限 - 显示联系人名称（可选）

首次运行时需要手动授予这些权限。

---

## 🔍 查看日志

### 打开 Logcat
1. Android Studio 底部点击 `Logcat`
2. 在过滤器中输入应用包名：`com.callrecord.manager`
3. 查看应用运行日志

### 常用日志级别
- `D` (Debug) - 调试信息
- `I` (Info) - 一般信息
- `W` (Warning) - 警告
- `E` (Error) - 错误

---

## 📚 下一步

### 学习建议
1. 阅读 `DEVELOPMENT_GUIDE.md` 了解详细开发指南
2. 阅读 `PROJECT_SUMMARY.md` 了解项目架构
3. 查看代码注释理解实现细节
4. 尝试修改 UI 或添加新功能

### 功能扩展
- 添加录音播放功能
- 实现纪要编辑
- 添加数据导出
- 优化 UI 界面

---

## 💡 提示

- 首次运行建议使用真机测试，模拟器可能无法访问录音文件
- 转写大文件可能需要较长时间，请耐心等待
- API 调用需要网络连接，确保网络畅通
- 建议使用 WiFi 网络，避免消耗过多流量

---

## 🎯 目标检查清单

- [ ] Android Studio 已安装
- [ ] JDK 17+ 已安装
- [ ] API Key 已配置
- [ ] 项目已导入
- [ ] Gradle 同步成功
- [ ] 设备已连接
- [ ] 应用已运行
- [ ] 录音已扫描
- [ ] 转写功能正常
- [ ] 纪要生成成功

---

**祝你开发顺利！** 🚀

如有问题，请查看详细文档或搜索错误信息。
