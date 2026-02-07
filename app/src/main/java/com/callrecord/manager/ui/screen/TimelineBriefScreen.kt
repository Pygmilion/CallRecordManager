package com.callrecord.manager.ui.screen

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callrecord.manager.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data model for a timeline brief result from LLM
 */
data class TimelineBriefResult(
    val title: String = "",
    val timelineNodes: List<TimelineNode> = emptyList(),
    val trendAnalysis: String = "",
    val currentStatus: String = "",
    val followUpSuggestions: List<String> = emptyList()
)

data class TimelineNode(
    val date: String = "",
    val summary: String = ""
)

/**
 * Timeline Brief screen – redesigned with proper timeline visual layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineBriefScreen(
    briefResult: TimelineBriefResult,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("脉络简报", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    if (!isLoading && briefResult.title.isNotEmpty()) {
                        IconButton(onClick = {
                            val exportText = buildExportText(briefResult)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, briefResult.title)
                                putExtra(Intent.EXTRA_TEXT, exportText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "导出简报"))
                        }) {
                            Icon(Icons.Outlined.Share, "导出")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "正在分析多条纪要，生成脉络简报...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (briefResult.title.isEmpty()) {
                BrandEmptyState(
                    icon = Icons.Outlined.Timeline,
                    message = "暂无简报内容",
                    hint = "选择多条纪要后可生成事件脉络简报"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CardShape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Icon(
                                    Icons.Outlined.Timeline, null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    briefResult.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Timeline section header
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.DateRange, null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "时间线概览",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // Timeline nodes with proper visual timeline
                    itemsIndexed(briefResult.timelineNodes) { index, node ->
                        BrandTimelineNode(
                            node = node,
                            isFirst = index == 0,
                            isLast = index == briefResult.timelineNodes.lastIndex
                        )
                    }

                    // Trend analysis
                    if (briefResult.trendAnalysis.isNotBlank()) {
                        item {
                            SectionCard(
                                icon = Icons.Outlined.TrendingUp,
                                title = "发展趋势分析",
                                accentColor = StatusColors.info
                            ) {
                                Text(
                                    briefResult.trendAnalysis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Current status
                    if (briefResult.currentStatus.isNotBlank()) {
                        item {
                            SectionCard(
                                icon = Icons.Outlined.PushPin,
                                title = "当前最新状态",
                                accentColor = StatusColors.warning
                            ) {
                                Text(
                                    briefResult.currentStatus,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Follow-up suggestions
                    if (briefResult.followUpSuggestions.isNotEmpty()) {
                        item {
                            SectionCard(
                                icon = Icons.Outlined.Lightbulb,
                                title = "后续建议",
                                accentColor = StatusColors.success
                            ) {
                                briefResult.followUpSuggestions.forEachIndexed { idx, suggestion ->
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(22.dp),
                                            shape = StatusTagShape,
                                            color = StatusColors.successContainer
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    "${idx + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = StatusColors.success,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            suggestion,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Brand timeline node with left vertical line + dot + content card
 */
@Composable
private fun BrandTimelineNode(
    node: TimelineNode,
    isFirst: Boolean,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Timeline column: line + dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
        ) {
            // Top line segment (hidden for first node)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .background(
                        if (isFirst) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
            )
            // Dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            // Bottom line segment (hidden for last node)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(
                        if (isLast) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Content card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    node.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    node.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun buildExportText(brief: TimelineBriefResult): String {
    return buildString {
        appendLine("# ${brief.title}")
        appendLine()
        appendLine("## 时间线概览")
        brief.timelineNodes.forEach { node ->
            appendLine("- **${node.date}**: ${node.summary}")
        }
        appendLine()
        if (brief.trendAnalysis.isNotBlank()) {
            appendLine("## 发展趋势分析")
            appendLine(brief.trendAnalysis)
            appendLine()
        }
        if (brief.currentStatus.isNotBlank()) {
            appendLine("## 当前最新状态")
            appendLine(brief.currentStatus)
            appendLine()
        }
        if (brief.followUpSuggestions.isNotEmpty()) {
            appendLine("## 后续建议/待办")
            brief.followUpSuggestions.forEachIndexed { idx, s ->
                appendLine("${idx + 1}. $s")
            }
        }
    }
}
