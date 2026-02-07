package com.callrecord.manager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.callrecord.manager.data.local.AppDatabase
import com.callrecord.manager.data.local.CallRecordEntity
import com.callrecord.manager.data.local.MeetingMinuteEntity
import com.callrecord.manager.data.remote.ApiClient
import com.callrecord.manager.data.repository.CallRecordRepository
import com.callrecord.manager.ui.screen.*
import com.callrecord.manager.ui.theme.CallRecordManagerTheme
import com.callrecord.manager.ui.theme.DialogShape
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
        
        // 初始化数据库
        val database = AppDatabase.getDatabase(applicationContext)
        
        // 初始化 API 服务
        val apiKey = BuildConfig.STEPFUN_API_KEY
        val apiService = ApiClient.createStepFunService(apiKey)
        
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
                                return MainViewModel(repository, apiKey, applicationContext) as T
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
                        onCopySharedFile = { uri, tier, contactName -> copyAndImportSharedFile(uri, tier, contactName) }
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
            viewModel.setPendingShareFile(
                PendingShareFile(
                    uri = uri,
                    fileName = fileName,
                    fileSize = fileSize,
                    suggestedContactName = suggestedContact
                )
            )
        } catch (e: Exception) {
            com.callrecord.manager.utils.AppLogger.e("MainActivity", "处理选中文件失败", e)
        }
    }

    /**
     * Copy shared file to app directory and import with chosen options.
     * Called from MainApp composable via AudioReceiveScreen confirm.
     */
    fun copyAndImportSharedFile(uri: Uri, tier: AudioImportTier, contactName: String?) {
        try {
            val fileName = getFileName(uri) ?: "audio_${System.currentTimeMillis()}.m4a"

            val destDir = File(getExternalFilesDir(null), "ImportedRecordings")
            if (!destDir.exists()) destDir.mkdirs()

            val destFile = File(destDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            viewModel.importWithOptions(destFile.absolutePath, fileName, tier, contactName)
        } catch (e: Exception) {
            com.callrecord.manager.utils.AppLogger.e("MainActivity", "复制分享文件失败", e)
            viewModel.clearPendingShareFile()
        }
    }
    
    /**
     * Check for shared audio in intent and show receive screen
     */
    private fun checkSharedAudio(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                com.callrecord.manager.utils.AppLogger.i("MainActivity", "接收到分享的音频: $uri")
                showReceiveScreen(uri)
            }
        } else if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            intent.data?.let { uri ->
                com.callrecord.manager.utils.AppLogger.i("MainActivity", "打开音频文件: $uri")
                showReceiveScreen(uri)
            }
        }
    }

    /**
     * Show the AudioReceiveScreen for a shared URI
     */
    private fun showReceiveScreen(uri: Uri) {
        try {
            val fileName = getFileName(uri) ?: "audio_${System.currentTimeMillis()}.m4a"
            val fileSize = getFileSize(uri)
            val suggestedContact = extractContactFromFileName(fileName)

            if (::viewModel.isInitialized) {
                viewModel.setPendingShareFile(
                    PendingShareFile(
                        uri = uri,
                        fileName = fileName,
                        fileSize = fileSize,
                        suggestedContactName = suggestedContact
                    )
                )
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
}

@Composable
fun MainApp(
    viewModel: MainViewModel,
    onPickAudioFile: () -> Unit,
    onCopySharedFile: (Uri, AudioImportTier, String?) -> Unit = { _, _, _ -> }
) {
    var selectedTab by remember { mutableStateOf(0) }
    var selectedRecord by remember { mutableStateOf<CallRecordEntity?>(null) }
    var selectedMinute by remember { mutableStateOf<MeetingMinuteEntity?>(null) }
    var showTimelineBrief by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Audio receive screen
    val pendingFile by viewModel.pendingShareFile.collectAsState()
    val shareError by viewModel.shareError.collectAsState()

    // Timeline brief state
    val timelineBriefResult by viewModel.timelineBriefResult.collectAsState()
    val isGeneratingBrief by viewModel.isGeneratingBrief.collectAsState()

    // Global task state for banner & back press guard
    val hasActiveTasks by viewModel.hasActiveTasks.collectAsState()
    val activeTaskDescription by viewModel.activeTaskDescription.collectAsState()

    // BackHandler: intercept back press when tasks are running
    BackHandler(enabled = hasActiveTasks && selectedRecord == null && selectedMinute == null && !showTimelineBrief) {
        showExitDialog = true
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            shape = DialogShape,
            title = {
                Text(
                    "任务进行中",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    "有任务正在处理中，离开可能导致任务中断。是否继续？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("留在应用")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    // Allow the system back behavior
                }) {
                    Text("离开", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // BackHandler for AudioReceiveScreen
    BackHandler(enabled = pendingFile != null) {
        viewModel.clearPendingShareFile()
    }

    pendingFile?.let { pending ->
        AudioReceiveScreen(
            pendingFile = pending,
            errorMessage = shareError,
            onConfirm = { tier, contactName ->
                onCopySharedFile(pending.uri, tier, contactName)
            },
            onDismiss = { viewModel.clearPendingShareFile() }
        )
    }
    
    // Detail screens
    // BackHandler for RecordDetailScreen
    BackHandler(enabled = selectedRecord != null) {
        selectedRecord = null
    }

    if (selectedRecord != null) {
        RecordDetailScreen(
            record = selectedRecord!!,
            viewModel = viewModel,
            onBack = { selectedRecord = null }
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
                        onGenerateTimelineBrief = { selectedMinutes ->
                            viewModel.generateTimelineBrief(selectedMinutes)
                            showTimelineBrief = true
                        }
                    )
                }
            }

            // Global task progress banner (top overlay)
            AnimatedVisibility(
                visible = hasActiveTasks,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC1A1A2E))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = activeTaskDescription.ifEmpty { "正在处理中，请保持 App 在前台运行..." },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
