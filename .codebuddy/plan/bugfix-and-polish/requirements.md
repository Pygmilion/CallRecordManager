# 需求文档 — 导航修复、Logo 改版 & 搜索修复

## 引言

本次修改涉及三个独立问题的修复与优化：

1. **系统 Back 键导航异常**：在录音详情、纪要详情等子页面按返回键会直接退出整个 App，而非返回到列表主页面。
2. **App Logo 配色调整**：当前 Logo 为纯绿色麦克风 + 声波图标，需改为蓝白绿相间的飞书风格配色，使图标更具品牌辨识度。
3. **搜索功能异常**：搜索录音时结果被全量列表的 Flow 覆盖，导致搜索结果闪现后消失；同时搜索范围不够全面（不包含文件名）。

---

## 需求

### 需求 1：系统 Back 键导航修复

**用户故事：** 作为一名用户，我希望在录音详情或纪要详情页面按下系统返回键时能回到对应的列表页面，以便符合 Android 标准导航体验，而不是直接退出 App。

#### 验收标准

1. WHEN 用户在录音详情页面（`RecordDetailScreen`）按下系统 Back 键 THEN 系统 SHALL 导航回到录音列表页面，而非退出 App
2. WHEN 用户在纪要详情页面（`MinuteDetailScreen`）按下系统 Back 键 THEN 系统 SHALL 导航回到纪要列表页面，而非退出 App
3. WHEN 用户在脉络简报页面（`TimelineBriefScreen`）按下系统 Back 键 THEN 系统 SHALL 导航回到纪要列表页面，而非退出 App
4. WHEN 用户在音频接收页面（`AudioReceiveScreen`）按下系统 Back 键 THEN 系统 SHALL 关闭接收页面回到列表，而非退出 App
5. WHEN 用户在录音列表主页面按下系统 Back 键 THEN 系统 SHALL 正常退出 App（保持现有行为）
6. WHEN 播放器正在播放且用户在录音详情页按下 Back 键 THEN 系统 SHALL 停止播放并返回录音列表

#### 技术分析

**根因**：`MainActivity.kt` 中的 `MainApp` Composable 使用条件渲染方式显示详情页面（通过 `selectedRecord`、`selectedMinute`、`showTimelineBrief` 等状态变量控制），但这些子页面内部没有注册 `BackHandler`。因此系统 Back 事件会穿透到 Activity 层面，直接触发 Activity 的 `finish()` 退出 App。

现有的 `BackHandler` 仅在以下条件下生效：
```
BackHandler(enabled = hasActiveTasks && selectedRecord == null && selectedMinute == null && !showTimelineBrief)
```
即只有在「没有子页面打开」且「有活跃任务」时才拦截 Back 键。需要为每个子页面状态添加 `BackHandler` 拦截。

---

### 需求 2：App Logo 配色改版（飞书风格蓝白绿）

**用户故事：** 作为一名用户，我希望 App 图标采用蓝白绿相间的配色（借鉴飞书 Logo 风格），以便在主屏幕上具有更高的品牌辨识度和视觉吸引力。

#### 验收标准

1. WHEN App 安装到设备上 THEN 系统 SHALL 显示采用蓝白绿相间配色的新 Logo 图标
2. WHEN 查看新 Logo THEN 图标 SHALL 包含以下配色元素：
   - 蓝色（如 `#3370FF` 飞书蓝）用于麦克风主体或部分声波元素
   - 白色（`#FFFFFF`）用于图标内的高光、留白或部分图形元素
   - 绿色（如 `#10B981` 品牌绿或 `#34D399`）用于声波弧线或部分装饰元素
3. WHEN 查看新 Logo THEN 图标背景 SHALL 保持深蓝黑色（`#1A1A2E`）或调整为适配新配色的深色背景
4. WHEN 在浅色和深色壁纸前查看 THEN 图标 SHALL 保持清晰的辨识度
5. WHEN Adaptive Icon 裁剪为圆形或圆角矩形 THEN 核心图形 SHALL 不被裁剪

#### 设计参考

飞书 Logo 的核心特征：
- 使用「蓝 + 绿」双色构成主体图形，蓝色偏沉稳（`#3370FF`），绿色偏活力（`#34C759` 或类似）
- 色块之间有白色留白/间隔
- 整体风格简洁现代、几何感强

---

### 需求 3：搜索功能修复

**用户故事：** 作为一名用户，我希望在搜索录音时能正确过滤并显示匹配结果，以便快速找到我需要的录音文件。

#### 验收标准

1. WHEN 用户在搜索栏输入关键词 THEN 系统 SHALL 持续显示匹配结果，而非被全量列表覆盖
2. WHEN 用户搜索关键词匹配联系人名称或电话号码 THEN 系统 SHALL 正确返回匹配的录音记录
3. WHEN 用户搜索关键词匹配录音文件名 THEN 系统 SHALL 也能返回匹配的录音记录
4. WHEN 用户清空搜索栏或关闭搜索 THEN 系统 SHALL 恢复显示完整录音列表
5. WHEN 搜索进行期间有新录音导入 THEN 系统 SHALL 保持搜索状态不被覆盖，仅在搜索关键词为空时才响应全量列表更新

#### 技术分析

**根因**：`MainViewModel` 中存在两个 Flow 竞争问题：

1. `loadRecords()` 在 `init` 中启动，通过 `repository.getAllRecords().collect { records -> _records.value = records }` 持续监听数据库变更并将全量结果写入 `_records`
2. `searchRecords(query)` 中也 `collect` 搜索结果写入同一个 `_records`

由于 `loadRecords()` 的 Flow 是持续活跃的，任何数据库变更都会触发 `getAllRecords()` 的 emit，将 `_records` 重置为全量结果，覆盖搜索结果。

此外，DAO 的搜索 SQL 只匹配 `phoneNumber` 和 `contactName`，不匹配 `fileName` 字段。

**修复方向**：使用 `Job` 管理 `loadRecords` 的 collect 协程，在搜索开始时取消全量 collect，搜索清空时重新启动。同时扩展 SQL 搜索范围增加 `fileName` 字段匹配。
