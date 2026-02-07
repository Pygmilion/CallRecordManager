# 需求文档 — V2 功能增强

## 引言

本需求文档覆盖 CallRecordManager 应用的 V2 迭代增强，包含以下四个方面的改进：
1. 移除底部冗余状态提示栏（已有每条录音独立的流水线状态标签后，Snackbar 冗余）
2. 系统文件分享接收流程重构（新增专用接收界面，支持操作档位选择和联系人归档）
3. 纪要按联系人归档显示 + 多纪要合并生成事件发展脉络简报
4. LLM/ASR 模型选择与提示词优化

### 当前状态分析

- **底部 Snackbar**：`RecordListScreen` 中存在 `errorMessage`/`successMessage` 驱动的 Snackbar，在引入 `RecordProcessBadge` 后与之重复
- **分享流程**：当前通过 `Intent.ACTION_SEND` 接收音频后直接调用 `handleSelectedAudio` 复制文件 → `importAudioFile` 导入数据库，没有中间界面，用户无法选择处理档位或关联联系人；日志显示分享后自动触发转写时因网络问题（`SocketException`）导致失败
- **纪要展示**：`MinuteListScreen` 按时间倒序平铺显示，没有联系人维度的分组归档
- **模型现状**：
  - ASR 实际使用 `step-asr`（Multipart 上传方式），不支持说话人分离，speakers 被硬编码为单个 Speaker 1
  - `ApiModels.kt` 中 `StepFunAsrRequest` 数据类默认值写了 `step-1-flash`，但该数据类及对应的 JSON Body 接口从未被实际调用，存在代码不一致
  - LLM 使用 `step-2-16k`，prompt 结构简单，缺乏上下文引导和输出格式约束

### 技术决策

#### 不采用端到端语音模型

经评估，端到端语音模型（直接将音频输入 LLM 跳过独立 ASR）**不适用于本项目**，理由如下：
1. **独立转写文本不可或缺**：`TranscriptEntity` 存储的完整转写文本是用户可审阅、可搜索的核心数据资产，端到端模型跳过该环节
2. **操作档位依赖分阶段处理**：需求2要求用户可选"仅存储+转写"（不生成纪要），端到端模型将转写和纪要生成耦合，无法满足该需求
3. **说话人分离信息丢失**：ASR 提供的 segment 数据（时间戳、说话人标识）对后续功能有价值
4. **脉络简报依赖转写文本**：需求3的多纪要合并功能需要独立的转写文本作为输入
5. **音频大小限制**：端到端模型对音频输入有更严格的大小/时长限制

因此保持 **两阶段流水线架构**：独立 ASR → 独立 LLM。

#### ASR 模型维持现状

经确认，`step-1-flash` 模型的可用性及其对标准 `/v1/audio/transcriptions` 端点的兼容性无法保证。因此 **ASR 模型维持 `step-asr` 不变**，仍使用 Multipart 上传方式。但需修正代码中的不一致：
- `ApiModels.kt` 中 `StepFunAsrRequest` 数据类及 `StepFunApiService` 中的 JSON Body 接口（`transcribeAudio` 方法）作为未使用的死代码，应予以清理或标注
- 实际 ASR 调用继续使用 `transcribeAudioFile`（Multipart）+ `step-asr` 模型

---

## 需求

### 需求 1 — 移除底部冗余状态提示栏

**用户故事：** 作为一名用户，我希望录音列表页面不再显示底部 Snackbar 提示栏，以便界面更简洁，不与每条录音的独立状态标签产生信息冗余。

#### 验收标准

1. WHEN 录音列表页面加载 THEN 系统 SHALL 不再显示底部 Snackbar（包括错误提示和成功提示）
2. WHEN 转写/纪要生成等操作进行中或完成/失败 THEN 系统 SHALL 仅通过每条录音下方的 `RecordProcessBadge` 展示状态，不额外弹出底部提示
3. IF 存在全局性错误（如网络不可用、权限缺失等非单条录音维度的错误） THEN 系统 SHALL 通过其他合理方式（如顶部 Banner 或 Toast）提示用户
4. WHEN 纪要列表页面(`MinuteListScreen`)中也有类似冗余提示 THEN 系统 SHALL 同步评估并清理

---

### 需求 2 — 系统文件分享接收流程重构

**用户故事：** 作为一名用户，我希望从系统文件管理器分享音频文件到本应用后，能看到一个专用的接收界面，在该界面可以选择处理档位和关联联系人，以便灵活控制文件的后续处理流程。

#### 验收标准

##### 2.1 接收界面基本功能
1. WHEN 用户从外部应用通过 `ACTION_SEND` 或 `ACTION_VIEW` 分享/打开音频文件 THEN 系统 SHALL 显示专用的"接收音频"界面（而非直接静默导入）
2. WHEN 接收界面展示 THEN 系统 SHALL 显示文件名、文件大小、音频时长等基本信息

