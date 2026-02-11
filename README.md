# 通话录音管理与纪要生成应用

## 项目简介
一个安卓应用，用于管理通话录音并自动生成会谈纪要。

## 核心功能
- 📱 通话录音管理（扫描、播放、删除）
- 🎙️ 语音转文字（使用阶跃星辰 ASR API）
- 📝 AI 智能纪要生成（摘要、结构化、说话人分离）
- 💾 本地存储管理

## 技术栈
- 语言：Kotlin
- UI：Jetpack Compose
- 架构：MVVM + Repository
- 数据库：Room (SQLite)
- 网络：Retrofit + OkHttp
- 异步：Coroutines + Flow

## 项目结构
```
app/
├── src/main/
│   ├── java/com/callrecord/manager/
│   │   ├── data/              # 数据层
│   │   │   ├── local/         # 本地数据库
│   │   │   ├── remote/        # API 服务
│   │   │   └── repository/    # 仓库层
│   │   ├── domain/            # 业务逻辑层
│   │   │   ├── model/         # 数据模型
│   │   │   └── usecase/       # 用例
│   │   ├── ui/                # UI 层
│   │   │   ├── screen/        # 页面
│   │   │   ├── component/     # 组件
│   │   │   └── theme/         # 主题
│   │   └── util/              # 工具类
│   └── res/                   # 资源文件
└── build.gradle.kts
```

## 开发环境要求
- Android Studio Hedgehog | 2023.1.1+
- JDK 17+
- Android SDK 24+ (目标 SDK 34)
- Kotlin 1.9+

## 快速开始
1. 使用 Android Studio 打开项目
2. 在 `local.properties` 中配置 API Key
3. 同步 Gradle 依赖
4. 运行到设备或模拟器

## API 配置

> ⚠️ **v1.1.0 起**：API Key 已改为在 App 设置页面中配置，无需在 `local.properties` 中填写。
> 打开 App → 点击右上角⚙️齿轮图标 → 填写你的 API Key

## 开发进度
- [x] 项目框架搭建
- [ ] 录音管理功能
- [ ] 语音转文字
- [ ] AI 纪要生成
- [ ] UI 优化

## License
MIT License
