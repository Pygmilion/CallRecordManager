# 实施计划

- [ ] 1. 修复系统 Back 键导航 — 为所有子页面添加 BackHandler
  - 在 `MainActivity.kt` 的 `MainApp` 函数中，为 4 个子页面状态分别添加 `BackHandler`：
    - `selectedRecord != null` → 设置 `selectedRecord = null`（同时停止音频播放器）
    - `selectedMinute != null` → 设置 `selectedMinute = null`
    - `showTimelineBrief` → 设置 `showTimelineBrief = false`
    - `pendingFile != null`（AudioReceiveScreen）→ 调用 `viewModel.clearPendingShareFile()`
  - 确保这些 `BackHandler` 在对应子页面显示时 `enabled = true`，优先级高于现有的活跃任务拦截 `BackHandler`
  - 确保 `RecordDetailScreen` 的 `BackHandler` 中调用 `exoPlayer.stop()` / `exoPlayer.release()` 清理播放器资源
  - _需求：1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

- [ ] 2. 重新设计 App Logo — 飞书风格蓝白绿配色
  - 修改 `app/src/main/res/mipmap-hdpi/ic_launcher_foreground.xml`，重新设计矢量图标：
    - 麦克风主体使用飞书蓝 `#3370FF`
    - 声波弧线使用品牌绿 `#34D399` / `#10B981`
    - 在麦克风和声波之间添加白色 `#FFFFFF` 高光元素或留白间隔
    - 整体保持几何感强、简洁现代的风格
  - 修改 `app/src/main/res/values/colors.xml` 中 `ic_launcher_background` 颜色值，调整为适配新配色的深色背景（如 `#1E293B` 深蓝灰）
  - 确保图标核心图形在 Adaptive Icon 安全区域（内圈 66dp）内，不被圆形/圆角矩形裁剪
  - _需求：2.1, 2.2, 2.3, 2.4, 2.5_

- [ ] 3. 修复搜索 Flow 竞争问题 — 用 Job 管理 collect 协程
  - 在 `MainViewModel.kt` 中：
    - 将 `loadRecords()` 返回的 `Job` 保存为类成员变量 `private var loadRecordsJob: Job? = null`
    - `loadRecords()` 方法中：先取消上一个 `loadRecordsJob`，再启动新的 `viewModelScope.launch` 并赋值给 `loadRecordsJob`
    - `searchRecords(query)` 方法中：当 `query` 非空时，先调用 `loadRecordsJob?.cancel()` 停止全量列表监听，再启动搜索 collect
    - `searchRecords(query)` 方法中：当 `query` 为空时，不重复调用 `loadRecords()`（避免叠加协程），改为仅在 `loadRecordsJob` 已被取消时才重新启动
  - 同样对 `loadMinutes()` 和 `searchMinutes()` 做相同的 Job 管理处理
  - _需求：3.1, 3.4, 3.5_

- [ ] 4. 扩展搜索 SQL 范围 — 增加 fileName 字段匹配
  - 在 `Daos.kt` 的 `CallRecordDao.searchRecords()` 方法中，修改 `@Query` SQL：
    - 原始：`WHERE phoneNumber LIKE '%' || :query || '%' OR contactName LIKE '%' || :query || '%'`
    - 新增：`OR fileName LIKE '%' || :query || '%'`
  - 验证 `CallRecordEntity` 中 `fileName` 字段名与数据库列名一致
  - _需求：3.2, 3.3_

- [ ] 5. 编译验证与集成测试
  - 运行 `./gradlew assembleDebug` 确保编译通过
  - 手动验证以下场景：
    - 在录音详情页按 Back → 回到录音列表
    - 在纪要详情页按 Back → 回到纪要列表
    - 在脉络简报页按 Back → 回到纪要列表
    - 在音频接收页按 Back → 关闭接收页
    - 在主列表页按 Back → 正常退出 App
    - 搜索关键词 → 结果稳定显示不被覆盖
    - 清空搜索 → 恢复全量列表
    - 搜索文件名 → 能匹配到结果
    - 新 Logo 在桌面上正确显示蓝白绿配色
  - _需求：1.1-1.6, 2.1-2.5, 3.1-3.5_
