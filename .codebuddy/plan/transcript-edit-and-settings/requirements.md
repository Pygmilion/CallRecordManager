# 需求文档

## 引言

本需求文档描述 CallRecordManager（语音快记）App 的两个新特性：

1. **转写文字编辑功能** — 用户可以在录音详情页对语音转写后的文字进行自由编辑，包括关键词搜索和批量替换功能，编辑后的文字可持久化存储，并支持基于修改后的文字重新生成会谈纪要。
2. **App 设置页及 API Key 管理** — 新增一个设置页面入口（位于 App 顶部），用户可以在设置中填写 StepFun API Key。**彻底移除**现有的 API Key 硬编码机制（包括 `BuildConfig.STEPFUN_API_KEY`、`local.properties` 配置、`build.gradle.kts` 中的 `buildConfigField`），API Key 完全由用户在 App 内手动填写，运行时动态取用。

### 现状分析

**转写文字方面：**
- 当前 `RecordDetailScreen` 以只读的聊天气泡形式展示转写的 `SpeakerSegment` 列表
- `TranscriptEntity` 中存储了 `fullText`（完整文本）和 `speakers`（说话人分段列表）
- 转写完成后用户无法修改转写文字，只能查看
- 纪要生成依赖 `TranscriptEntity` 中的文字内容

**API Key 方面（需要彻底清理的硬编码位置）：**
- `app/build.gradle.kts` 第30行：`buildConfigField("String", "STEPFUN_API_KEY", ...)` 从 `local.properties` 读取并注入 BuildConfig
- `MainActivity.kt` 第65行：`val apiKey = BuildConfig.STEPFUN_API_KEY` 读取硬编码 Key
- `MainActivity.kt` 第66行：`val apiService = ApiClient.createStepFunService(apiKey)` 用硬编码 Key 创建 API 服务
- `MainActivity.kt` 第87行：`MainViewModel(repository, apiKey, ...)` 将硬编码 Key 传入 ViewModel
- `MainViewModel.kt` 构造函数中接收 `apiKey: String` 参数，并在所有 API 调用中传递
- `CallRecordRepository.kt` 的 `transcribeRecord()`、`generateMeetingMinute()`、`regenerateMinutesForTranscribed()`、`generateTimelineBrief()` 等方法均通过参数传入 `apiKey`
- `StepFunApiService.kt` 的 `ApiClient.createStepFunService(apiKey)` 通过 OkHttp Interceptor 固定注入 Authorization Header
- 项目中尚未使用 `SharedPreferences` 或 `DataStore` 进行本地数据持久化

---

## 需求

### 需求 1：转写文字编辑功能

**用户故事：** 作为一名语音快记用户，我希望能够编辑语音转写后的文字内容，以便修正转写错误、补充遗漏信息，并基于修正后的文字重新生成更准确的会谈纪要。

#### 验收标准

##### 1.1 编辑入口

1. WHEN 用户在录音详情页（`RecordDetailScreen`）查看转写内容时 THEN 系统 SHALL 在转写区域上方显示一个"编辑转写"按钮（如铅笔图标按钮）
2. IF 当前录音没有转写记录 THEN 系统 SHALL 不显示"编辑转写"按钮
3. WHEN 用户点击"编辑转写"按钮 THEN 系统 SHALL 打开一个全屏或接近全屏的编辑窗口

##### 1.2 编辑窗口设计

4. WHEN 编辑窗口打开时 THEN 系统 SHALL 将所有转写段落合并为一段完整的文字，显示在一个可自由编辑的多行文本输入框中
5. WHEN 编辑窗口打开时 THEN 系统 SHALL 在窗口顶部显示标题栏，包含"返回"按钮、标题"编辑转写"和"保存"按钮
6. WHEN 用户在文本输入框中输入、删除或修改文字 THEN 系统 SHALL 实时更新文本内容，不做任何输入限制
7. WHEN 用户已修改文字但尚未保存时点击返回 THEN 系统 SHALL 弹出确认对话框，提示用户"是否放弃未保存的修改？"

##### 1.3 关键词搜索功能

