package com.callrecord.manager.ui.screen

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.callrecord.manager.ui.theme.*

/**
 * Operation tiers for shared audio file processing
 */
enum class AudioImportTier(val label: String, val description: String) {
    STORE_ONLY("仅存储", "将文件保存到应用目录"),
    STORE_AND_TRANSCRIBE("存储并转写", "保存文件后自动进行语音转写"),
    FULL_PIPELINE("存储、转写并生成纪要", "完整流水线：保存→转写→生成会谈纪要")
}

/**
 * Data class holding pending shared file information
 */
data class PendingShareFile(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val suggestedContactName: String? = null
)

/**
 * Audio receive screen – redesigned with brand styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioReceiveScreen(
    pendingFile: PendingShareFile,
    errorMessage: String?,
    onConfirm: (tier: AudioImportTier, contactName: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTier by remember { mutableStateOf(AudioImportTier.FULL_PIPELINE) }
    var contactName by remember { mutableStateOf(pendingFile.suggestedContactName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.GraphicEq, null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "接收音频文件",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // File info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.AudioFile, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                pendingFile.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "大小: ${formatFileSize(pendingFile.fileSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Operation tier selection
                Text(
                    "处理方式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.selectableGroup()) {
                    AudioImportTier.values().forEach { tier ->
                        val isSelected = selectedTier == tier
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = CardShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface,
                            onClick = { selectedTier = tier }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        tier.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Text(
                                        tier.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Contact name input
                Text(
                    "联系人（可选）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    label = { Text("联系人名称") },
                    placeholder = { Text("输入或留空") },
                    singleLine = true,
                    shape = SearchBarShape,
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, null)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Error message
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.ErrorOutline, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedTier, contactName.ifBlank { null }) },
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("确认导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}
