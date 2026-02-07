# 需求文档 — UI 全面改版 & 后台任务可靠性

## 引言

本需求文档涵盖两大方向的改进：

### 1. 后台任务可靠性
当前应用所有的 ASR 转写和 LLM 纪要生成任务都在 `MainViewModel` 的 `viewModelScope` 协程中执行。当用户将 App 切换到后台后，Android 系统可能随时杀死进程，导致正在执行的长时间网络请求（如大文件 ASR 转写、LLM 纪要生成）中断，任务进度丢失。当前没有任何 ForegroundService、WorkManager 或其他机制保障后台任务的可靠执行，也没有向用户发出任何"请保持 App 在前台"的提醒。

### 2. UI 全面改版
当前应用使用默认的 Material 3 主题（紫色 + Dynamic Color），图标是默认的加号图案，App 名称为"通话录音管理"。交互和视觉风格偏原始/开发者工具风格，缺乏现代消费级产品的设计感。

本次改版目标：
- App 名称改为 **"语音快记"**
- 参照 **OpenAI ChatGPT、Anthropic Claude、Manus** 等顶级 AI 产品的设计语言
- 打造简洁、现代、专业的交互体验
- 使用自定义配色方案替代默认 Material 紫色
- 设计专属应用图标
- 全面优化卡片、列表、按钮、导航等核心 UI 组件

### 当前技术状况

| 维度 | 现状 | 问题 |
|------|------|------|
| App 名称 | "通话录音管理" | 不够简洁吸引 |
| 主题 | 默认 Material 3 紫色 + Dynamic Color | 缺乏品牌特色 |
| 图标 | 默认白圈+绿色加号 | 无辨识度 |
| Typography | 默认 `Typography()` | 无自定义字体层级 |
| 卡片样式 | 默认 Material Card | 过于朴素 |
| 导航 | 底部 `NavigationBar`（录音/纪要两个 tab） | 可保留但需要美化 |
| 后台任务 | `viewModelScope.launch` 纯协程 | App 退后台即中断 |
| 通知 | 无 | 无任何进度通知或保活机制 |

---

## 需求

### 需求 1 — 后台任务可靠性与用户提醒

**用户故事：** 作为一名用户，我希望在触发转写/纪要生成等耗时任务后，即使将 App 切到后台，任务也不会中断；或者至少在任务进行时能收到明确提醒，让我知道需要保持 App 在前台，以便任务不会意外失败。

#### 验收标准

##### 1.1 任务进行中的前台提醒
1. WHEN 用户触发 ASR 转写或 LLM 纪要生成任务 THEN 系统 SHALL 在屏幕顶部显示一条醒目的、不可关闭的提示横幅（如半透明黑底白字），内容为"正在处理中，请保持 App 在前台运行..."
2. WHEN 任务完成或失败 THEN 系统 SHALL 自动隐藏上述提示横幅
3. IF 用户试图通过返回键或 Home 键离开 App 且有任务正在执行 THEN 系统 SHALL 弹出确认对话框提示"有任务正在处理中，离开可能导致任务中断，是否继续？"

##### 1.2 系统通知
4. WHEN 用户触发耗时任务 THEN 系统 SHALL 发送一条持续显示的系统通知（Notification），显示任务进度（如"转写中: 录音文件名"），点击通知可返回 App
5. WHEN 任务完成 THEN 系统 SHALL 更新通知为"转写完成"或"纪要已生成"，并在 5 秒后自动清除
6. WHEN 任务失败 THEN 系统 SHALL 更新通知为"任务失败：原因"，点击通知可返回 App 重试

##### 1.3 后台保活（可选/低优先级）
7. IF 技术可行且不影响用户体验 THEN 系统 SHOULD 使用 ForegroundService 或 WorkManager 保障任务在后台也能继续执行
8. IF 使用 ForegroundService THEN 系统 SHALL 在状态栏显示一条持续通知，告知用户后台有任务正在运行

> **技术说明**：考虑到 ForegroundService 的复杂度和权限要求（Android 14+ 需要 `FOREGROUND_SERVICE_DATA_SYNC` 等特殊权限），优先实现 1.1 + 1.2（前台提醒+通知），1.3 作为可选增强。

---

