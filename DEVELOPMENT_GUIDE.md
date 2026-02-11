# 通话录音管理应用 - 开发指南

## 📱 项目概述

这是一个完整的安卓应用，用于管理通话录音并自动生成会谈纪要。

### 核心功能
1. **录音管理**：扫描、展示、播放、删除通话录音
2. **语音转文字**：使用阶跃星辰 ASR API 进行语音识别
3. **说话人分离**：自动识别对话中的不同说话人
4. **AI 纪要生成**：使用 LLM 生成结构化会谈纪要
5. **本地存储**：所有数据存储在本地数据库

---

## 🛠️ 开发环境配置

### 1. 安装 Android Studio
下载地址：https://developer.android.com/studio

推荐版本：Android Studio Hedgehog (2023.1.1) 或更高

### 2. 安装 JDK
需要 JDK 17 或更高版本

检查 JDK 版本：
```bash
java -version
```

### 3. 配置 Android SDK
在 Android Studio 中：
- Tools → SDK Manager
- 安装 Android SDK Platform 34
- 安装 Android SDK Build-Tools 34.0.0

---

## 🚀 项目导入与运行

### 1. 打开项目
1. 启动 Android Studio
2. File → Open
3. 选择 `CallRecordManager` 文件夹
4. 等待 Gradle 同步完成

### 2. 配置 API Key

> ⚠️ **v1.1.0 起**：API Key 已改为在 App 设置页面中配置，无需在文件中填写。
> 打开 App → 点击右上角⚙️齿轮图标 → 填写你的 API Key

获取 API Key：
- 访问：https://platform.stepfun.com/
- 注册账号并创建 API Key

### 3. 同步 Gradle
点击 Android Studio 顶部的 "Sync Project with Gradle Files" 按钮

### 4. 连接设备
**方式 A：使用真机**
1. 在手机上启用开发者选项
2. 启用 USB 调试
3. 用 USB 线连接电脑
4. 在 Android Studio 中选择设备

**方式 B：使用模拟器**
1. Tools → Device Manager
2. Create Device
3. 选择 Pixel 6 或其他设备
4. 选择 API 34 系统镜像
5. 启动模拟器

### 5. 运行应用
点击绿色的 Run 按钮（或按 Shift+F10）

---

## 📂 项目结构详解

```
CallRecordManager/
├── app/
│   ├── src/main/
│   │   ├── java/com/callrecord/manager/
│   │   │   ├── data/                    # 数据层
│   │   │   │   ├── local/              # 本地数据库
│   │   │   │   │   ├── Entities.kt    # 数据实体
│   │   │   │   │   ├── Daos.kt        # 数据访问对象
│   │   │   │   │   └── AppDatabase.kt # 数据库配置
│   │   │   │   ├── remote/             # 网络 API
│   │   │   │   │   ├── ApiModels.kt   # API 数据模型
│   │   │   │   │   └── StepFunApiService.kt  # API 服务
│   │   │   │   └── repository/         # 仓库层
│   │   │   │       └── CallRecordRepository.kt
│   │   │   ├── ui/                     # UI 层
│   │   │   │   ├── screen/            # 页面
│   │   │   │   │   ├── MainViewModel.kt      # 主 ViewModel
│   │   │   │   │   ├── RecordListScreen.kt   # 录音列表
│   │   │   │   │   └── MinuteListScreen.kt   # 纪要列表
│   │   │   │   └── theme/             # 主题
│   │   │   │       └── Theme.kt
│   │   │   └── MainActivity.kt        # 主活动
│   │   ├── res/                       # 资源文件
│   │   │   ├── values/               # 值资源
│   │   │   │   ├── strings.xml      # 字符串
│   │   │   │   ├── colors.xml       # 颜色
│   │   │   │   └── themes.xml       # 主题
│   │   │   └── xml/                 # XML 配置
│   │   └── AndroidManifest.xml      # 应用清单
│   └── build.gradle.kts             # 应用构建配置
├── build.gradle.kts                 # 项目构建配置
├── settings.gradle.kts              # 项目设置
├── local.properties                 # 本地配置（API Key）
└── README.md                        # 项目说明
```

---

## 🔑 核心代码说明

