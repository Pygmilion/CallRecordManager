package com.callrecord.manager.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.callrecord.manager.data.local.CallRecordEntity
import com.callrecord.manager.data.local.RecordProcessStage
import com.callrecord.manager.ui.theme.*
import com.callrecord.manager.utils.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Recording list screen – redesigned with brand styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    viewModel: MainViewModel,
    onRecordClick: (CallRecordEntity) -> Unit,
    onPickAudioFile: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onViewMinute: (CallRecordEntity) -> Unit = {}
) {
    val records by viewModel.records.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.recordSearchQuery.collectAsState()
    val logMessages by viewModel.logMessages.collectAsState()
    val recordProcessStages by viewModel.recordProcessStages.collectAsState()

    var showSearchBar by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    // Group by contact
    val groupedRecords = remember(records) {
        records.groupBy { record ->
            record.contactName ?: record.phoneNumber ?: "未知联系人"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showSearchBar) {
                BrandSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.searchRecords(it) },
                    onClose = {
                        showSearchBar = false
                        viewModel.searchRecords("")
                    }
                )
            } else {
                LargeTopAppBar(
                    title = {
                        Text(
                            "语音快记",
                            style = MaterialTheme.typography.headlineLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Outlined.Search, "搜索")
                        }
                        IconButton(onClick = { viewModel.scanRecordings() }) {
                            Icon(Icons.Outlined.Refresh, "扫描录音")
                        }
                        IconButton(onClick = onPickAudioFile) {
                            Icon(Icons.Outlined.Add, "添加文件")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Outlined.Settings, "设置")
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        floatingActionButton = {
            if (!isLoading) {
                FloatingActionButton(
                    onClick = onPickAudioFile,
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    shape = ButtonShape
                ) {
                    Icon(Icons.Default.Add, "添加文件")
                }
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
                records.isEmpty() -> {
                    BrandEmptyState(
                        icon = Icons.Outlined.GraphicEq,
                        message = "还没有录音",
                        hint = "点击右上角 + 添加音频文件，或扫描本地录音",
                        actionText = "扫描录音文件",
                        onAction = { viewModel.scanRecordings() }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = 8.dp, bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        groupedRecords.forEach { (contactName, recordsInGroup) ->
                            // Group header
                            item(key = "header_$contactName") {
                                ContactGroupHeader(
                                    contactName = contactName,
                                    count = recordsInGroup.size
                                )
                            }

                            // Records in group
                            items(recordsInGroup, key = { it.id }) { record ->
                                BrandRecordItem(
                                    record = record,
                                    onClick = { onRecordClick(record) },
                                    onTranscribe = { viewModel.transcribeRecord(record) },
                                    onDelete = { viewModel.deleteRecord(record) },
                                    onRetryMinute = { viewModel.retryGenerateMinute(record) },
                                    onViewMinute = { onViewMinute(record) },
                                    processStage = recordProcessStages[record.id] ?: RecordProcessStage.IDLE
                                )
                            }
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
    }
}

/**
 * Contact group header with avatar placeholder
 */
@Composable
fun ContactGroupHeader(contactName: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle with first character
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = contactName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = contactName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Brand styled record card
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandRecordItem(
    record: CallRecordEntity,
    onClick: () -> Unit,
    onTranscribe: () -> Unit,
    onDelete: () -> Unit,
    onRetryMinute: () -> Unit = {},
    onViewMinute: () -> Unit = {},
    processStage: RecordProcessStage = RecordProcessStage.IDLE
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left avatar
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Middle info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.contactName ?: record.phoneNumber ?: "未知联系人",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dateFormat.format(Date(record.recordTime)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formatDuration(record.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                RecordProcessBadge(stage = processStage)
            }

            // Menu button
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert, "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("转写并生成纪要") },
                        onClick = {
                            showMenu = false
                            onTranscribe()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Create, null) },
                        enabled = !record.isTranscribed
                    )
                    if (record.isTranscribed) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (processStage) {
                                        RecordProcessStage.MINUTE_FAILED -> "重试生成纪要"
                                        RecordProcessStage.COMPLETED -> "重新生成纪要"
                                        else -> "生成纪要"
                                    }
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRetryMinute()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) }
                        )
                    }
                    if (processStage == RecordProcessStage.COMPLETED) {
                        DropdownMenuItem(
                            text = { Text("查看纪要") },
                            onClick = {
                                showMenu = false
                                onViewMinute()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Description, null) }
                        )
                    }
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete, null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Recording process stage badge – capsule style with semantic colors
 */
@Composable
fun RecordProcessBadge(stage: RecordProcessStage) {
    val (text, bgColor, textColor, showSpinner) = when (stage) {
        RecordProcessStage.IDLE -> StatusBadgeData(
            "待处理",
            StatusColors.pendingContainer, StatusColors.pending, false
        )
        RecordProcessStage.TRANSCRIBING -> StatusBadgeData(
            "转写中...",
            StatusColors.processingContainer, StatusColors.processing, true
        )
        RecordProcessStage.TRANSCRIBE_DONE -> StatusBadgeData(
            "转写完成",
            StatusColors.infoContainer, StatusColors.info, false
        )
        RecordProcessStage.GENERATING_MINUTE -> StatusBadgeData(
            "纪要生成中...",
            StatusColors.processingContainer, StatusColors.processing, true
        )
        RecordProcessStage.COMPLETED -> StatusBadgeData(
            "已完成",
            StatusColors.successContainer, StatusColors.success, false
        )
        RecordProcessStage.TRANSCRIBE_FAILED -> StatusBadgeData(
            "转写失败",
            StatusColors.errorContainer, StatusColors.error, false
        )
        RecordProcessStage.MINUTE_FAILED -> StatusBadgeData(
            "纪要失败",
            StatusColors.errorContainer, StatusColors.error, false
        )
    }

    Surface(
        shape = StatusTagShape,
        color = bgColor,
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}

private data class StatusBadgeData(
    val text: String,
    val bgColor: Color,
    val textColor: Color,
    val showSpinner: Boolean
)

/**
 * Brand search bar with rounded corners
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "搜索录音...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = SearchBarShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, "清除")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "返回")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/**
 * Brand empty state with icon + guidance text + action button
 */
@Composable
fun BrandEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    hint: String = "",
    actionText: String = "",
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        if (hint.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (onAction != null && actionText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAction,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(actionText)
            }
        }
    }
}

/**
 * Format duration helper
 */
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%d:%02d", minutes, secs)
    }
}

/**
 * Log dialog
 */
@Composable
fun LogDialog(
    logs: List<String>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        title = { Text("运行日志", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    items(logs.size) { index ->
                        Text(
                            text = logs[index],
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        if (index < logs.size - 1) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.getLogFilePath()?.let { logPath ->
                            val logFile = File(logPath)
                            if (logFile.exists()) {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "com.callrecord.manager.fileprovider",
                                        logFile
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "应用日志")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "分享日志文件"))
                                    AppLogger.i("日志导出", "日志文件已分享: $logPath")
                                } catch (e: Exception) {
                                    AppLogger.e("日志导出", "导出失败", e)
                                }
                            } else {
                                AppLogger.w("日志导出", "日志文件不存在: $logPath")
                            }
                        } ?: run {
                            AppLogger.w("日志导出", "未找到日志文件路径")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出日志文件")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
