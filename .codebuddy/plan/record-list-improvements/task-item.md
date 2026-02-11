# 实施计划

- [ ] 1. 录音列表分组折叠/展开功能
  - [ ] 1.1 在 `RecordListScreen.kt` 中添加折叠状态管理
    - 在 `RecordListScreen` 组合函数中新增 `expandedGroups: MutableState<Set<String>>` 状态，默认包含所有分组 key（即默认全部展开）
    - 当 `groupedRecords` 变化时，使用 `LaunchedEffect` 同步新分组到 `expandedGroups`
    - _需求：1.1、1.3_

  - [ ] 1.2 改造 `ContactGroupHeader` 组件支持点击折叠
    - 为 `ContactGroupHeader` 添加 `isExpanded: Boolean` 和 `onToggle: () -> Unit` 参数
    - 在 Header 右侧添加折叠/展开图标（`ExpandMore` / `ExpandLess`），带旋转动画
    - 整个 Header 区域可点击（`Modifier.clickable`）触发折叠切换
    - _需求：1.1、1.2_

  - [ ] 1.3 在 LazyColumn 中用 `AnimatedVisibility` 包裹分组内的录音列表项
    - 在 `groupedRecords.forEach` 循环中，根据 `expandedGroups` 状态决定是否显示子项
    - 使用 `AnimatedVisibility(visible = isExpanded, enter = expandVertically + fadeIn, exit = shrinkVertically + fadeOut)` 包裹 `items(recordsInGroup)` 区块
    - 搜索状态（`searchQuery` 非空）下强制所有分组展开，不响应折叠操作
    - _需求：1.2、1.5、边界情况_

  - [ ] 1.4 移除 `BrandRecordItem` 中的联系人名称显示
    - 在 `BrandRecordItem` 的 Middle info `Column` 中，将当前显示 `record.contactName ?: record.phoneNumber ?: "未知联系人"` 的 `Text` 移除
    - 替换为显示录音时间（`dateFormat.format(Date(record.recordTime))`）作为第一行主信息
    - 调整卡片布局，保留：录音时间、录音时长、处理状态徽章、更多操作菜单
    - _需求：1.4_

- [ ] 2. 手动编辑联系人名称 — 数据层
  - [ ] 2.1 在 `CallRecordDao`（`Daos.kt`）中新增按手机号批量更新联系人名称的 SQL 方法
    - 添加 `@Query("UPDATE call_records SET contactName = :newName WHERE phoneNumber = :phoneNumber") suspend fun updateContactNameByPhone(phoneNumber: String, newName: String)`
    - _需求：2.3_

  - [ ] 2.2 在 `CallRecordRepository.kt` 中新增 `updateContactName(phoneNumber: String, newName: String): Result<Unit>` 方法
    - 调用 `callRecordDao.updateContactNameByPhone(phoneNumber, newName)`
    - 包装在 `try-catch` 中返回 `Result`
    - _需求：2.3_

  - [ ] 2.3 在 `MainViewModel.kt` 中新增 `updateContactName(phoneNumber: String, newName: String)` 方法
    - 在 `viewModelScope.launch` 中调用 `repository.updateContactName()`
    - 成功后设置 `_successMessage`，失败后设置 `_errorMessage`
    - 更新完成后列表会通过 Flow 自动刷新
    - _需求：2.3、2.4_

- [ ] 3. 手动编辑联系人名称 — UI 层
  - [ ] 3.1 在 `RecordListScreen.kt` 中创建 `EditContactNameDialog` 组合函数
    - 包含：标题 "编辑联系人名称"、手机号显示（只读 `Text`）、联系人名称输入框（`OutlinedTextField`）、"确认" 和 "取消" 按钮
    - "确认" 按钮在输入为空时置灰（`enabled = inputName.isNotBlank()`）
    - _需求：2.2、2.5_

  - [ ] 3.2 在 `ContactGroupHeader` 中条件显示编辑按钮
    - 新增参数 `phoneNumber: String?`、`hasContactName: Boolean`、`onEditContactName: () -> Unit`
    - 当 `hasContactName == false`（即分组 key 等于手机号，原始 contactName 为空）时，在 Header 上显示编辑图标按钮（`Icons.Outlined.Edit`）
    - 点击后触发 `onEditContactName` 回调，弹出 `EditContactNameDialog`
    - _需求：2.1_

  - [ ] 3.3 在 `RecordListScreen` 中串联编辑对话框与 ViewModel
    - 添加 `showEditDialog` 和 `editingPhoneNumber` 状态
    - 在 `ContactGroupHeader` 的 `onEditContactName` 回调中设置状态并显示对话框
    - 对话框确认后调用 `viewModel.updateContactName(phoneNumber, newName)`
    - _需求：2.2、2.3、2.4_

- [ ] 4. 会谈纪要参与者布局改为纵向排列
  - [ ] 4.1 修改 `MinuteDetailScreen.kt` 中参与者区域的布局
    - 将 Participants 部分的 `Row(horizontalArrangement = Arrangement.spacedBy(8.dp))` 改为 `Column(verticalArrangement = Arrangement.spacedBy(8.dp))`
    - 每个参与者 `Surface` 标签添加 `Modifier.fillMaxWidth()`，使其宽度一致
    - 保持每个参与者标签内部的 `Row`（图标 + 姓名 + 角色）样式不变
    - _需求：3.1、3.2、3.4_