##### 2.2 操作档位选择
3. WHEN 接收界面展示 THEN 系统 SHALL 提供三个操作档位供用户选择：
   - **仅存储**：仅将文件复制到应用目录并创建数据库记录
   - **存储并转写**：存储后自动启动语音转写
   - **存储、转写并生成纪要**：存储 → 转写 → 自动生成会谈纪要（完整流水线）
4. IF 用户未选择档位 THEN 系统 SHALL 默认选中"存储、转写并生成纪要"（全流程）

##### 2.3 联系人关联
5. WHEN 接收界面展示 THEN 系统 SHALL 提供联系人输入区域，支持以下两种方式：
   - 手动输入联系人名称
   - 从系统通讯录选择联系人（如有通讯录权限）
6. IF 文件名中包含可识别的联系人名称（按现有 `createRecordFromFile` 的解析规则） THEN 系统 SHALL 自动预填联系人字段
7. IF 用户未填写联系人 THEN 系统 SHALL 允许留空，以"未知联系人"归档

##### 2.4 容错与状态管理
8. WHEN 用户点击确认按钮 THEN 系统 SHALL 关闭接收界面并按选择的档位执行后续操作，同时在录音列表中更新对应的 `RecordProcessBadge`
9. WHEN 用户点击取消按钮 THEN 系统 SHALL 取消操作，不导入文件
10. IF 接收过程中发生异常（如文件复制失败） THEN 系统 SHALL 在接收界面上直接展示错误信息
11. WHEN 分享操作触发时应用已在前台（`onNewIntent`） THEN 系统 SHALL 同样展示接收界面，而非直接处理

##### 2.5 网络中断容错
12. WHEN 用户选择包含转写的档位且网络不可用 THEN 系统 SHALL 先完成存储，并在录音列表中标记待转写状态，待用户手动触发转写
13. IF 转写过程中发生 `SocketException` 或 `UnknownHostException` THEN 系统 SHALL 对 ASR 调用增加重试机制（最多 3 次，间隔递增）

---

### 需求 3 — 纪要按联系人归档 + 事件发展脉络简报

**用户故事：** 作为一名用户，我希望在纪要页面能按联系人维度归档查看所有会谈纪要，并能多选纪要合并生成一份事件发展脉络简报，以便纵览同一联系人在不同时间的讨论进展。

#### 验收标准

##### 3.1 纪要按联系人归档显示
1. WHEN 纪要列表页面加载 THEN 系统 SHALL 按联系人对纪要进行分组显示（与录音列表的分组方式一致）
2. WHEN 分组显示时 THEN 系统 SHALL 在每个联系人组标题旁显示该联系人的纪要数量
3. IF 纪要无法关联到联系人（如联系人字段为空） THEN 系统 SHALL 将其归入"未知联系人"分组
4. WHEN 纪要数据中缺少直接的 `contactName` 字段 THEN 系统 SHALL 通过 `transcriptId → recordId → CallRecordEntity.contactName` 的关联链路获取联系人信息

##### 3.2 多选模式
5. WHEN 用户长按任意纪要卡片 THEN 系统 SHALL 进入多选模式
6. WHEN 多选模式激活 THEN 系统 SHALL 在每个纪要卡片前显示复选框，并在顶部工具栏显示已选数量和"生成脉络简报"按钮
7. WHEN 用户点击复选框 THEN 系统 SHALL 切换该纪要的选中状态
8. IF 用户再次长按或点击关闭按钮 THEN 系统 SHALL 退出多选模式并清除所有选中

##### 3.3 事件发展脉络简报生成
9. WHEN 用户选择 2 条或以上纪要并点击"生成脉络简报" THEN 系统 SHALL 调用 LLM API 将多条纪要的内容合并，按时间顺序生成一份事件发展脉络简报
10. WHEN 脉络简报生成中 THEN 系统 SHALL 显示加载指示器
11. WHEN 脉络简报生成完成 THEN 系统 SHALL 展示简报详情页，包含：
    - 事件总标题
    - 时间线概览（每条纪要对应一个时间节点，显示日期和关键摘要）
    - 事件发展趋势分析
    - 当前最新状态总结
    - 后续建议/待办
12. IF 选中的纪要不足 2 条 THEN 系统 SHALL 禁用"生成脉络简报"按钮并显示提示
13. WHEN 用户在简报详情页 THEN 系统 SHALL 支持导出为 Markdown 或文本格式（复用现有导出逻辑）

---

### 需求 4 — LLM/ASR 模型选择与提示词优化

**用户故事：** 作为一名用户，我希望应用的语音转写和纪要生成效果更加准确、结构化，以便获得更高质量的转写文本和会谈纪要。

