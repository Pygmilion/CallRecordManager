package com.callrecord.manager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.callrecord.manager.data.local.AppDatabase
import com.callrecord.manager.data.local.CallRecordEntity
import com.callrecord.manager.data.local.MeetingMinuteEntity
import com.callrecord.manager.data.remote.ApiClient
import com.callrecord.manager.data.repository.ApiKeyProvider
import com.callrecord.manager.data.repository.CallRecordRepository
import com.callrecord.manager.ui.screen.*
import com.callrecord.manager.ui.theme.CallRecordManagerTheme
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: MainViewModel
    
    // 文件选择器
    private val audioFilePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedAudio(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化日志系统
        val logDir = File(getExternalFilesDir(null), "Logs")
        com.callrecord.manager.utils.AppLogger.init(logDir)
        com.callrecord.manager.utils.AppLogger.i("MainActivity", "应用启动")
        
        // Create notification channel
        com.callrecord.manager.utils.TaskNotificationHelper.createChannel(applicationContext)
        // Clear stale progress notification from previous process/work restarts.
        com.callrecord.manager.utils.TaskNotificationHelper.cancelProgressNotification(applicationContext)
        
        // 初始化数据库
        val database = AppDatabase.getDatabase(applicationContext)
        
        // Initialize API Key provider
        ApiKeyProvider.init(applicationContext)
        
        // 初始化 API 服务 (API Key is now fetched dynamically per-request)
        val apiService = ApiClient.createStepFunService { ApiKeyProvider.getApiKey() }
        
        // 初始化仓库
        val repository = CallRecordRepository(
            context = applicationContext,
            callRecordDao = database.callRecordDao(),
            transcriptDao = database.transcriptDao(),
            meetingMinuteDao = database.meetingMinuteDao(),
            apiService = apiService
        )

        setContent {
            CallRecordManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val vm: MainViewModel = viewModel(
                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return MainViewModel(repository, applicationContext) as T
                            }
                        }
                    )
                    
                    // 保存 viewModel 引用
                    viewModel = vm
                    
                    // 处理启动时的分享文件
                    LaunchedEffect(Unit) {
                        checkSharedAudio(intent)
                    }
                    
                    MainApp(
                        viewModel = vm,
                        onPickAudioFile = { pickAudioFile() },
                        onCopySharedFiles = { pendingFiles, tier, contactName ->
                            copyAndImportSharedFiles(
                                pendingFiles = pendingFiles,
                                tier = tier,
                                contactName = contactName
                            )
                        }
                    )
                }
            }
        }
    }
    
    /**
     * 打开文件选择器
     */
    fun pickAudioFile() {
        audioFilePicker.launch("audio/*")
    }
    
    /**
     * Handle audio selected from file picker (existing flow, no receive screen)
     */
    private fun handleSelectedAudio(uri: Uri) {
        try {
            val fileName = getFileName(uri) ?: "audio_${System.currentTimeMillis()}.m4a"
            val fileSize = getFileSize(uri)

            // Show the AudioReceiveScreen instead of importing directly
            val suggestedContact = extractContactFromFileName(fileName)
            viewModel.setPendingShareFiles(
                listOf(
                    PendingShareFile(
                        uri = uri,
                        fileName = fileName,
                        fileSize = fileSize,
                        suggestedContactName = suggestedContact
                    )
                )
            )
        } catch (e: Exception) {
            com.callrecord.manager.utils.AppLogger.e("MainActivity", "处理选中文件失败", e)
        }
    }

    /**
     * Copy shared files to app directory and import with chosen options.
     * Called from MainApp composable via AudioReceiveScreen confirm.
     */
    fun copyAndImportSharedFiles(
        pendingFiles: List<PendingShareFile>,
        tier: AudioImportTier,
        contactName: String?
    ) {
        try {
            val destDir = File(getExternalFilesDir(null), "ImportedRecordings")
            if (!destDir.exists()) destDir.mkdirs()

            val copiedFiles = mutableListOf<LocalImportAudioFile>()
            val failedFiles = mutableListOf<String>()

            pendingFiles.forEach { pending ->
                runCatching {
                    val destFile = createUniqueDestinationFile(destDir, pending.fileName)
                    contentResolver.openInputStream(pending.uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalStateException("无法读取文件内容")
                    copiedFiles.add(
                        LocalImportAudioFile(
                            filePath = destFile.absolutePath,
                            fileName = destFile.name
                        )
                    )
                }.onFailure { error ->
                    com.callrecord.manager.utils.AppLogger.e(
                        "MainActivity",
                        "复制分享文件失败: ${pending.fileName}",
                        error
                    )
                    failedFiles.add(pending.fileName)
                }
            }

            if (copiedFiles.isEmpty()) {
                viewModel.setShareError("导入失败：未能复制任何文件")
                return
            }
            viewModel.importBatchWithOptions(
                files = copiedFiles,
                tier = tier,
                contactName = contactName,
                copyFailedFileNames = failedFiles
            )
        } catch (e: Exception) {
            com.callrecord.manager.utils.AppLogger.e("MainActivity", "复制分享文件失败", e)
            viewModel.setShareError("导入失败: ${e.message}")
        }
    }
    
    /**
     * Check for shared audio in intent and show receive screen
     */
    private fun checkSharedAudio(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val mimeType = intent.type.orEmpty()

        if (action == Intent.ACTION_SEND) {
            extractSingleShareUri(intent)?.let { uri ->
                if (isAudioUri(uri, mimeType)) {
                    com.callrecord.manager.utils.AppLogger.i("MainActivity", "接收到分享的音频: $uri")
                    showReceiveScreen(listOf(uri))
                }
            }
        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = extractMultipleShareUris(intent)
            val audioUris = uris.filter { uri -> isAudioUri(uri, mimeType) }
            if (audioUris.isNotEmpty()) {
                com.callrecord.manager.utils.AppLogger.i("MainActivity", "接收到批量分享音频: ${audioUris.size} 个")
                showReceiveScreen(audioUris)
            }
        } else if (action == Intent.ACTION_VIEW && intent.data != null) {
            intent.data?.let { uri ->
                com.callrecord.manager.utils.AppLogger.i("MainActivity", "打开音频文件: $uri")
                showReceiveScreen(listOf(uri))
            }
        }
    }

    /**
     * Show the AudioReceiveScreen for shared URI list.
     */
    private fun showReceiveScreen(uris: List<Uri>) {
        try {
            val pendingFiles = uris.map { uri ->
                val fileName = getFileName(uri) ?: "audio_${System.currentTimeMillis()}.m4a"
                val fileSize = getFileSize(uri)
                val suggestedContact = extractContactFromFileName(fileName)
                PendingShareFile(
                    uri = uri,
                    fileName = fileName,
                    fileSize = fileSize,
                    suggestedContactName = suggestedContact
                )
            }

            if (::viewModel.isInitialized) {
                viewModel.setPendingShareFiles(pendingFiles)
            }
        } catch (e: Exception) {
            com.callrecord.manager.utils.AppLogger.e("MainActivity", "准备接收界面失败", e)
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkSharedAudio(intent)
    }
    
    /**
     * 获取文件名
     */
    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    /**
     * Get file size from Uri
     */
    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) {
                size = cursor.getLong(sizeIndex)
            }
        }
        return size
    }

    /**
     * Extract contact name from file name (same logic as parseFileName in repository)
     */
    private fun extractContactFromFileName(fileName: String): String? {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val separatorIndex = nameWithoutExt.indexOfLast { it == '_' || it == '-' || it == ' ' }
        if (separatorIndex > 0) {
            val contactPart = nameWithoutExt.substring(0, separatorIndex).trim()
            // Don't suggest if it looks like a phone number
            if (!contactPart.all { it.isDigit() || it == '-' || it == ' ' }) {
                return contactPart
            }
        }
        return null
    }

    private fun createUniqueDestinationFile(destDir: File, originName: String): File {
        val safeName = originName.ifBlank { "audio_${System.currentTimeMillis()}.m4a" }
        var target = File(destDir, safeName)
        if (!target.exists()) {
            return target
        }

        val base = safeName.substringBeforeLast(".", safeName)
        val ext = safeName.substringAfterLast(".", "")
        var index = 1
        while (target.exists()) {
            val candidateName = if (ext.isBlank()) {
                "${base}_$index"
            } else {
                "${base}_$index.$ext"
            }
            target = File(destDir, candidateName)
            index++
        }
        return target
    }

    private fun extractSingleShareUri(intent: Intent): Uri? {
        val fromExtra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (fromExtra != null) return fromExtra
        val clipData = intent.clipData
        if (clipData != null && clipData.itemCount > 0) {
            return clipData.getItemAt(0)?.uri
        }
        return intent.data
    }

    private fun extractMultipleShareUris(intent: Intent): List<Uri> {
        val fromExtras = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?.filterNotNull()
                .orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                ?.filterNotNull()
                .orEmpty()
        }
        if (fromExtras.isNotEmpty()) {
            return fromExtras
        }

        val clipData = intent.clipData ?: return emptyList()
        return buildList {
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index)?.uri?.let { add(it) }
            }
        }
    }

    private fun isAudioUri(uri: Uri, intentMimeType: String): Boolean {
        if (intentMimeType.startsWith("audio/")) {
            return true
        }
        val resolvedType = contentResolver.getType(uri).orEmpty()
        if (resolvedType.startsWith("audio/")) {
            return true
        }
        val extension = getFileName(uri)
            ?.substringAfterLast('.', "")
            ?.lowercase()
            .orEmpty()
        return extension in setOf("mp3", "m4a", "wav", "amr", "3gp", "aac", "ogg", "flac")
    }
}

