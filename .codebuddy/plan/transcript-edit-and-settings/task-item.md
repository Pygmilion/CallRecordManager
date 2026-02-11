# 实施计划

- [ ] 1. 创建 `ApiKeyProvider` 单例类，封装 API Key 的持久化存储与动态读取
   - 在 `data/repository/` 下新建 `ApiKeyProvider.kt`
   - 使用 `SharedPreferences` 实现 API Key 的读取（`getApiKey()`）、保存（`saveApiKey()`）和清除（`clearApiKey()`）
   - 提供 `hasApiKey(): Boolean` 方法用于检查是否已配置
   - 接收 `Context` 参数，设计为可在 `MainActivity` 中初始化并在全局使用的单例
   - _需求：2.3、2.4_

- [ ] 2. 改造 `StepFunApiService.kt` 中的 `ApiClient`，使 OkHttp Interceptor 动态获取 API Key
   - 修改 `ApiClient.createStepFunService()` 方法签名，移除 `apiKey: String` 参数
   - 改为接收 `ApiKeyProvider` 实例（或函数式参数 `() -> String`）
   - OkHttp Interceptor 在每次请求时从 `ApiKeyProvider` 动态读取最新 Key，而非在创建时固定注入
   - _需求：2.4（第11条）、2.5（第16条）_

- [ ] 3. 改造 `CallRecordRepository`，移除所有方法中的 `apiKey` 参数
   - 在 `CallRecordRepository` 构造函数中注入 `ApiKeyProvider`
   - 移除 `transcribeRecord()`、`generateMeetingMinute()`、`regenerateMinutesForTranscribed()`、`generateTimelineBrief()` 等方法的 `apiKey` 参数
   - 改为在方法内部通过 `ApiKeyProvider.getApiKey()` 获取 Key
   - 在 API 调用前检查 Key 是否为空，若为空则抛出明确异常或返回错误状态
   - _需求：2.4（第11条）、2.5（第15条）、2.6（第18条）_

- [ ] 4. 改造 `MainViewModel`，移除 `apiKey` 构造参数并更新所有 API 调用点
   - 移除 `MainViewModel` 构造函数中的 `apiKey: String` 参数
   - 注入 `ApiKeyProvider`，在需要 API Key 的地方通过 provider 动态获取
   - 更新所有调用 `repository.transcribeRecord()`、`repository.generateMeetingMinute()` 等方法的地方，移除 `apiKey` 实参
   - 新增"API Key 未配置"的错误处理逻辑，当 Key 为空时引导用户前往设置页
   - _需求：2.4（第11条）、2.5（第14条）、2.6（第18条）_

- [ ] 5. 清理 `MainActivity.kt` 和 `build.gradle.kts` 中的硬编码 API Key
   - 删除 `MainActivity.kt` 中 `val apiKey = BuildConfig.STEPFUN_API_KEY` 及相关传递逻辑
   - 在 `MainActivity` 中初始化 `ApiKeyProvider` 单例，并传入 ViewModel 和 Repository
   - 修改 `ApiClient.createStepFunService()` 的调用方式，传入 `ApiKeyProvider` 而非 apiKey 字符串
   - 删除 `app/build.gradle.kts` 中的 `buildConfigField("String", "STEPFUN_API_KEY", ...)` 配置
   - 确认项目中不再有任何 `BuildConfig.STEPFUN_API_KEY` 的引用
   - _需求：2.5（第12、13、17条）_

- [ ] 6. 创建设置页面 `SettingsScreen.kt`，实现 API Key 配置 UI
   - 在 `ui/screen/` 下新建 `SettingsScreen.kt`
   - 实现包含 TopAppBar（返回按钮 + "设置"标题）的页面布局
   - 实现 "API Key 配置" 区块：说明文字 + 密码模式输入框（支持眼睛图标切换明文/密文）+ 保存按钮
   - 页面打开时从 `ApiKeyProvider` 加载已保存的 API Key
   - 保存时调用 `ApiKeyProvider.saveApiKey()`，清空则调用 `clearApiKey()`
   - 显示 API Key 配置状态（"已配置"/"未配置"）和保存成功的 Snackbar 提示
   - _需求：2.2（第3、4条）、2.3（第5、6、7条）、2.6（第19、20条）_

- [ ] 7. 在主页面 TopAppBar 添加设置入口，并实现导航
   - 在 `RecordListScreen` 和 `MinuteListScreen`（或共用的 TopAppBar 区域）添加齿轮图标按钮
   - 点击后导航到 `SettingsScreen`
   - 在 `MainActivity` 中注册设置页面的路由/状态管理
   - _需求：2.1（第1、2条）_

- [ ] 8. 创建转写编辑窗口 `TranscriptEditScreen.kt`，实现文字编辑与搜索替换功能
   - 在 `ui/screen/` 下新建 `TranscriptEditScreen.kt`
   - 实现全屏编辑窗口：TopAppBar（返回按钮 + "编辑转写"标题 + 保存按钮）
   - 将转写的 `speakers` 段落合并为完整文字，加载到可编辑的多行 `TextField` 中
   - 实现搜索/替换面板（通过工具栏按钮展开/收起）：搜索框 + 替换框 + 匹配计数显示 + 上一个/下一个导航 + 全部替换按钮
   - 搜索时高亮显示所有匹配关键词（使用 `VisualTransformation` 或 `AnnotatedString`）
   - 全部替换后显示"已替换 N 处"提示
   - 未保存时点击返回弹出确认对话框
   - _需求：1.2（第4、5、6、7条）、1.3（第8、9、10条）、1.4（第11、12、13条）_

- [ ] 9. 实现转写编辑的保存逻辑与数据库更新
   - 在 `Daos.kt` 的 `TranscriptDao` 中新增 `updateTranscriptContent()` 方法，用于更新 `fullText`、`speakers` 和 `updateTime`
   - 在 `CallRecordRepository` 中新增 `updateTranscriptText()` 方法，封装保存编辑后文字的逻辑（更新 `fullText`，将 `speakers` 合并为单个 `SpeakerSegment(speaker="Edited", text=全文)`，更新 `updateTime`）
   - 在 `MainViewModel` 中新增 `saveEditedTranscript()` 方法，供 UI 调用
   - 保存成功后返回详情页并显示 Snackbar "转写内容已更新"
   - _需求：1.5（第14、15、16、17、18条）_

- [ ] 10. 实现编辑入口与"重新生成纪要"功能
   - 在 `RecordDetailScreen` 的转写内容区域上方添加"编辑转写"铅笔图标按钮（仅当存在转写记录时显示）
   - 点击后打开 `TranscriptEditScreen`，传入当前转写内容
   - 在 `RecordDetailScreen` 中添加"重新生成纪要"操作按钮
   - 若已有纪要，点击时弹出确认对话框"将覆盖现有纪要，是否继续？"
   - 确认后调用 `MainViewModel` 中的重新生成纪要方法（复用现有 `retryGenerateMinute()` 逻辑，使用更新后的 `fullText`）
   - _需求：1.1（第1、2、3条）、1.6（第19、20、21、22条）_