### 需求 2 — App 品牌改造：名称与图标

**用户故事：** 作为一名用户，我希望 App 的名称和图标能体现产品定位和品质感，以便在桌面和最近任务列表中能快速辨认。

#### 验收标准

##### 2.1 App 名称
1. WHEN App 安装到设备上 THEN 系统 SHALL 在桌面图标下方显示名称 **"语音快记"**
2. WHEN App 在最近任务列表或系统设置中显示 THEN 系统 SHALL 使用 **"语音快记"** 作为应用名称

##### 2.2 App 图标
3. WHEN App 安装到设备上 THEN 系统 SHALL 显示一个自定义设计的应用图标，符合以下设计要求：
   - 使用 Adaptive Icon 格式（前景 + 背景分层）
   - 图标主体为语音/声波/麦克风相关的简约符号
   - 配色与 App 主题色一致（暖色系或蓝绿色系，参考需求 3 中的配色方案）
   - 风格现代简约，参照 OpenAI/Claude/Manus 等产品的图标设计感
   - 使用 Vector Drawable（XML 矢量图）实现，支持所有 DPI
4. WHEN 系统使用圆形图标蒙版 THEN 系统 SHALL 提供适配圆形蒙版的 round icon

---

### 需求 3 — 主题与配色系统重构

**用户故事：** 作为一名用户，我希望 App 的视觉风格现代、简洁、有品质感，参照 OpenAI ChatGPT / Claude / Manus 等产品的设计语言，以便使用时感受到专业和愉悦。

#### 验收标准

##### 3.1 配色方案
1. WHEN App 启动 THEN 系统 SHALL 使用自定义的品牌配色方案，**不再使用 Dynamic Color 和默认 Material 紫色**
2. WHEN 使用浅色模式 THEN 系统 SHALL 采用以下配色风格：
   - 主背景：接近纯白或极浅灰（如 `#FAFAFA` 或 `#F7F7F8`），营造干净开阔感
   - 主色调（Primary）：深邃的蓝黑色或深青色（如 `#1A1A2E` 或 `#0D9488`），用于关键操作按钮和强调元素
   - 次要色（Secondary）：柔和的灰色系（如 `#6B7280`），用于辅助文字和图标
   - 强调色（Accent/Tertiary）：温暖的绿色或蓝绿色（如 `#10B981`），用于成功状态和积极反馈
   - 错误色：柔和的红色（如 `#EF4444`）
   - 卡片背景：纯白 `#FFFFFF`，配合微妙阴影
3. WHEN 使用深色模式 THEN 系统 SHALL 采用深色配色方案：
   - 主背景：深灰黑色（如 `#0F0F0F` 或 `#1A1A1A`），非纯黑
   - 卡片背景：略浅于背景的灰色（如 `#2A2A2A`）
   - 主色调与浅色模式保持一致性，适当调整亮度
   - 文字颜色：高对比度的白灰色（如 `#E5E7EB`）
4. WHEN 配色方案应用 THEN 系统 SHALL 确保所有文字与背景的对比度符合 WCAG AA 标准

##### 3.2 字体层级（Typography）
5. WHEN 显示页面标题 THEN 系统 SHALL 使用粗体 + 较大字号（如 24sp），风格简洁有力
6. WHEN 显示卡片标题 THEN 系统 SHALL 使用中等粗细 + 中等字号（如 16-18sp）
7. WHEN 显示辅助信息 THEN 系统 SHALL 使用较小字号 + 较浅颜色（如 12-14sp），不喧宾夺主
8. WHEN 显示状态标签 THEN 系统 SHALL 使用小字号 + 圆角胶囊形背景

##### 3.3 圆角与间距
9. WHEN 渲染 Card 组件 THEN 系统 SHALL 使用较大的圆角半径（12-16dp），营造柔和现代感
10. WHEN 布局列表和卡片 THEN 系统 SHALL 使用充裕的间距（卡片间距 12-16dp，内边距 16-20dp），避免拥挤感

---

### 需求 4 — 录音列表页面（RecordListScreen）重构

**用户故事：** 作为一名用户，我希望录音列表页面简洁美观、信息清晰、操作便捷，以便快速找到和管理我的通话录音。

#### 验收标准

