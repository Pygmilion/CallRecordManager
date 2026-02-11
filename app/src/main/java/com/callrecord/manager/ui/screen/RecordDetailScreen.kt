package com.callrecord.manager.ui.screen

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callrecord.manager.data.local.CallRecordEntity
import com.callrecord.manager.data.local.SpeakerSegment
import com.callrecord.manager.data.local.TranscriptEntity
import com.callrecord.manager.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Recording detail screen – redesigned with modern audio player & chat-bubble transcript
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    record: CallRecordEntity,
    viewModel: MainViewModel? = null,
    onBack: () -> Unit,
    onEditTranscript: ((transcriptId: Long, currentText: String) -> Unit)? = null,
    onRegenerateMinute: ((record: CallRecordEntity) -> Unit)? = null
) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    // Load transcript
    var transcript by remember { mutableStateOf<TranscriptEntity?>(null) }
    LaunchedEffect(record.id) {
        viewModel?.let { transcript = it.getTranscriptForRecord(record.id) }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Init player
    LaunchedEffect(record.filePath) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(record.filePath)
                prepare()
                duration = this.duration
            }
        } catch (_: Exception) {}
    }

    // Progress updater
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { currentPosition = it.currentPosition }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    // Current active segment
    val currentSegmentIndex = remember(currentPosition, transcript) {
        val segments = transcript?.speakers ?: emptyList()
        val posSeconds = currentPosition / 1000.0
        segments.indexOfLast { it.startTime <= posSeconds }
    }

    // Auto-scroll
    LaunchedEffect(currentSegmentIndex) {
        if (currentSegmentIndex >= 0 && isPlaying) {
            coroutineScope.launch {
                listState.animateScrollToItem(currentSegmentIndex + 2)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        record.contactName ?: "录音详情",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        mediaPlayer?.stop()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Info card
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
                        // Contact row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        (record.contactName ?: "?").take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    record.contactName ?: record.phoneNumber ?: "未知联系人",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    dateFormat.format(Date(record.recordTime)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Meta info chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InfoChip(
                                icon = Icons.Outlined.Timer,
                                text = formatRecordDuration(record.duration)
                            )
                            InfoChip(
                                icon = Icons.Outlined.FolderOpen,
                                text = "${record.fileSize / 1024} KB"
                            )
                            if (transcript != null) {
                                InfoChip(
                                    icon = Icons.Outlined.RecordVoiceOver,
                                    text = "${transcript!!.speakers.size} 段",
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            // Modern audio player card
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
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Custom progress bar
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { newValue ->
                                mediaPlayer?.seekTo(newValue.toInt())
                                currentPosition = newValue.toInt()
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(currentPosition),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatTime(duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Play controls
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rewind 10s
                            IconButton(
                                onClick = {
                                    mediaPlayer?.let {
                                        val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                                        it.seekTo(newPos)
                                        currentPosition = newPos
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Replay10, "后退10秒",
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Play / Pause – large circular button
                            FilledIconButton(
                                onClick = {
                                    mediaPlayer?.let { player ->
                                        if (isPlaying) {
                                            player.pause()
                                            isPlaying = false
                                        } else {
                                            player.start()
                                            isPlaying = true
                                            player.setOnCompletionListener {
                                                isPlaying = false
                                                currentPosition = 0
                                                player.seekTo(0)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(64.dp),
                                shape = PlayerButtonShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "暂停" else "播放",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Forward 10s
                            IconButton(
                                onClick = {
                                    mediaPlayer?.let {
                                        val newPos = (it.currentPosition + 10000).coerceAtMost(duration)
                                        it.seekTo(newPos)
                                        currentPosition = newPos
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Forward10, "前进10秒",
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Action buttons: Edit transcript & Regenerate minute
            if (transcript != null && transcript!!.fullText.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Edit transcript button
                        if (onEditTranscript != null) {
                            OutlinedButton(
                                onClick = {
                                    onEditTranscript(transcript!!.id, transcript!!.fullText)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Outlined.Edit, null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("编辑转写")
                            }
                        }
                        // Regenerate minute button
                        if (onRegenerateMinute != null) {
                            OutlinedButton(
                                onClick = {
                                    onRegenerateMinute(record)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome, null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("重新生成纪要")
                            }
                        }
                    }
                }
            }

            // Transcript section
            val segments = transcript?.speakers ?: emptyList()
            if (segments.isNotEmpty()) {
                // Section header
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.RecordVoiceOver, null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "语音转录 (${segments.size} 段)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                // Chat-bubble style segments
                items(segments.size) { index ->
                    val segment = segments[index]
                    val isActive = index == currentSegmentIndex
                    val isLeftSpeaker = segment.speaker.contains("1") || segment.speaker.lowercase().contains("a")

                    ChatBubbleSegment(
                        segment = segment,
                        isActive = isActive,
                        isLeft = isLeftSpeaker,
                        onClick = {
                            val targetMs = (segment.startTime * 1000).toInt()
                            mediaPlayer?.seekTo(targetMs)
                            currentPosition = targetMs
                        }
                    )
                }
            } else if (transcript != null && transcript!!.fullText.isNotBlank()) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.RecordVoiceOver, null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("语音转录", style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                transcript!!.fullText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Info chip for metadata display
 */
@Composable
fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        shape = StatusTagShape,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

/**
 * Chat-bubble style transcript segment
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBubbleSegment(
    segment: SpeakerSegment,
    isActive: Boolean,
    isLeft: Boolean,
    onClick: () -> Unit
) {
    val bubbleColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isLeft -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
    }
    val textColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val bubbleShape = if (isLeft) {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End
    ) {
        if (!isLeft) Spacer(modifier = Modifier.weight(0.15f))

        Card(
            onClick = onClick,
            modifier = Modifier.weight(0.85f),
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        segment.speaker,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        formatSegmentTimeRange(segment.startTime, segment.endTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    segment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                )
            }
        }

        if (isLeft) Spacer(modifier = Modifier.weight(0.15f))
    }
}

private fun formatTime(milliseconds: Int): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}

private fun formatRecordDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}

private fun formatSegmentTimeRange(startTime: Double, endTime: Double): String {
    val startMin = (startTime / 60).toInt()
    val startSec = (startTime % 60).toInt()
    val endMin = (endTime / 60).toInt()
    val endSec = (endTime % 60).toInt()
    return String.format("%02d:%02d-%02d:%02d", startMin, startSec, endMin, endSec)
}