### 1. 数据模型（Entities.kt）
定义了三个核心实体：
- `CallRecordEntity`：录音记录
- `TranscriptEntity`：转写记录
- `MeetingMinuteEntity`：会谈纪要

### 2. 数据访问（Daos.kt）
使用 Room 框架提供数据库操作：
- `CallRecordDao`：录音 CRUD 操作
- `TranscriptDao`：转写 CRUD 操作
- `MeetingMinuteDao`：纪要 CRUD 操作

### 3. API 服务（StepFunApiService.kt）
集成阶跃星辰 API：
- `transcribeAudioFile()`：语音转文字
- `chatCompletion()`：LLM 对话

### 4. 仓库层（CallRecordRepository.kt）
业务逻辑实现：
- `scanSystemRecordings()`：扫描系统录音文件
- `transcribeRecord()`：转写录音
- `generateMeetingMinute()`：生成纪要

### 5. ViewModel（MainViewModel.kt）
管理 UI 状态和业务逻辑：
- 录音列表状态
- 纪要列表状态
- 加载状态
- 错误处理

### 6. UI 界面
- `RecordListScreen.kt`：录音列表页面
- `MinuteListScreen.kt`：纪要列表页面
- 使用 Jetpack Compose 构建现代化 UI

---

## 🔧 常见问题解决

### 1. Gradle 同步失败
**问题**：依赖下载失败
**解决**：
```bash
# 清理项目
./gradlew clean

# 重新构建
./gradlew build
```

### 2. API Key 未配置
**问题**：转写或纪要功能报错
**解决**：在 App 设置页面中配置 API Key（点击右上角⚙️图标）

### 3. 权限被拒绝
**问题**：无法读取录音文件
**解决**：
- 在应用设置中手动授予存储权限
- 在代码中添加运行时权限请求

### 4. 找不到录音文件
**问题**：扫描不到录音
**解决**：
- 检查录音文件路径是否正确
- 在 `CallRecordRepository.kt` 中添加更多录音目录

### 5. API 调用失败
**问题**：网络请求超时或失败
**解决**：
- 检查网络连接
- 确认 API Key 是否有效
- 查看 Logcat 中的详细错误信息

---

## 📝 下一步开发计划

### Phase 1：完善基础功能
- [ ] 添加运行时权限请求
- [ ] 实现录音播放功能
- [ ] 添加录音详情页面
- [ ] 添加纪要详情页面

### Phase 2：优化用户体验
- [ ] 添加加载进度显示
- [ ] 优化错误提示
- [ ] 添加空状态插图
- [ ] 实现下拉刷新

### Phase 3：高级功能
- [ ] 支持批量转写
- [ ] 添加纪要导出功能（PDF/Word）
- [ ] 实现纪要编辑功能
- [ ] 添加数据统计页面

### Phase 4：性能优化
- [ ] 实现分页加载
- [ ] 优化大文件上传
- [ ] 添加缓存机制
- [ ] 减少内存占用

---

## 🎓 学习资源

### Kotlin 学习
- 官方文档：https://kotlinlang.org/docs/home.html
- Kotlin 中文网：https://www.kotlincn.net/

### Android 开发
- 官方指南：https://developer.android.com/guide
- Jetpack Compose：https://developer.android.com/jetpack/compose

### Room 数据库
- 官方文档：https://developer.android.com/training/data-storage/room

### Retrofit 网络库
- 官方文档：https://square.github.io/retrofit/

---

## 💡 开发技巧

### 1. 查看日志
在 Android Studio 底部打开 Logcat，筛选应用日志

### 2. 调试技巧
- 在代码中设置断点
- 点击 Debug 按钮运行
- 使用 Step Over/Into 逐步调试

### 3. 性能分析
- Tools → Profiler
- 查看 CPU、内存、网络使用情况

### 4. 布局检查
- Tools → Layout Inspector
- 实时查看 UI 层级结构

---

## 📞 技术支持

如遇到问题，可以：
1. 查看 Android Studio 的 Logcat 日志
2. 搜索错误信息
3. 查阅官方文档
4. 在开发者社区提问

---

## 📄 许可证

MIT License - 可自由使用和修改