##### 4.1 顶部应用栏
1. WHEN 录音列表页面显示 THEN 系统 SHALL 在顶部显示简洁的应用栏，标题为"语音快记"或"录音"，风格为大标题（Large TopAppBar）+ 左对齐，参照 iOS/Claude 的导航栏风格
2. WHEN 应用栏显示 THEN 系统 SHALL 在右侧放置刷新和添加按钮，使用简约的线条图标

##### 4.2 录音卡片样式
3. WHEN 显示每条录音 THEN 系统 SHALL 使用带有微妙阴影和大圆角的卡片，包含：
   - 左侧：联系人头像占位符（圆形，带首字母或图标）
   - 中间：联系人名称（主标题）、通话日期时间（副标题）、文件时长（辅助信息）
   - 右侧：处理状态标签（胶囊形，使用语义化颜色）
4. WHEN 录音卡片显示更多操作 THEN 系统 SHALL 通过右侧的三点菜单按钮触发下拉菜单，下拉菜单样式使用 Material 3 的 DropdownMenu，带有适当的间距和图标

##### 4.3 空状态
5. WHEN 录音列表为空 THEN 系统 SHALL 显示一个精美的空状态插图和引导文字（如"还没有录音，点击右上角添加"），居中显示

##### 4.4 搜索栏
6. IF 录音列表存在搜索功能 THEN 系统 SHALL 使用圆角搜索框（参照 ChatGPT 的搜索栏风格），位于顶部应用栏下方

---

### 需求 5 — 纪要列表页面（MinuteListScreen）重构

**用户故事：** 作为一名用户，我希望纪要列表页面按联系人分组后，视觉层次清晰，分组标题醒目但不刺眼，以便快速浏览和定位纪要。

#### 验收标准

##### 5.1 分组标题样式
1. WHEN 纪要按联系人分组显示 THEN 系统 SHALL 使用粘性标题（Sticky Header）样式，标题行包含联系人名和纪要数量，使用浅色背景和较小字号
2. WHEN 分组标题显示 THEN 系统 SHALL 在标题左侧显示联系人头像占位符（与录音卡片风格一致）

##### 5.2 纪要卡片样式
3. WHEN 显示每条纪要 THEN 系统 SHALL 使用与录音卡片风格一致的大圆角卡片，包含：
   - 纪要标题（主标题，限两行）
   - 纪要摘要预览（副标题，限两行，颜色较浅）
   - 通话时间和来源录音文件名
   - 删除按钮（使用红色图标，尺寸适中不突兀）
4. WHEN 纪要处于多选模式 THEN 系统 SHALL 在卡片左侧显示勾选圆形复选框，使用品牌主色填充

##### 5.3 脉络简报入口
5. WHEN 多选模式激活且选中 2 条以上纪要 THEN 系统 SHALL 在底部显示一个悬浮操作栏（类似 Snackbar 位置），包含"生成脉络简报"按钮和已选数量

---

### 需求 6 — 详情页面美化

**用户故事：** 作为一名用户，我希望录音详情和纪要详情页面的布局合理、排版美观，以便舒适地查看内容。

#### 验收标准

##### 6.1 录音详情页（RecordDetailScreen）
1. WHEN 打开录音详情页 THEN 系统 SHALL 显示一个现代风格的音频播放器控件，包含：
   - 圆角进度条（不使用默认 Material Slider 样式）
   - 大的圆形播放/暂停按钮（居中），前进/后退按钮（两侧，较小）
   - 当前时间 / 总时长显示
2. WHEN 播放器下方显示转录文本 THEN 系统 SHALL 使用类似聊天气泡的样式显示每个说话人的文字，当前高亮段落使用主色调背景

##### 6.2 纪要详情页（MinuteDetailScreen）
3. WHEN 打开纪要详情页 THEN 系统 SHALL 使用清晰的排版展示各个章节（摘要、关键要点、待办事项、完整内容），每个章节使用左侧彩色竖线或图标区分
4. WHEN 显示待办事项 THEN 系统 SHALL 使用带优先级颜色标签的列表样式
5. WHEN 显示语音转录原文 THEN 系统 SHALL 使用可折叠区域，默认收起

