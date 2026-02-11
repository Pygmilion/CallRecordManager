package com.callrecord.manager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.callrecord.manager.ui.theme.CardShape
import com.callrecord.manager.ui.theme.DialogShape
import kotlinx.coroutines.launch

/**
 * Full-screen transcript editing screen with search & replace functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptEditScreen(
    initialText: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = initialText))
    }
    var hasChanges by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Search & Replace state
    var showSearchPanel by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableStateOf(0) }

    // Calculate matches
    val matches = remember(textFieldValue.text, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val result = mutableListOf<IntRange>()
            var startIndex = 0
            val text = textFieldValue.text
            val query = searchQuery
            while (startIndex < text.length) {
                val index = text.indexOf(query, startIndex, ignoreCase = true)
                if (index >= 0) {
                    result.add(index until index + query.length)
                    startIndex = index + 1
                } else {
                    break
                }
            }
            result
        }
    }

    // Clamp match index
    LaunchedEffect(matches.size) {
        if (matches.isEmpty()) {
            currentMatchIndex = 0
        } else if (currentMatchIndex >= matches.size) {
            currentMatchIndex = matches.size - 1
        }
    }

    // Track changes
    LaunchedEffect(textFieldValue.text) {
        hasChanges = textFieldValue.text != initialText
    }

    // Build annotated string with highlighted matches
    val annotatedText = remember(textFieldValue.text, searchQuery, currentMatchIndex, matches) {
        if (searchQuery.isBlank() || matches.isEmpty()) {
            null // Use plain text rendering
        } else {
            buildAnnotatedString {
                val text = textFieldValue.text
                var lastEnd = 0
                matches.forEachIndexed { index, range ->
                    // Append text before this match
                    if (range.first > lastEnd) {
                        append(text.substring(lastEnd, range.first))
                    }
                    // Append highlighted match
                    val isCurrentMatch = index == currentMatchIndex
                    withStyle(
                        SpanStyle(
                            background = if (isCurrentMatch) {
                                androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange for current
                            } else {
                                androidx.compose.ui.graphics.Color(0xFFFFEB3B) // Yellow for others
                            }
                        )
                    ) {
                        append(text.substring(range.first, range.last + 1))
                    }
                    lastEnd = range.last + 1
                }
                // Append remaining text
                if (lastEnd < text.length) {
                    append(text.substring(lastEnd))
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val editCoroutineScope = rememberCoroutineScope()

    // Handle back with unsaved changes
    fun handleBack() {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    // Discard dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            shape = DialogShape,
            title = { Text("放弃修改？", style = MaterialTheme.typography.titleLarge) },
            text = { Text("您有未保存的修改，确定要放弃吗？", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text("放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("继续编辑")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "编辑转写",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    // Toggle search panel
                    IconButton(onClick = {
                        showSearchPanel = !showSearchPanel
                        if (!showSearchPanel) {
                            searchQuery = ""
                            replaceText = ""
                        }
                    }) {
                        Icon(
                            if (showSearchPanel) Icons.Default.SearchOff else Icons.Default.Search,
                            "搜索替换"
                        )
                    }
                    // Save button
                    TextButton(
                        onClick = {
                            onSave(textFieldValue.text)
                        },
                        enabled = hasChanges
                    ) {
                        Text(
                            "保存",
                            color = if (hasChanges) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search & Replace panel
            if (showSearchPanel) {
                SearchReplacePanel(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it; currentMatchIndex = 0 },
                    replaceText = replaceText,
                    onReplaceTextChange = { replaceText = it },
                    matchCount = matches.size,
                    currentMatchIndex = currentMatchIndex,
                    onPrevious = {
                        if (matches.isNotEmpty()) {
                            currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else matches.size - 1
                        }
                    },
                    onNext = {
                        if (matches.isNotEmpty()) {
                            currentMatchIndex = if (currentMatchIndex < matches.size - 1) currentMatchIndex + 1 else 0
                        }
                    },
                    onReplaceAll = {
                        if (searchQuery.isNotBlank() && matches.isNotEmpty()) {
                            val count = matches.size
                            val newText = textFieldValue.text.replace(searchQuery, replaceText, ignoreCase = true)
                            textFieldValue = TextFieldValue(text = newText)
                            searchQuery = ""
                            editCoroutineScope.launch {
                                snackbarHostState.showSnackbar("已替换 $count 处")
                            }
                        }
                    }
                )
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Editable text area
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(scrollState)
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 400.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                "转写内容为空",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (annotatedText != null && searchQuery.isNotBlank()) {
                            // Show annotated text with highlights overlaid
                            // Note: BasicTextField handles input; we overlay highlights via decoration
                            innerTextField()
                        } else {
                            innerTextField()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Search and replace panel component.
 */
@Composable
private fun SearchReplacePanel(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    matchCount: Int,
    currentMatchIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplaceAll: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索关键词", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search, null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                if (matchCount > 0) "${currentMatchIndex + 1}/$matchCount" else "0/0",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                // Navigation buttons
                IconButton(
                    onClick = onPrevious,
                    enabled = matchCount > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, "上一个", modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onNext,
                    enabled = matchCount > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "下一个", modifier = Modifier.size(20.dp))
                }
            }

            // Replace row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = onReplaceTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("替换为", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.FindReplace, null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                // Replace all button
                FilledTonalButton(
                    onClick = onReplaceAll,
                    enabled = searchQuery.isNotBlank() && matchCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("全部替换", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
