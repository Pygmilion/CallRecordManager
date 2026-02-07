# 实施计划 — UI 全面改版 & 后台任务可靠性

- [ ] 1. 创建品牌配色系统与 Typography（Color.kt + Type.kt + Theme.kt 重构）
   - 新建 `ui/theme/Color.kt`，定义浅色/深色两套完整的品牌配色（Primary 深蓝黑 `#1A1A2E`、Accent 蓝绿 `#10B981`、背景 `#FAFAFA`/`#0F0F0F`、卡片白 `#FFFFFF`/`#2A2A2A`、错误红 `#EF4444` 等）
   - 新建 `ui/theme/Type.kt`，定义自定义 Typography 层级（页面标题 24sp Bold、卡片标题 16sp Medium、辅助信息 12sp、状态标签 11sp）
   - 重构 `ui/theme/Theme.kt`：移除 Dynamic Color 逻辑，使用自定义 `lightColorScheme()` / `darkColorScheme()`，引入新的 Typography，将 status bar 颜色改为背景色而非 primary
   - 定义通用的圆角 Shape 常量（Card 16dp、Button 24dp、Dialog 20dp），可放入 Theme.kt 或新增 Shape.kt
   - _需求：3.1, 3.2, 3.3_

- [ ] 2. App 名称与图标更新
   - 修改 `res/values/strings.xml` 中 `app_name` 为 **"语音快记"**
   - 设计并替换 `res/drawable/ic_launcher_foreground.xml`：用 XML VectorDrawable 绘制一个声波/麦克风主题的简约图标（3 条弧形声波线 + 圆形底座），配色使用品牌蓝绿色
   - 更新 `res/drawable/ic_launcher_background.xml` 或创建新的背景层（使用品牌深蓝黑色 `#1A1A2E` 纯色填充）
   - 确保 `res/mipmap-anydpi-v26/ic_launcher.xml` 和 `ic_launcher_round.xml` 引用正确的前景/背景
   - 清理各 mipmap-*dpi 目录下的旧 PNG 图标文件（如有），确保 Adaptive Icon 优先级生效
   - _需求：2.1, 2.2_

- [ ] 3. 底部导航栏与主界面框架美化（MainActivity.kt）
   - 重构 `MainActivity.kt` 中的底部 `NavigationBar`：去掉默认 Material 椭圆指示器，选中项用品牌主色图标+文字、未选中项用浅灰色，导航栏背景与主背景色一致
   - 为 Tab 切换添加 Compose `AnimatedContent` 或 `Crossfade` 过渡动画
   - 在 `MainActivity.kt` 中添加全局任务提醒横幅 Composable（半透明黑底白字顶部横幅），绑定 ViewModel 的任务执行状态
   - 添加 `onBackPressed` / `BackHandler` 拦截逻辑：当有任务执行中时弹出确认对话框
   - _需求：7.1, 7.2, 1.1.1, 1.1.2, 1.1.3_

- [ ] 4. 后台任务通知系统（Notification）
   - 在 `AndroidManifest.xml` 中声明 `POST_NOTIFICATIONS` 权限（Android 13+）
   - 在 `MainActivity.kt` 或 Application 类中创建 NotificationChannel（"task_progress"）
   - 新建 `utils/TaskNotificationHelper.kt` 工具类，封装以下功能：
     - `showProgressNotification(title, text)` — 发送/更新进度通知，点击 PendingIntent 返回 App
     - `showSuccessNotification(text)` — 显示完成通知，5 秒后自动清除
     - `showFailureNotification(text)` — 显示失败通知，点击返回 App
   - 在 `MainViewModel.kt` 的 `transcribeRecord()` 和 `generateMinute()` 方法中，在任务开始/成功/失败时调用 NotificationHelper 对应方法
   - 运行时请求 `POST_NOTIFICATIONS` 权限（Android 13+）
   - _需求：1.2.4, 1.2.5, 1.2.6_