##### 6.3 脉络简报页面
6. WHEN 显示事件脉络简报 THEN 系统 SHALL 使用时间轴样式排版（左侧竖线 + 时间节点圆点），每个节点展示日期和摘要

---

### 需求 7 — 底部导航栏美化

**用户故事：** 作为一名用户，我希望底部导航栏简洁美观，与整体设计风格一致。

#### 验收标准

1. WHEN 底部导航栏显示 THEN 系统 SHALL 使用自定义样式，不使用默认 Material NavigationBar 的浓重指示器样式：
   - 选中项：使用品牌主色的图标 + 文字，不使用大面积的椭圆指示器背景
   - 未选中项：使用浅灰色图标 + 文字
   - 导航栏背景与主背景色一致或使用微妙的毛玻璃效果
2. WHEN 用户切换 Tab THEN 系统 SHALL 使用平滑的过渡动画

---

### 需求 8 — 通用 UI 组件规范

**用户故事：** 作为一名用户，我希望 App 中所有的按钮、对话框、加载指示器等组件风格统一、现代。

#### 验收标准

##### 8.1 按钮
1. WHEN 显示主要操作按钮 THEN 系统 SHALL 使用填充样式（Filled）+ 大圆角（24dp+）+ 品牌主色
2. WHEN 显示次要操作按钮 THEN 系统 SHALL 使用描边样式（Outlined）+ 大圆角

##### 8.2 对话框
3. WHEN 显示对话框 THEN 系统 SHALL 使用大圆角（20dp+）+ 充裕的内边距 + 现代排版

##### 8.3 加载指示器
4. WHEN 后台任务执行中 THEN 系统 SHALL 使用与品牌主色一致的加载动画，而非默认 CircularProgressIndicator 样式

##### 8.4 Toast/提示
5. WHEN 需要向用户展示轻量提示 THEN 系统 SHALL 使用自定义的 Toast 样式或顶部滑入的提示条（非 Snackbar）

---

## 技术约束与注意事项

### 后台任务
1. **最小方案**：在 ViewModel 处理任务期间，通过 UI 提示栏 + Notification 提醒用户保持 App 在前台，配合 `onBackPressed` 拦截
2. **增强方案**：使用 `ForegroundService` + `FOREGROUND_SERVICE_DATA_SYNC` 权限（Android 14+），在 Service 中执行网络请求。这需要增加权限声明和 Service 代码，作为可选实现
3. **WorkManager**：适合可延迟的任务调度，但对于"用户立即发起的转写/纪要生成"实时性不足，不推荐作为首选

### UI 改版
4. **图标生成**：由于 Android 的图标是 PNG 或 VectorDrawable，代码中只能生成 XML 矢量图标。对于高质量位图图标，建议用户使用 Image Asset Studio 后续替换
5. **自定义 Typography**：使用 Material 3 的 `Typography` 自定义字体层级，无需引入第三方字体库
6. **向后兼容**：所有 UI 改动必须兼容 API 26+（Android 8.0），Adaptive Icon 需要 API 26+
7. **Compose 动画**：过渡动画使用 Compose Animation API，避免引入额外的动画库
8. **品牌一致性**：所有页面的配色、圆角、间距、字体必须保持一致，通过 Theme.kt 中的统一定义实现

### 影响范围
本次改版涉及以下文件的修改：
- `res/values/strings.xml` — App 名称
- `res/mipmap-*/ic_launcher*` — 应用图标
- `ui/theme/Theme.kt` — 主题配色
- `ui/theme/Color.kt` — 自定义颜色（新增）
- `ui/theme/Type.kt` — 自定义字体层级（新增）
- `ui/screen/RecordListScreen.kt` — 录音列表
- `ui/screen/MinuteListScreen.kt` — 纪要列表
- `ui/screen/RecordDetailScreen.kt` — 录音详情
- `ui/screen/MinuteDetailScreen.kt` — 纪要详情
- `ui/screen/TimelineBriefScreen.kt` — 脉络简报
- `ui/screen/AudioReceiveScreen.kt` — 音频接收
- `MainActivity.kt` — 主界面框架和导航
- `MainViewModel.kt` — 任务状态管理（后台提醒相关）
- `AndroidManifest.xml` — Notification channel、可选 Service 声明
