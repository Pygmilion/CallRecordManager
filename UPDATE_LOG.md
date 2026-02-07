# 通话录音管理应用 - 更新说明

## 本次更新内容

### 1. 修复了文件导入功能
- ✅ 添加了手动选择音频文件的功能
- ✅ 文件选择后会自动导入到应用数据库
- ✅ 支持所有常见音频格式（mp3, m4a, wav, amr, 3gp等）

### 2. 添加了详细的日志系统
创建了新的日志工具类 `AppLogger.kt`，包含以下功能：
- 🔍 DEBUG级别：调试信息
- ℹ️ INFO级别：一般信息
- ⚠️ WARN级别：警告信息
- ❌ ERROR级别：错误信息

### 3. 在关键流程添加了日志打点

#### 文件导入流程：
- 开始导入文件
- 检查文件是否存在
- 记录文件大小
- 创建录音记录
- 保存到数据库
- 导入成功/失败

#### 语音转写流程：
- 开始转写录音
- 检查文件是否存在
- 记录文件大小
- 创建转写记录
- 准备文件上传
- 调用阶跃星辰ASR API
- 记录API响应码
- 转写成功（记录文本长度、说话人分段数）
- 更新数据库
- 转写失败（记录错误详情）

#### 生成纪要流程：
- 开始生成会谈纪要
- 记录转写文本长度
- 构建Prompt
- 调用阶跃星辰LLM API
- 记录API响应码
- 解析JSON响应
- 保存纪要到数据库
- 生成成功/失败

### 4. 界面改进
- ✅ 顶部工具栏添加了"查看日志"按钮（ℹ️图标）
- ✅ 点击可查看所有运行日志（最多显示50条）
- ✅ 错误日志会自动显示为错误提示
- ✅ 日志包含emoji图标，便于快速识别日志级别

## 使用方法

### 编译安装：
1. 使用 Android Studio 打开项目
2. 点击 Build → Build Bundle(s) / APK(s) → Build APK(s)
3. 等待编译完成
4. 使用 adb 安装：
   ```bash
   ~/Downloads/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### 查看日志：
1. 打开应用
2. 点击顶部的 ℹ️ 图标
3. 查看详细的运行日志
4. 如果转写失败，日志会显示具体的错误原因

### 调试转写问题：
如果转写功能不工作，请按以下步骤排查：

1. **检查API Key**：
   - 确保 `local.properties` 文件中配置了正确的 `STEPFUN_API_KEY`
   
2. **查看日志**：
   - 点击顶部的 ℹ️ 图标查看日志
   - 查找 "转写" 相关的日志
   - 查看是否有错误信息
   
3. **常见错误**：
   - `文件不存在`：文件路径有问题
   - `API错误 401`：API Key无效或过期
   - `API错误 400`：请求参数错误
   - `API错误 500`：服务器错误
   
4. **网络检查**：
   - 确保手机有网络连接
   - 确保可以访问阶跃星辰API服务器

## 文件清单

### 新增文件：
- `app/src/main/java/com/callrecord/manager/utils/AppLogger.kt` - 日志工具类

### 修改文件：
- `app/src/main/java/com/callrecord/manager/data/repository/CallRecordRepository.kt` - 添加日志
- `app/src/main/java/com/callrecord/manager/ui/screen/MainViewModel.kt` - 添加日志监听
- `app/src/main/java/com/callrecord/manager/ui/screen/RecordListScreen.kt` - 添加日志查看界面
- `app/src/main/java/com/callrecord/manager/MainActivity.kt` - 文件选择器

## 技术细节

### 日志系统架构：
```
AppLogger (单例)
    ↓
LogListener 接口
    ↓
MainViewModel (实现 LogListener)
    ↓
UI (显示日志)
```

### 日志流程：
1. Repository 调用 `AppLogger.i/d/w/e()` 记录日志
2. AppLogger 通知所有 LogListener
3. MainViewModel 接收日志并添加到 logMessages
4. UI 监听 logMessages 并显示在对话框中

## 下一步优化建议

1. 添加日志导出功能（保存到文件）
2. 添加日志过滤功能（按级别筛选）
3. 添加实时日志显示（不需要打开对话框）
4. 添加性能监控（记录API调用耗时）
5. 添加崩溃日志收集

## 联系方式

如有问题，请查看日志并提供详细的错误信息。
