# 实施计划

- [ ] 1. 定义录音处理流水线状态枚举
   - 在 `Entities.kt` 中新增 `RecordProcessStage` 枚举，包含以下值：`IDLE`（待处理）、`TRANSCRIBING`（转写中）、`TRANSCRIBE_DONE`（转写完成）、`GENERATING_MINUTE`（纪要生成中）、`COMPLETED`（全部完成）、`TRANSCRIBE_FAILED`（转写失败）、`MINUTE_FAILED`（纪要生成失败）
   - 该枚举为内存中的 UI 状态，不需要持久化到数据库
   - _需求：1.1、1.2、1.3、1.4、1.5_

- [ ] 2. 扩展 ViewModel 支持每条录音独立的流水线状态
   - 在 `MainViewModel.kt` 中将 `_processingRecordId`（单个 Long?）和 `_processingMessage`（单个 String?）替换为 `_recordProcessStages: MutableStateFlow<Map<Long, RecordProcessStage>>`，实现多条录音独立状态追踪
   - 新增辅助方法 `updateRecordStage(recordId: Long, stage: RecordProcessStage)` 更新指定录音的阶段状态
   - 修改 `transcribeRecord()` 方法，在转写开始时设置 `TRANSCRIBING`，成功后设置 `TRANSCRIBE_DONE`，失败时设置 `TRANSCRIBE_FAILED`
   - 修改 `generateMinute()` 方法，在纪要生成开始时设置 `GENERATING_MINUTE`，成功后设置 `COMPLETED`，失败时设置 `MINUTE_FAILED`
   - _需求：1.2、1.3、1.4、1.5、1.7_

- [ ] 3. 实现应用启动时的状态还原逻辑
   - 在 `MainViewModel.kt` 的 `loadRecords()` 中，根据每条 `CallRecordEntity` 的 `isTranscribed` 字段、关联的 `TranscriptEntity.status` 以及是否存在关联的 `MeetingMinuteEntity`，推导并填充 `_recordProcessStages` 的初始值
   - 未转写 → `IDLE`；已转写但无纪要 → `TRANSCRIBE_DONE`；已转写且有纪要 → `COMPLETED`
   - _需求：1.6_

- [ ] 4. 新增 `RecordProcessBadge` Composable 组件
   - 在 `RecordListScreen.kt` 中创建 `RecordProcessBadge(stage: RecordProcessStage)` 组件，根据不同阶段显示对应的状态标签：
     - `IDLE`：灰色文字"待处理"
     - `TRANSCRIBING`：蓝色/主题色 + 旋转 `CircularProgressIndicator` + "转写中..."
     - `TRANSCRIBE_DONE`：蓝色文字"转写完成"
     - `GENERATING_MINUTE`：蓝色/主题色 + 旋转指示器 + "纪要生成中..."
     - `COMPLETED`：绿色文字"✅ 已完成"
     - `TRANSCRIBE_FAILED`：红色文字"❌ 转写失败"
     - `MINUTE_FAILED`：红色文字"❌ 纪要生成失败"
   - _需求：1.1、1.2、1.3、1.4、1.5_

- [ ] 5. 将 `RecordProcessBadge` 集成到 `RecordItem` 卡片中
   - 修改 `RecordItem` Composable 的参数签名：移除 `isProcessing` 和 `processingMessage` 参数，新增 `processStage: RecordProcessStage` 参数
   - 在卡片底部行中，用 `RecordProcessBadge(stage)` 替换原有的"已转写" `AssistChip` 和旧的处理进度 `Row`
   - 状态标签放在通话时长右侧（底部行的右侧区域），保持紧凑的单行布局
   - _需求：1.1、1.4、1.7_

- [ ] 6. 更新 `RecordListScreen` 中传递给 `RecordItem` 的参数
   - 在 `RecordListScreen` 中 collect `viewModel.recordProcessStages` 状态
   - 在 `items()` 迭代中，从 `recordProcessStages` map 中获取每条录音对应的 `RecordProcessStage`（默认值为 `IDLE`），传递给 `RecordItem` 的 `processStage` 参数
   - _需求：1.7_

- [ ] 7. 移除 FAB 中的刷新按钮，仅保留添加文件按钮
   - 在 `RecordListScreen` 的 `floatingActionButton` 区域，移除包裹两个 FAB 的 `Column`，仅保留单个 `FloatingActionButton`（加号/添加文件），`onClick` 保持调用 `onPickAudioFile`
   - _需求：2.1、2.5_

- [ ] 8. 为录音列表底部增加防遮挡 padding
   - 修改 `LazyColumn` 的 `contentPadding` 参数，将底部 padding 从 16.dp 增加至 96.dp，确保列表最后几项的操作按钮不被 FAB 遮挡
   - _需求：2.2、2.3_

- [ ] 9. 编译验证并安装测试
   - 使用 `./gradlew assembleDebug` 编译项目，确保无编译错误
   - 使用 adb 安装 APK 到测试设备，验证：各状态标签正确显示、FAB 不再干涉列表底部操作、应用重启后状态正确还原
   - _需求：1.1–1.7、2.1–2.5_
