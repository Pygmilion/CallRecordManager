# 长音频后台转写稳定性修复方案（2026-03-28）

## 1. 背景与问题

近期在 Android 端出现以下高频问题：

1. 长音频（如 40min）长期停留在“转写中”，最终超时或反复重试。
2. 应用退到后台后，转写任务可能中断或从分片起点重复开始。
3. 任务失败/删除后，UI 仍残留全局“正在转写”提示（幽灵状态）。
4. 多文件导入（尤其分享导入）状态语义不一致：后续文件直接显示“转写中”，缺少“排队中”。
5. 录音扫描未覆盖 `Music/Recordings/CallRecordings`，导致扫描结果不全。

## 2. 目标

- 保证长音频在后台可持续执行，避免无进度循环。
- 支持分片断点续跑，网络波动或进程变化后可恢复。
- 让状态机收敛：任务取消/异常/删除后不再永久停留 PROCESSING。
- 明确串行队列语义：只有一个运行中，其余均为排队中。
- 去除 App 顶部全局转写提示，仅保留录音卡片级状态。

## 3. 实施内容

### 3.1 后台执行与通知策略

- 引入 WorkManager 串行任务链，新增：
  - `app/src/main/java/com/callrecord/manager/work/TranscriptionWorker.kt`
  - `app/src/main/java/com/callrecord/manager/work/TranscriptionWorkScheduler.kt`
- Worker 运行期间启用前台桥接通知（ForegroundInfo + dataSync），任务结束统一关闭进度通知。
- 成功/失败保留结果通知；移除应用内“额头全局进度条”路径。
- `CancellationException` 统一转为可收敛失败结果，避免无限重启。

### 3.2 长音频分片与断点续跑

- 新增切片工具：
  - `app/src/main/java/com/callrecord/manager/utils/AudioChunker.kt`
- 在仓库层实现 checkpoint（本地 JSON）持久化：
  - 维度：`recordId + 源文件指纹(path hash/size/mtime)`
  - 内容：总分片、下一分片索引、已合并文本、分片目录路径
- 默认分片时长设置为 **3 分钟/片**，降低单请求失败概率。
- 每个分片完成后即时原子更新 checkpoint；全部完成后清理 checkpoint 和分片目录。
- checkpoint 不匹配或分片缺失时自动重建，避免脏状态污染。

### 3.3 状态机收敛与孤儿状态修复

- `RecordProcessStage` 新增 `QUEUED` 状态，UI 明确显示“排队中...”。
- `MainViewModel` 启动与刷新时结合 WorkManager 状态重建每条录音的处理阶段。
- 新增孤儿 PROCESSING 修复逻辑：
  - 若无活跃 Work 且 PROCESSING/PENDING 超过阈值，自动落为 FAILED。
- 删除录音时：取消该 record 关联 Work + 清 checkpoint + 清关联转写/纪要。

### 3.4 多文件分享导入与扫描增强

- 分享入口支持 `SEND_MULTIPLE`，支持批量文件复制导入。
- 导入对话框支持批量文件展示和批量处理入口。
- 扫描逻辑改为递归并补充目录：
  - `/storage/emulated/0/Music/Recordings/CallRecordings`
- 扫描和导入均加入路径去重/失败隔离，避免重复与整批失败。

### 3.5 网络与请求稳定性

- StepFun 客户端优化：
  - 上传日志级别降为 BASIC（避免大文件 BODY 日志开销）
  - 设置 `HTTP/1.1`、连接重试、延长读写/call timeout
- 分片转写请求按可重试错误码与网络异常做重试与退避。

## 4. 关键状态流转（目标语义）

单条录音：

`IDLE -> QUEUED -> TRANSCRIBING -> TRANSCRIBE_DONE -> GENERATING_MINUTE -> COMPLETED`

异常分支：

- 转写异常/取消：`... -> TRANSCRIBE_FAILED`
- 删除任务：取消 Work 并移除该条状态，不残留“转写中”

## 5. 验证结论（当前版本）

已完成构建验证：

- `:app:lintDebug` 通过
- `:app:assembleDebug` 通过

现场验证反馈：

- 40min 音频可完成转写（此前卡死问题已缓解）
- 仍需继续观察“极端网络抖动 + 多长音频并行导入”场景稳定性

## 6. 已知限制与后续优化

1. 后台稳定性依赖前台服务通知（Android 机制限制，无法完全隐藏）。
2. 分片后文本拼接仍是纯串接，后续可增加段落清洗与去重。
3. 队列当前为全局串行（FIFO），后续可加入优先级或手动重排。
4. 可考虑为“排队中”增加预计等待信息（前方任务数量/预估时间）。

## 7. 回滚与应急

如线上出现新风险，可按以下顺序回退：

1. 先关闭分片转写开关（回退为整段上传）。
2. 保留 WorkManager 串行调度，仅回退 checkpoint 恢复逻辑。
3. 必要时回退到旧转写路径，同时保留状态机收敛修复（避免幽灵状态）。

---

文档目的：为后续迭代提供可追踪的技术依据，确保“问题-方案-验证-回滚”闭环。