- [ ] 5. 录音列表页面（RecordListScreen.kt）UI 重构
   - 重构顶部应用栏：改为 `LargeTopAppBar` 或自定义大标题样式，标题改为"语音快记"，右侧放置搜索/刷新/添加图标按钮（线条风格 Icons.Outlined）
   - 重构搜索栏：使用圆角大圆角 `OutlinedTextField` + 搜索图标，位于应用栏折叠区或下方
   - 重构录音卡片：大圆角（16dp）+ 微妙阴影（elevation 2dp）+ 左侧联系人头像占位符（`Surface` 圆形 + 首字母 Text）+ 中间信息区（联系人名、日期、时长）+ 右侧胶囊状态标签（根据 `RecordProcessStage` 使用语义化颜色）
   - 重构空状态：居中显示大图标 + 引导文字
   - 重构 FAB：使用品牌主色 + 大圆角
   - 重构下拉菜单：使用 Material 3 `DropdownMenu` + 图标 + 适当间距
   - _需求：4.1, 4.2, 4.3, 4.4_

- [ ] 6. 纪要列表页面（MinuteListScreen.kt）UI 重构
   - 重构分组标题：使用粘性标题样式（`stickyHeader`），左侧联系人头像占位符 + 联系人名 + 纪要数量，浅色背景 + 小字号
   - 重构纪要卡片：与录音卡片风格一致的大圆角卡片，纪要标题（限2行）+ 摘要预览（限2行、较浅色）+ 通话时间 + 来源录音文件名 + 删除按钮（红色图标、尺寸适中）
   - 重构多选模式：卡片左侧圆形复选框（品牌主色填充）+ 底部悬浮操作栏（选中数量 + "生成脉络简报"按钮）
   - _需求：5.1, 5.2, 5.3_

- [ ] 7. 录音详情页（RecordDetailScreen.kt）UI 美化
   - 重构音频播放器控件：自定义圆角进度条（`Canvas` 绘制或自定义 Slider 样式）+ 大圆形播放/暂停按钮（居中）+ 前进/后退小按钮（两侧）+ 时间显示
   - 重构转录文本显示：使用聊天气泡样式（不同说话人不同颜色/对齐方向），当前播放高亮段落使用品牌主色半透明背景
   - 统一页面背景和间距风格
   - _需求：6.1.1, 6.1.2_

- [ ] 8. 纪要详情页（MinuteDetailScreen.kt）& 脉络简报页（TimelineBriefScreen.kt）UI 美化
   - 重构纪要详情页的章节排版：每个章节（摘要、关键要点、待办、完整内容）使用左侧彩色竖线（`Modifier.drawBehind` 或 `Box` + `Divider`）或图标来区分
   - 待办事项使用带优先级颜色标签的列表样式
   - 语音转录原文可折叠区域默认收起，使用品牌一致的展开/收起动画
   - 重构脉络简报页为时间轴布局：左侧竖线 + 时间节点圆点（品牌色）+ 右侧日期和摘要内容
   - _需求：6.2.3, 6.2.4, 6.2.5, 6.3.6_

- [ ] 9. 通用 UI 组件统一与 AudioReceiveScreen 美化
   - 全局审查并统一所有 `Button` 样式：主要按钮使用 Filled + 24dp 圆角 + 品牌主色，次要按钮使用 Outlined + 24dp 圆角
   - 全局审查并统一所有 `AlertDialog` 样式：20dp+ 圆角 + 充裕内边距
   - 将所有 `CircularProgressIndicator` 替换为品牌色版本（`color = MaterialTheme.colorScheme.primary`）
   - 美化 `AudioReceiveScreen.kt`：使用与整体风格一致的卡片、按钮、间距
   - 实现顶部滑入提示条组件替代 Snackbar（可在 `MainActivity.kt` 中实现为全局组件）
   - _需求：8.1, 8.2, 8.3, 8.4, 8.5_

- [ ] 10. 深色模式适配与最终验证
   - 验证深色模式下所有页面的配色是否正确应用（背景 `#0F0F0F`、卡片 `#2A2A2A`、文字 `#E5E7EB`）
   - 检查 status bar 颜色在浅/深色模式下是否正确
   - 检查所有文字与背景的对比度是否达到 WCAG AA 标准
   - 确保 Notification 在深色模式下正常显示
   - 编译安装并全面验证所有页面的视觉效果
   - _需求：3.1.3, 3.1.4_