8. WHEN 编辑窗口打开时 THEN 系统 SHALL 在标题栏下方或通过工具栏按钮提供一个搜索/替换面板的开关
9. WHEN 用户在搜索框中输入关键词 THEN 系统 SHALL 高亮显示文本中所有匹配的关键词
10. WHEN 搜索到匹配结果时 THEN 系统 SHALL 显示匹配数量（如"第 2/5 个"），并提供上一个/下一个导航按钮

##### 1.4 批量替换功能

11. WHEN 搜索面板展开时 THEN 系统 SHALL 同时显示一个"替换为"输入框
12. WHEN 用户填写了搜索词和替换词后点击"全部替换"按钮 THEN 系统 SHALL 将文本中所有匹配的关键词替换为目标文字
13. WHEN 替换操作完成后 THEN 系统 SHALL 显示替换结果提示（如"已替换 N 处"）

##### 1.5 保存编辑

14. WHEN 用户点击"保存"按钮 THEN 系统 SHALL 将修改后的完整文字更新到 `TranscriptEntity` 的 `fullText` 字段中
15. WHEN 保存成功后 THEN 系统 SHALL 同时更新 `TranscriptEntity` 的 `speakers` 列表，将其合并为单个 `SpeakerSegment`（speaker="Edited", text=修改后的全文）
16. WHEN 保存成功后 THEN 系统 SHALL 更新 `TranscriptEntity` 的 `updateTime` 字段为当前时间
17. WHEN 保存成功后 THEN 系统 SHALL 关闭编辑窗口并返回录音详情页，详情页应显示更新后的转写内容
18. WHEN 保存成功后 THEN 系统 SHALL 显示一个简短的成功提示（如 Snackbar "转写内容已更新"）

##### 1.6 基于编辑后文字重新生成纪要

19. WHEN 用户保存编辑后的转写文字后返回录音详情页 THEN 系统 SHALL 在录音详情页或录音列表页提供"重新生成纪要"的操作入口
20. WHEN 用户触发"重新生成纪要" THEN 系统 SHALL 使用更新后的 `TranscriptEntity`（含修改后的 fullText）重新调用 LLM 生成纪要
21. WHEN 重新生成纪要时 THEN 系统 SHALL 删除该转写记录关联的旧纪要，然后生成新纪要
22. IF 该录音已有纪要 THEN 系统 SHALL 在"重新生成纪要"操作前提示用户"将覆盖现有纪要，是否继续？"

---

### 需求 2：App 设置页及 API Key 管理

**用户故事：** 作为一名语音快记用户，我希望能够在 App 中自行配置 StepFun API Key，以便无需重新编译即可使用自己的 API Key 调用转写和纪要生成服务。

#### 验收标准

##### 2.1 设置入口

1. WHEN 用户在 App 的主页面（录音列表或纪要列表）时 THEN 系统 SHALL 在页面顶部 TopAppBar 区域显示一个设置图标按钮（如齿轮图标）
2. WHEN 用户点击设置图标 THEN 系统 SHALL 导航到设置页面

##### 2.2 设置页面布局

3. WHEN 设置页面打开时 THEN 系统 SHALL 显示一个包含标题栏（带返回按钮和"设置"标题）的页面
4. WHEN 设置页面打开时 THEN 系统 SHALL 显示"API Key 配置"区块，包含：
   - 一段说明文字，解释 API Key 的用途（如"用于连接阶跃星辰 StepFun API，提供语音转写和纪要生成服务"）
   - 一个文本输入框用于填写 API Key
   - 输入框支持密码模式切换（默认遮掩显示，可点击眼睛图标切换明文/密文）
   - 一个"保存"按钮

##### 2.3 API Key 持久化存储

5. WHEN 用户在 API Key 输入框中填写内容并点击"保存" THEN 系统 SHALL 将 API Key 持久化存储到本地（使用 SharedPreferences）
6. WHEN 设置页面打开时 THEN 系统 SHALL 自动加载并显示已保存的 API Key（如有）
7. WHEN 用户清空 API Key 输入框并保存 THEN 系统 SHALL 清除本地存储的 API Key

##### 2.4 API Key 运行时取用