@Composable
fun MainApp(
    viewModel: MainViewModel,
    onPickAudioFile: () -> Unit,
    onCopySharedFiles: (List<PendingShareFile>, AudioImportTier, String?) -> Unit = { _, _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedRecord by remember { mutableStateOf<CallRecordEntity?>(null) }
    var selectedMinute by remember { mutableStateOf<MeetingMinuteEntity?>(null) }
    var showTimelineBrief by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // Transcript edit state
    var editingTranscriptId by remember { mutableStateOf<Long?>(null) }
    var editingTranscriptText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    // Audio receive screen
    val pendingFiles by viewModel.pendingShareFiles.collectAsState()
    val shareError by viewModel.shareError.collectAsState()

    // Timeline brief state
    val timelineBriefResult by viewModel.timelineBriefResult.collectAsState()
    val isGeneratingBrief by viewModel.isGeneratingBrief.collectAsState()

    // BackHandler for AudioReceiveScreen
    BackHandler(enabled = pendingFiles.isNotEmpty()) {
        viewModel.clearPendingShareFiles()
    }

    if (pendingFiles.isNotEmpty()) {
        AudioReceiveScreen(
            pendingFiles = pendingFiles,
            errorMessage = shareError,
            onConfirm = { tier, contactName ->
                onCopySharedFiles(pendingFiles, tier, contactName)
            },
            onDismiss = { viewModel.clearPendingShareFiles() }
        )
    }
    
    // Detail screens
    // BackHandler for RecordDetailScreen
    BackHandler(enabled = selectedRecord != null) {
        selectedRecord = null
    }

    if (selectedRecord != null) {
        // Check if we're editing a transcript
        if (editingTranscriptId != null) {
            BackHandler(enabled = true) {
                editingTranscriptId = null
                editingTranscriptText = ""
            }
            TranscriptEditScreen(
                initialText = editingTranscriptText,
                onSave = { editedText ->
                    viewModel.saveEditedTranscript(editingTranscriptId!!, editedText) { success ->
                        if (success) {
                            editingTranscriptId = null
                            editingTranscriptText = ""
                        }
                    }
                },
                onBack = {
                    editingTranscriptId = null
                    editingTranscriptText = ""
                }
            )
            return
        }

        RecordDetailScreen(
            record = selectedRecord!!,
            viewModel = viewModel,
            onBack = { selectedRecord = null },
            onEditTranscript = { transcriptId, currentText ->
                editingTranscriptId = transcriptId
                editingTranscriptText = currentText
            },
            onRegenerateMinute = { record ->
                viewModel.retryGenerateMinute(record)
            }
        )
        return
    }
    
    // BackHandler for MinuteDetailScreen
    BackHandler(enabled = selectedMinute != null) {
        selectedMinute = null
    }

    if (selectedMinute != null) {
        MinuteDetailScreen(
            minute = selectedMinute!!,
            viewModel = viewModel,
            onBack = { selectedMinute = null }
        )
        return
    }

    // BackHandler for TimelineBriefScreen
    BackHandler(enabled = showTimelineBrief) {
        showTimelineBrief = false
    }

    if (showTimelineBrief) {
        TimelineBriefScreen(
            briefResult = timelineBriefResult,
            isLoading = isGeneratingBrief,
            onBack = { showTimelineBrief = false }
        )
        return
    }

    // BackHandler for SettingsScreen
    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Custom brand navigation bar
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = {
                        Icon(
                            if (selectedTab == 0) Icons.Filled.Phone else Icons.Outlined.Phone,
                            contentDescription = null
                        )
                    },
                    label = {
                        Text(
                            "录音",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            if (selectedTab == 1) Icons.Filled.Description else Icons.Outlined.Description,
                            contentDescription = null
                        )
                    },
                    label = {
                        Text(
                            "纪要",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            // Tab content with Crossfade transition
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(300),
                label = "tab_crossfade"
            ) { tab ->
                when (tab) {
                    0 -> RecordListScreen(
                        viewModel = viewModel,
                        onRecordClick = { selectedRecord = it },
                        onPickAudioFile = onPickAudioFile,
                        onSettingsClick = { showSettings = true },
                        onViewMinute = { record ->
                            coroutineScope.launch {
                                val minute = viewModel.getMinuteForRecord(record)
                                if (minute != null) {
                                    selectedMinute = minute
                                }
                            }
                        }
                    )
                    1 -> MinuteListScreen(
                        viewModel = viewModel,
                        onMinuteClick = { selectedMinute = it },
                        onSettingsClick = { showSettings = true },
                        onGenerateTimelineBrief = { selectedMinutes ->
                            viewModel.generateTimelineBrief(selectedMinutes)
                            showTimelineBrief = true
                        }
                    )
                }
            }
        }
    }
}