#### 模型选型决策

| 能力层 | 当前模型 | V2 目标 | 变更说明 |
|--------|---------|---------|---------|
| ASR（语音转写） | `step-asr`（Multipart 上传） | **`step-asr`（维持不变）** | `step-1-flash` 可用性未经验证，维持现状；清理代码中未使用的 `StepFunAsrRequest` 数据类 |
| LLM（纪要生成） | `step-2-16k`（16K 上下文） | **`step-3.5-flash`** | 更强推理能力、更好 JSON 遵从性、更大上下文窗口（利于多纪要合并） |

> **注意**：不采用端到端语音模型，保持 ASR → LLM 两阶段流水线（理由详见引言部分的技术决策章节）。

#### 验收标准

##### 4.1 ASR 代码清理与容错增强（维持 step-asr）
1. WHEN 转写音频文件 THEN 系统 SHALL 继续使用 `step-asr` 模型通过 Multipart 上传方式调用 `/v1/audio/transcriptions` 端点
2. WHEN 代码中存在未使用的 `StepFunAsrRequest` 数据类和 `transcribeAudio` JSON Body 接口 THEN 系统 SHALL 清理或移除这些死代码，消除与实际调用方式的不一致
3. WHEN ASR 调用发生网络异常（`SocketException`/`UnknownHostException`/`IOException`） THEN 系统 SHALL 增加重试机制（最多 3 次，间隔递增 3s/6s/9s）
4. IF 文件大小超过 25MB THEN 系统 SHALL 提示用户文件过大，建议压缩后重试

##### 4.2 LLM 模型升级与纪要提示词优化
5. WHEN 生成会谈纪要 THEN 系统 SHALL 使用 **`step-3.5-flash`** 模型替代 `step-2-16k`
6. WHEN 生成会谈纪要 THEN 系统 SHALL 使用优化后的 System Prompt，包含：
   - 更明确的角色设定（专业商务/个人事务会谈纪要助手）
   - 输出语言要求（中文）
   - JSON 输出格式的严格约束（仅返回 JSON，不添加额外文字）
   - 各字段的字数和内容要求（如摘要 100-200 字、关键要点 3-7 条等）
   - 待办事项的优先级枚举约束（`HIGH`/`MEDIUM`/`LOW`）
7. WHEN 构建 user prompt 时 THEN 系统 SHALL 在对话转写内容前添加录音元数据上下文（联系人名称、通话时间、通话时长），以帮助 LLM 更好地理解上下文
8. WHEN LLM 返回的 JSON 解析失败 THEN 系统 SHALL 尝试修复常见的 JSON 格式问题（如尾部逗号、缺少引号、Markdown 代码块包裹），而非直接降级为默认结构

##### 4.3 LLM 脉络简报专用提示词
9. WHEN 生成事件发展脉络简报 THEN 系统 SHALL 使用 `step-3.5-flash` 模型并配合专用 System Prompt，要求 LLM：
    - 按时间顺序梳理多次会谈中讨论的同一事件的发展脉络
    - 提取每次会谈中事件的关键状态变化
    - 分析事件发展趋势
    - 总结当前最新状态
    - 给出后续跟进建议
10. WHEN 构建脉络简报 prompt 时 THEN 系统 SHALL 将多条纪要按时间排序，每条标注日期和联系人，以便 LLM 理解时序关系

---

## 技术约束与注意事项

1. **数据库兼容性**：`MeetingMinuteEntity` 当前没有直接的 `contactName` 字段，需通过 `transcriptId → TranscriptEntity.recordId → CallRecordEntity.contactName` 关联。如果频繁查询性能不佳，可考虑在 `MeetingMinuteEntity` 中增加冗余字段
2. **Activity 生命周期**：接收界面可以实现为 Dialog/BottomSheet（在 MainActivity 内）或独立 Activity，需注意 `singleTask`/`singleTop` 启动模式对 `onNewIntent` 的影响
3. **权限**：通讯录选择功能依赖 `READ_CONTACTS` 权限，需处理用户拒绝权限的场景
4. **API 限制**：`step-3.5-flash` 的上下文窗口比 `step-2-16k` 更大，但合并多条纪要时仍需注意总长度，必要时分批处理或摘要后再合并
5. **脉络简报存储**：简报可以作为特殊类型的 `MeetingMinuteEntity` 存储，或新建独立的 Entity
6. **两阶段架构不变**：保持 ASR（`step-asr`）→ LLM（`step-3.5-flash`）的流水线架构，不使用端到端语音模型
7. **ASR 死代码清理**：`ApiModels.kt` 中的 `StepFunAsrRequest`/`AudioInput` 数据类和 `StepFunApiService` 中的 `transcribeAudio` 方法目前未被使用，应予以清理以避免混淆
