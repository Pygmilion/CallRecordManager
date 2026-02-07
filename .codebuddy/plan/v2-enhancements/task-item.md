# 实施计划 — V2 功能增强

- [x] 1. 移除 RecordListScreen 底部冗余 Snackbar
   - 删除 `RecordListScreen.kt` 中 `errorMessage`/`successMessage` 驱动的 `Snackbar` 组件（约第148-176行的整个 Snackbar 块）
   - 移除对应的 `errorMessage` 和 `successMessage` 状态收集（`viewModel.errorMessage.collectAsState()` 等）
   - 评估 `MinuteListScreen.kt` 中是否有类似冗余提示，如有则同步清理
   - 保留 `MainViewModel` 中 `_errorMessage`/`_successMessage` 及 `clearError()`/`clearSuccess()` 方法（其他页面可能仍需使用），但移除转写/纪要流程中对它们的赋值（`_successMessage.value = "转写完成..."` 等），改为仅更新 `RecordProcessStage`
   - _需求：1.1、1.2、1.4_

- [x] 2. ASR 死代码清理与容错增强
   - 从 `ApiModels.kt` 中移除未使用的 `StepFunAsrRequest` 和 `AudioInput` 数据类
   - 从 `StepFunApiService.kt` 中移除未使用的 `transcribeAudio`（JSON Body）接口方法，仅保留 `transcribeAudioFile`（Multipart）
   - 在 `CallRecordRepository.kt` 的 `transcribeAudio()` 方法中增加网络重试机制（最多3次，间隔递增3s/6s/9s），复用纪要生成中已有的重试模式
   - 在转写前增加文件大小检查：超过25MB时返回提示错误
   - _需求：4.1.1、4.1.2、4.1.3、4.1.4_

- [x] 3. LLM 模型升级与提示词优化
   - 将 `CallRecordRepository.kt` 中 `generateMeetingMinute()` 方法的模型从 `step-2-16k` 改为 `step-3.5-flash`
   - 重写 System Prompt：增加角色设定、中文输出要求、JSON 格式严格约束、各字段字数和内容要求、待办优先级枚举约束
   - 重写 `buildMeetingMinutePrompt()` 方法：在转写内容前添加录音元数据上下文（联系人名称、通话时间、通话时长），需要将 `record` 参数传入
   - 优化 `parseMinuteResult()` 方法：在 JSON 解析失败前尝试修复常见格式问题（去除 Markdown 代码块包裹 ` ```json...``` `、修复尾部逗号等），而非直接降级为默认结构
   - 同步更新 `ApiModels.kt` 中 `StepFunLlmRequest` 的默认模型为 `step-3.5-flash`
   - _需求：4.2.5、4.2.6、4.2.7、4.2.8_

- [x] 4. 文件分享接收界面 — UI 层实现
   - 在 `ui/screen/` 下新建 `AudioReceiveScreen.kt`，实现专用的"接收音频"Compose 界面（BottomSheet 或全屏 Dialog 形式）
   - 界面包含：文件名/文件大小/时长展示区域、三档操作选择（仅存储 / 存储并转写 / 存储转写并生成纪要，默认选中第三档）、联系人输入区域（TextField + 通讯录选择按钮）、确认/取消按钮、错误信息展示区域
   - 从文件名中尝试自动提取联系人名称进行预填
   - _需求：2.1、2.2、2.3、2.4_

- [x] 5. 文件分享接收界面 — 逻辑层集成
   - 修改 `MainActivity.kt`：将 `checkSharedAudio()` 和 `handleSelectedAudio()` 中的直接处理逻辑改为触发接收界面展示
   - 在 `MainViewModel` 中新增：`pendingShareFile` 状态（包含 Uri、文件名、文件大小等信息）、`importWithOptions()` 方法（接收操作档位和联系人参数）
   - 确保 `onNewIntent` 场景同样展示接收界面而非直接处理
   - 在 `MainApp` Composable 中增加接收界面的条件渲染逻辑
   - 处理网络不可用场景：选择含转写档位时先完成存储，标记待转写状态
   - _需求：2.4.8、2.4.9、2.4.10、2.4.11、2.5.12、2.5.13_

- [x] 6. 数据库层扩展 — 纪要联系人关联查询
   - 在 `MeetingMinuteDao` 中新增关联查询方法：通过 `transcriptId → TranscriptEntity.recordId → CallRecordEntity.contactName` 链路获取纪要对应的联系人名称
   - 新增数据类 `MinuteWithContact`（包含 `MeetingMinuteEntity` + `contactName`），用于纪要列表页按联系人分组
   - 在 `CallRecordRepository` 中新增 `getAllMinutesWithContact()` 方法，返回 `Flow<List<MinuteWithContact>>`
   - _需求：3.1.4_

- [x] 7. 纪要列表按联系人归档显示
   - 重构 `MinuteListScreen.kt`：将平铺列表改为按联系人分组显示（复用 `RecordListScreen` 中已有的分组模式）
   - 每个联系人组标题显示联系人名称和纪要数量
   - 无联系人的纪要归入"未知联系人"分组
   - 在 `MainViewModel` 中将 `_minutes` 数据源切换为带联系人信息的 `MinuteWithContact`，并提供分组后的数据
   - _需求：3.1.1、3.1.2、3.1.3_

- [x] 8. 纪要多选模式与脉络简报 — UI 层
   - 在 `MinuteListScreen.kt` 中实现多选模式：长按进入多选、复选框显示、顶部工具栏显示已选数量和"生成脉络简报"按钮、选中不足2条时按钮禁用
   - 新建 `TimelineBriefScreen.kt`：脉络简报详情页，展示事件总标题、时间线概览、事件发展趋势分析、当前最新状态总结、后续建议/待办
   - 在简报详情页支持导出为 Markdown 或文本格式（复用现有纪要导出逻辑）
   - 在 `MainApp` 中增加脉络简报详情页的导航
   - _需求：3.2.5、3.2.6、3.2.7、3.2.8、3.3.11、3.3.12、3.3.13_

- [x] 9. 纪要多选模式与脉络简报 — 逻辑层
   - 在 `MainViewModel` 中新增：多选状态管理（`selectedMinuteIds`、`isMultiSelectMode`）、`generateTimelineBrief()` 方法
   - 在 `CallRecordRepository` 中新增 `generateTimelineBrief()` 方法：接收多条纪要，按时间排序，构建专用 System Prompt 和 User Prompt，调用 `step-3.5-flash` 生成脉络简报
   - 定义脉络简报的数据模型（`TimelineBriefResult`）和存储策略（可存为特殊类型 Entity 或内存展示）
   - 实现 LLM 脉络简报专用提示词：要求按时间顺序梳理事件发展、提取关键状态变化、分析趋势、总结最新状态、给出后续建议
   - _需求：3.3.9、3.3.10、4.3.9、4.3.10_

- [x] 10. 编译验证与安装测试
   - 执行 Gradle 编译（`./gradlew assembleDebug`）确保无编译错误
   - 安装到设备进行功能验证
   - 重点测试：Snackbar 移除后状态显示正确、分享接收流程完整、纪要分组显示正确、多选生成简报流程正常、LLM 模型切换后纪要生成质量
   - _需求：1-4 全部_
