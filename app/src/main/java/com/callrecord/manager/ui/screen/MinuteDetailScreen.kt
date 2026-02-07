package com.callrecord.manager.ui.screen

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.callrecord.manager.data.local.MeetingMinuteEntity
import com.callrecord.manager.data.local.Priority
import com.callrecord.manager.ui.theme.*
import com.callrecord.manager.utils.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Minute detail screen – redesigned with colored side bars, priority tags, collapsible sections
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuteDetailScreen(
    minute: MeetingMinuteEntity,
    viewModel: MainViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showTranscript by remember { mutableStateOf(false) }

    var transcript by remember { mutableStateOf<com.callrecord.manager.data.local.TranscriptEntity?>(null) }
    LaunchedEffect(minute.transcriptId) {
        viewModel?.let { transcript = it.getTranscriptById(minute.transcriptId) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("会谈纪要", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Outlined.Share, "导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Title card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            minute.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "创建时间: ${dateFormat.format(Date(minute.createTime))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Summary section with colored side bar
            item {
                SectionCard(
                    icon = Icons.Outlined.Description,
                    title = "摘要",
                    accentColor = StatusColors.info
                ) {
                    Text(
                        minute.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Key points
            if (minute.keyPoints.isNotEmpty()) {
                item {
                    SectionCard(
                        icon = Icons.Outlined.FormatListNumbered,
                        title = "关键要点",
                        accentColor = BrandAccent
                    ) {
                        minute.keyPoints.forEachIndexed { index, point ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    modifier = Modifier.size(22.dp),
                                    shape = StatusTagShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    point,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Participants
            if (minute.participants.isNotEmpty()) {
                item {
                    SectionCard(
                        icon = Icons.Outlined.People,
                        title = "参与者",
                    accentColor = StatusColors.pending
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            minute.participants.forEach { participant ->
                                Surface(
                                    shape = StatusTagShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.Person, null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            participant.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        participant.role?.let {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "· $it",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Action items with priority colors
            if (minute.actionItems.isNotEmpty()) {
                item {
                    SectionCard(
                        icon = Icons.Outlined.CheckCircle,
                        title = "待办事项",
                    accentColor = StatusColors.success
                    ) {
                        minute.actionItems.forEachIndexed { index, item ->
                            val priorityColor = when (item.priority) {
                                Priority.URGENT -> StatusColors.error
                                Priority.HIGH -> StatusColors.warning
                                Priority.MEDIUM -> StatusColors.info
                                Priority.LOW -> StatusColors.success
                            }
                            val priorityBgColor = when (item.priority) {
                                Priority.URGENT -> StatusColors.errorContainer
                                Priority.HIGH -> StatusColors.warningContainer
                                Priority.MEDIUM -> StatusColors.infoContainer
                                Priority.LOW -> StatusColors.successContainer
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = CardShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(modifier = Modifier.padding(12.dp)) {
                                    // Priority indicator bar
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(48.dp)
                                            .clip(CardShape)
                                            .background(priorityColor)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.description,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            // Priority tag
                                            Surface(
                                                shape = StatusTagShape,
                                                color = priorityBgColor
                                            ) {
                                                Text(
                                                    item.priority.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = priorityColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                            item.assignee?.let { assignee ->
                                                Surface(
                                                    shape = StatusTagShape,
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        assignee,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (index < minute.actionItems.size - 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // Full content
            item {
                SectionCard(
                    icon = Icons.Outlined.Article,
                    title = "完整内容",
                    accentColor = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        minute.fullContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ASR transcript (collapsible)
            if (transcript != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(20.dp)
                                            .clip(CardShape)
                                            .background(MaterialTheme.colorScheme.secondary)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Icon(
                                        Icons.Outlined.RecordVoiceOver, null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("语音转录原文", style = MaterialTheme.typography.titleSmall)
                                }
                                IconButton(onClick = { showTranscript = !showTranscript }) {
                                    Icon(
                                        if (showTranscript) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showTranscript) "收起" else "展开"
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = showTranscript,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    val speakerSegments = transcript!!.speakers
                                    if (speakerSegments.isNotEmpty()) {
                                        speakerSegments.forEach { segment ->
                                            val timeStr = formatSegmentTime(segment.startTime)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Text(
                                                    "[$timeStr] ${segment.speaker}:",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.width(140.dp)
                                                )
                                                Text(
                                                    segment.text,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Divider(
                                                modifier = Modifier.padding(vertical = 2.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                            )
                                        }
                                    } else {
                                        Text(
                                            transcript!!.fullText,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            if (!showTranscript) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "点击展开查看完整语音转录原文（${transcript!!.speakers.size} 个语句段落）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Export dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                shape = DialogShape,
                title = { Text("导出纪要", style = MaterialTheme.typography.titleLarge) },
                text = { Text("选择导出格式") },
                confirmButton = {
                    TextButton(onClick = {
                        val filePath = exportMinute(context, minute, "md")
                        showExportDialog = false
                        shareMinuteFile(context, filePath, "Markdown")
                    }) {
                        Text("Markdown (.md)")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val filePath = exportMinute(context, minute, "txt")
                        showExportDialog = false
                        shareMinuteFile(context, filePath, "文本")
                    }) {
                        Text("文本 (.txt)")
                    }
                }
            )
        }
    }
}

/**
 * Reusable section card with colored left accent bar
 */
@Composable
fun SectionCard(
    icon: ImageVector,
    title: String,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Colored accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon, null,
                        modifier = Modifier.size(20.dp),
                        tint = accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                content()
            }
        }
    }
}

// ===== Export helper functions (unchanged logic, kept as-is) =====

private fun exportMinute(context: Context, minute: MeetingMinuteEntity, format: String): String {
    return try {
        val minutesDir = File(context.getExternalFilesDir(null), "Minutes")
        if (!minutesDir.exists()) minutesDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${minute.title}_$timestamp.$format"
        val file = File(minutesDir, fileName)

        val content = if (format == "md") buildMarkdownContent(minute) else buildTextContent(minute)
        file.writeText(content, Charsets.UTF_8)
        AppLogger.i("导出纪要", "已导出: ${file.absolutePath}")
        file.absolutePath
    } catch (e: Exception) {
        AppLogger.e("导出纪要", "导出失败", e)
        ""
    }
}

private fun buildMarkdownContent(minute: MeetingMinuteEntity): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return buildString {
        appendLine("# ${minute.title}")
        appendLine()
        appendLine("**创建时间**: ${dateFormat.format(Date(minute.createTime))}")
        appendLine()
        appendLine("## 摘要")
        appendLine()
        appendLine(minute.summary)
        appendLine()
        if (minute.keyPoints.isNotEmpty()) {
            appendLine("## 关键要点")
            appendLine()
            minute.keyPoints.forEachIndexed { index, point -> appendLine("${index + 1}. $point") }
            appendLine()
        }
        if (minute.participants.isNotEmpty()) {
            appendLine("## 参与者")
            appendLine()
            minute.participants.forEach { p ->
                val role = p.role?.let { " - $it" } ?: ""
                appendLine("- ${p.name}$role")
            }
            appendLine()
        }
        if (minute.actionItems.isNotEmpty()) {
            appendLine("## 待办事项")
            appendLine()
            minute.actionItems.forEach { item ->
                appendLine("### ${item.description}")
                item.assignee?.let { appendLine("- 负责人: $it") }
                item.deadline?.let { appendLine("- 截止日期: $it") }
                appendLine("- 优先级: ${item.priority.name}")
                appendLine()
            }
        }
        appendLine("## 完整内容")
        appendLine()
        appendLine(minute.fullContent)
    }
}

private fun buildTextContent(minute: MeetingMinuteEntity): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return buildString {
        appendLine("=".repeat(50))
        appendLine(minute.title)
        appendLine("=".repeat(50))
        appendLine()
        appendLine("创建时间: ${dateFormat.format(Date(minute.createTime))}")
        appendLine()
        appendLine("-".repeat(50))
        appendLine("摘要")
        appendLine("-".repeat(50))
        appendLine(minute.summary)
        appendLine()
        if (minute.keyPoints.isNotEmpty()) {
            appendLine("-".repeat(50))
            appendLine("关键要点")
            appendLine("-".repeat(50))
            minute.keyPoints.forEachIndexed { index, point -> appendLine("${index + 1}. $point") }
            appendLine()
        }
        if (minute.participants.isNotEmpty()) {
            appendLine("-".repeat(50))
            appendLine("参与者")
            appendLine("-".repeat(50))
            minute.participants.forEach { p ->
                val role = p.role?.let { " - $it" } ?: ""
                appendLine("${p.name}$role")
            }
            appendLine()
        }
        if (minute.actionItems.isNotEmpty()) {
            appendLine("-".repeat(50))
            appendLine("待办事项")
            appendLine("-".repeat(50))
            minute.actionItems.forEach { item ->
                appendLine("【${item.description}】")
                item.assignee?.let { appendLine("  负责人: $it") }
                item.deadline?.let { appendLine("  截止日期: $it") }
                appendLine("  优先级: ${item.priority.name}")
                appendLine()
            }
        }
        appendLine("-".repeat(50))
        appendLine("完整内容")
        appendLine("-".repeat(50))
        appendLine(minute.fullContent)
    }
}

private fun shareMinuteFile(context: Context, filePath: String, formatName: String) {
    try {
        if (filePath.isEmpty()) return
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "com.callrecord.manager.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "会谈纪要 - ${file.nameWithoutExtension}")
            putExtra(Intent.EXTRA_TEXT, "已导出为${formatName}格式")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享纪要"))
    } catch (e: Exception) {
        AppLogger.e("分享纪要", "分享失败", e)
    }
}

private fun formatSegmentTime(seconds: Double): String {
    val totalSecs = seconds.toInt()
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%02d:%02d", mins, secs)
}
