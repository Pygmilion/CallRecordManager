package com.callrecord.manager.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callrecord.manager.data.local.MeetingMinuteEntity
import com.callrecord.manager.data.local.MinuteWithContact
import com.callrecord.manager.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Minute list screen – redesigned with brand styling, sticky headers, multi-select
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MinuteListScreen(
    viewModel: MainViewModel,
    onMinuteClick: (MeetingMinuteEntity) -> Unit,
    onGenerateTimelineBrief: (List<MinuteWithContact>) -> Unit = {}
) {
    val minutesWithContact by viewModel.minutesWithContact.collectAsState()
    val searchQuery by viewModel.minuteSearchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val logMessages by viewModel.logMessages.collectAsState()

    var showSearchBar by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    // Delete confirmation
    var minuteToDelete by remember { mutableStateOf<MinuteWithContact?>(null) }

    // Group by contact
    val groupedMinutes = remember(minutesWithContact) {
        minutesWithContact.groupBy { it.contactName ?: "未知联系人" }
    }

    fun exitMultiSelect() {
        isMultiSelectMode = false
        selectedIds = emptySet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = {
                        Text(
                            "已选择 ${selectedIds.size} 项",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitMultiSelect() }) {
                            Icon(Icons.Default.Close, "取消多选")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else if (showSearchBar) {
                BrandSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.searchMinutes(it) },
                    onClose = {
                        showSearchBar = false
                        viewModel.searchMinutes("")
                    }
                )
            } else {
                LargeTopAppBar(
                    title = {
                        Text(
                            "会谈纪要",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Outlined.Search, "搜索")
                        }
                        IconButton(onClick = { viewModel.refreshMinutes() }) {
                            Icon(Icons.Outlined.Refresh, "刷新")
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                minutesWithContact.isEmpty() -> {
                    BrandEmptyState(
                        icon = Icons.Outlined.Description,
                        message = "暂无会谈纪要",
                        hint = "转写录音后将自动生成纪要",
                        actionText = "为已转写录音生成纪要",
                        onAction = { viewModel.regenerateMinutes() }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = 8.dp, bottom = if (isMultiSelectMode) 80.dp else 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        groupedMinutes.forEach { (contactName, minutesInGroup) ->
                            // Sticky header with avatar
                            stickyHeader(key = "header_$contactName") {
                                MinuteGroupHeader(
                                    contactName = contactName,
                                    count = minutesInGroup.size
                                )
                            }

                            items(minutesInGroup, key = { it.id }) { minuteWithContact ->
                                BrandMinuteItem(
                                    minuteWithContact = minuteWithContact,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = minuteWithContact.id in selectedIds,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            selectedIds = if (minuteWithContact.id in selectedIds) {
                                                selectedIds - minuteWithContact.id
                                            } else {
                                                selectedIds + minuteWithContact.id
                                            }
                                        } else {
                                            onMinuteClick(minuteWithContact.toEntity())
                                        }
                                    },
                                    onLongClick = {
                                        if (!isMultiSelectMode) {
                                            isMultiSelectMode = true
                                            selectedIds = setOf(minuteWithContact.id)
                                        }
                                    },
                                    onDelete = {
                                        minuteToDelete = minuteWithContact
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Bottom floating action bar for multi-select
            AnimatedVisibility(
                visible = isMultiSelectMode && selectedIds.size >= 2,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    shape = ButtonShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "已选 ${selectedIds.size} 条纪要",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Button(
                            onClick = {
                                val selected = minutesWithContact.filter { it.id in selectedIds }
                                onGenerateTimelineBrief(selected)
                                exitMultiSelect()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimary,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = ButtonShape
                        ) {
                            Icon(Icons.Outlined.Timeline, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("生成脉络简报")
                        }
                    }
                }
            }
        }

        // Log dialog
        if (showLogDialog) {
            LogDialog(
                logs = logMessages,
                viewModel = viewModel,
                onDismiss = { showLogDialog = false }
            )
        }

        // Delete dialog
        minuteToDelete?.let { minute ->
            AlertDialog(
                onDismissRequest = { minuteToDelete = null },
                shape = DialogShape,
                title = { Text("删除纪要", style = MaterialTheme.typography.titleLarge) },
                text = { Text("确定要删除纪要\"${minute.title}\"吗？此操作不可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteMinute(minute.toEntity())
                            minuteToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { minuteToDelete = null }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

/**
 * Sticky group header for minutes grouped by contact
 */
@Composable
fun MinuteGroupHeader(contactName: String, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = contactName.take(1).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = contactName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = StatusTagShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Brand styled minute card with multi-select support
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrandMinuteItem(
    minuteWithContact: MinuteWithContact,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit = {}
) {
    val minute = minuteWithContact.toEntity()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val displayTime = minuteWithContact.recordTime ?: minute.createTime

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Checkbox in multi-select mode
            if (isMultiSelectMode) {
                Surface(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(top = 2.dp),
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                // Title
                Text(
                    text = minute.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Summary preview
                Text(
                    text = minute.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Meta info row: key points + action items + time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                            shape = StatusTagShape,
                            color = StatusColors.infoContainer
                        ) {
                            Text(
                                "${minute.keyPoints.size} 要点",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusColors.info,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        if (minute.actionItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = StatusTagShape,
                                color = StatusColors.successContainer
                            ) {
                                Text(
                                    "${minute.actionItems.size} 待办",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StatusColors.success,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = dateFormat.format(Date(displayTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Source recording
                if (minuteWithContact.recordFileName != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.GraphicEq, null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = minuteWithContact.recordFileName!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Delete button (normal mode only)
            if (!isMultiSelectMode) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除纪要",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