8. WHEN App 启动时 THEN 系统 SHALL 从本地 SharedPreferences 中读取用户配置的 API Key
9. IF 本地存储中没有用户配置的 API Key THEN 系统 SHALL 将 API Key 视为空（不做任何回退，不使用 BuildConfig）
10. WHEN 用户在设置页面更新了 API Key 并保存 THEN 系统 SHALL 立即将新 Key 应用到后续的所有 API 调用中，无需重启 App
11. WHEN `MainViewModel` 或 `CallRecordRepository` 执行 API 调用时 THEN 系统 SHALL 从统一的 API Key 提供源（如 `ApiKeyProvider`）动态获取当前有效的 Key，而不再依赖构造函数中传入的固定值

##### 2.5 彻底清理硬编码 API Key

12. WHEN 本需求实施完成后 THEN 系统 SHALL 删除 `app/build.gradle.kts` 中第30行的 `buildConfigField("String", "STEPFUN_API_KEY", ...)` 配置
13. WHEN 本需求实施完成后 THEN 系统 SHALL 删除 `MainActivity.kt` 中 `val apiKey = BuildConfig.STEPFUN_API_KEY` 及其相关的 Key 传递代码
14. WHEN 本需求实施完成后 THEN 系统 SHALL 移除 `MainViewModel` 构造函数中的 `apiKey: String` 参数，改为通过 `ApiKeyProvider` 动态获取
15. WHEN 本需求实施完成后 THEN 系统 SHALL 移除 `CallRecordRepository` 各 API 调用方法（`transcribeRecord`、`generateMeetingMinute`、`regenerateMinutesForTranscribed`、`generateTimelineBrief`）中的 `apiKey` 参数，改为内部从 `ApiKeyProvider` 获取
16. WHEN 本需求实施完成后 THEN 系统 SHALL 修改 `ApiClient.createStepFunService()` 不再在创建时固定注入 API Key 到 Interceptor 中，而是每次请求时从 `ApiKeyProvider` 动态获取最新的 Key
17. WHEN 本需求实施完成后 THEN 系统 SHALL 确保项目中不再有任何对 `BuildConfig.STEPFUN_API_KEY` 的引用

##### 2.6 API Key 状态提示

18. IF 用户未配置 API Key THEN 系统 SHALL 在用户尝试转写或生成纪要时弹出提示，引导用户前往设置页配置 API Key
19. WHEN 用户保存 API Key 成功后 THEN 系统 SHALL 显示保存成功的提示信息（如 Snackbar "API Key 已保存"）
20. WHEN 设置页面显示时 THEN 系统 SHALL 展示 API Key 的配置状态（如"已配置"或"未配置"）

---

## 技术注意事项

### 转写编辑
- 编辑窗口需要处理大段文字的性能问题，建议使用 `TextField` 配合 `BasicTextField` + `VisualTransformation` 实现搜索高亮
- `TranscriptDao` 需要新增一个 `updateTranscript()` 方法（如果尚无），用于更新转写记录
- 重新生成纪要的逻辑可复用现有的 `retryGenerateMinute()` 流程

### API Key 管理
- 创建统一的 `ApiKeyProvider` 类，封装 SharedPreferences 的读写逻辑
- `ApiKeyProvider` 应为单例，在 App 级别初始化，供 ViewModel 和 Repository 共用
- `MainViewModel` 不再在构造函数中接收 `apiKey`，而是持有 `ApiKeyProvider` 引用
- `CallRecordRepository` 不再在各方法中接收 `apiKey` 参数，而是在构造时注入 `ApiKeyProvider`
- `ApiClient.createStepFunService()` 不再接收 `apiKey` 参数，OkHttp Interceptor 改为每次请求时从 `ApiKeyProvider` 读取最新 Key
- 彻底移除 `build.gradle.kts` 中的 `buildConfigField`、`local.properties` 中的 `STEPFUN_API_KEY` 配置以及 `MainActivity` 中 `BuildConfig.STEPFUN_API_KEY` 的读取逻辑

### 兼容性
- 编辑后的转写内容应当仍然能与现有的纪要生成 Prompt 兼容
- API Key 切换后不需要重建 Retrofit 实例，Interceptor 动态读取即可
