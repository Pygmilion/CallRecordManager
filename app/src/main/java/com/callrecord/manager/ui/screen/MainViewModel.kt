package com.callrecord.manager.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.callrecord.manager.data.local.CallRecordEntity
import com.callrecord.manager.data.local.MeetingMinuteEntity
import com.callrecord.manager.data.local.MinuteWithContact
import com.callrecord.manager.data.local.RecordProcessStage
import com.callrecord.manager.data.local.TranscriptEntity
import com.callrecord.manager.data.local.TranscriptStatus
import android.content.Context
import com.callrecord.manager.data.repository.ApiKeyProvider
import com.callrecord.manager.data.repository.CallRecordRepository
import com.callrecord.manager.ui.screen.TimelineBriefResult
import com.callrecord.manager.utils.AppLogger
import com.callrecord.manager.utils.TaskNotificationHelper
import com.callrecord.manager.work.TranscriptionWorkScheduler
import com.callrecord.manager.work.TranscriptionWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主页 ViewModel
 */
class MainViewModel(
    private val repository: CallRecordRepository,
    private val appContext: Context? = null
) : ViewModel(), AppLogger.LogListener {

    // 录音列表
    private val _records = MutableStateFlow<List<CallRecordEntity>>(emptyList())
    val records: StateFlow<List<CallRecordEntity>> = _records.asStateFlow()

    // 纪要列表
    private val _minutes = MutableStateFlow<List<MeetingMinuteEntity>>(emptyList())
    val minutes: StateFlow<List<MeetingMinuteEntity>> = _minutes.asStateFlow()

    // Minutes with contact info for grouped display
    private val _minutesWithContact = MutableStateFlow<List<MinuteWithContact>>(emptyList())
    val minutesWithContact: StateFlow<List<MinuteWithContact>> = _minutesWithContact.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 错误信息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 成功信息
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // 录音搜索关键词
    private val _recordSearchQuery = MutableStateFlow("")
    val recordSearchQuery: StateFlow<String> = _recordSearchQuery.asStateFlow()

    // 纪要搜索关键词
    private val _minuteSearchQuery = MutableStateFlow("")
    val minuteSearchQuery: StateFlow<String> = _minuteSearchQuery.asStateFlow()
    
    // Job references for managing collect coroutines (to prevent Flow racing)
    private var loadRecordsJob: Job? = null
    private var loadMinutesJob: Job? = null
    private var loadMinutesWithContactJob: Job? = null
    private var searchRecordsJob: Job? = null
    private var searchMinutesJob: Job? = null
    private var queueStageSyncJob: Job? = null

    // 日志信息列表（用于调试）
    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()
    
    // Per-record process pipeline stages
    private val _recordProcessStages = MutableStateFlow<Map<Long, RecordProcessStage>>(emptyMap())
    val recordProcessStages: StateFlow<Map<Long, RecordProcessStage>> = _recordProcessStages.asStateFlow()

    // Pending shared files for AudioReceiveScreen (supports batch share)
    private val _pendingShareFiles = MutableStateFlow<List<PendingShareFile>>(emptyList())
    val pendingShareFiles: StateFlow<List<PendingShareFile>> = _pendingShareFiles.asStateFlow()

    // Error message for the AudioReceiveScreen
    private val _shareError = MutableStateFlow<String?>(null)
    val shareError: StateFlow<String?> = _shareError.asStateFlow()

    // Timeline brief result for the TimelineBriefScreen
    private val _timelineBriefResult = MutableStateFlow(TimelineBriefResult())
    val timelineBriefResult: StateFlow<TimelineBriefResult> = _timelineBriefResult.asStateFlow()

    // Loading state for timeline brief generation
    private val _isGeneratingBrief = MutableStateFlow(false)
    val isGeneratingBrief: StateFlow<Boolean> = _isGeneratingBrief.asStateFlow()

    init {
        // 注册日志监听
        AppLogger.addListener(this)
        AppLogger.i("ViewModel", "MainViewModel 初始化")
        
        loadRecords()
        loadMinutes()
    }
    
    override fun onCleared() {
        super.onCleared()
        queueStageSyncJob?.cancel()
        AppLogger.removeListener(this)
        AppLogger.i("ViewModel", "MainViewModel 销毁")
    }
    
    override fun onLog(level: AppLogger.Level, tag: String, message: String, throwable: Throwable?) {
        // 将日志添加到列表中，最多保留50条
        val formattedLog = AppLogger.formatForUI(level, tag, message, throwable)
        _logMessages.value = (_logMessages.value + formattedLog).takeLast(50)
        
        // 错误级别的日志自动显示为错误消息
        if (level == AppLogger.Level.ERROR) {
            _errorMessage.value = formattedLog
        }
    }

    /**
     * Update the process stage of a specific record
     */
    private fun updateRecordStage(recordId: Long, stage: RecordProcessStage) {
        _recordProcessStages.value = _recordProcessStages.value.toMutableMap().apply {
            put(recordId, stage)
        }
    }

    private enum class TranscriptionWorkState {
        NONE,
        QUEUED,
        RUNNING
    }

    /**
     * 加载录音列表
     */
    private fun loadRecords() {
        loadRecordsJob?.cancel()
        loadRecordsJob = viewModelScope.launch {
            repository.getAllRecords().collect { records ->
                _records.value = records
                // Restore process stages from DB on each refresh
                restoreProcessStages(records)
            }
        }
    }

    /**
     * Restore process stages from database + WorkManager states.
     */
    private suspend fun restoreProcessStages(records: List<CallRecordEntity>) {
        val rebuiltStages = mutableMapOf<Long, RecordProcessStage>()

        for (record in records) {
            val workState = getTranscriptionWorkState(record.id)
            val hasActiveWork = workState != TranscriptionWorkState.NONE
            val transcript = repository.repairOrphanProcessingTranscript(
                recordId = record.id,
                hasActiveWork = hasActiveWork
            )

            val stage = if (!record.isTranscribed) {
                when (transcript?.status) {
                    TranscriptStatus.PROCESSING, TranscriptStatus.PENDING -> {
                        when (workState) {
                            TranscriptionWorkState.RUNNING -> RecordProcessStage.TRANSCRIBING
                            TranscriptionWorkState.QUEUED -> RecordProcessStage.QUEUED
                            TranscriptionWorkState.NONE -> RecordProcessStage.TRANSCRIBE_FAILED
                        }
                    }
                    TranscriptStatus.FAILED -> RecordProcessStage.TRANSCRIBE_FAILED
                    TranscriptStatus.COMPLETED -> RecordProcessStage.TRANSCRIBE_DONE
                    null -> when (workState) {
                        TranscriptionWorkState.RUNNING -> RecordProcessStage.TRANSCRIBING
                        TranscriptionWorkState.QUEUED -> RecordProcessStage.QUEUED
                        TranscriptionWorkState.NONE -> RecordProcessStage.IDLE
                    }
                }
            } else {
                // Record is transcribed, check if minute exists
                val transcriptId = record.transcriptId
                if (transcriptId != null) {
                    val minute = repository.getMinuteByTranscriptId(transcriptId)
                    if (minute != null) {
                        RecordProcessStage.COMPLETED
                    } else {
                        when (workState) {
                            TranscriptionWorkState.RUNNING -> RecordProcessStage.GENERATING_MINUTE
                            TranscriptionWorkState.QUEUED -> RecordProcessStage.QUEUED
                            TranscriptionWorkState.NONE -> RecordProcessStage.TRANSCRIBE_DONE
                        }
                    }
                } else {
                    RecordProcessStage.TRANSCRIBE_DONE
                }
            }

            rebuiltStages[record.id] = stage
        }

        // Rebuild by current record IDs only to avoid stale deleted-record stages.
        _recordProcessStages.value = rebuiltStages
        scheduleQueueStageSync()
    }

    private suspend fun getTranscriptionWorkState(recordId: Long): TranscriptionWorkState {
        val context = appContext ?: return TranscriptionWorkState.NONE
        return withContext(Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(context)
                    .getWorkInfosByTag(TranscriptionWorker.recordTag(recordId))
                    .get()
                    .let { infos ->
                        when {
                            infos.any { info -> info.state == WorkInfo.State.RUNNING } -> TranscriptionWorkState.RUNNING
                            infos.any { info ->
                                info.state == WorkInfo.State.ENQUEUED ||
                                    info.state == WorkInfo.State.BLOCKED
                            } -> TranscriptionWorkState.QUEUED
                            else -> TranscriptionWorkState.NONE
                        }
                    }
            }.onFailure { e ->
                AppLogger.w("ViewModel", "查询后台任务状态失败: recordId=$recordId", e)
            }.getOrDefault(TranscriptionWorkState.NONE)
        }
    }

    /**
     * 加载纪要列表
     */
    private fun loadMinutes() {
        loadMinutesJob?.cancel()
        loadMinutesWithContactJob?.cancel()
        loadMinutesJob = viewModelScope.launch {
            repository.getAllMinutes().collect { minutes ->
                _minutes.value = minutes
                // Minute changes do not trigger call_records Flow; refresh record stages here as well.
                restoreProcessStages(_records.value)
            }
        }
        loadMinutesWithContactJob = viewModelScope.launch {
            repository.getAllMinutesWithContact().collect { minutesWithContact ->
                _minutesWithContact.value = minutesWithContact
            }
        }
    }

    /**
     * 扫描系统录音
     */
    fun scanRecordings() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            repository.scanSystemRecordings()
                .onSuccess { recordings ->
                    _errorMessage.value = null
                    _successMessage.value = "扫描完成，找到 ${recordings.size} 个录音文件"
                }
                .onFailure { error ->
                    _successMessage.value = null
                    _errorMessage.value = "扫描失败: ${error.message}"
                }
            
            _isLoading.value = false
        }
    }

    /**
     * 导入音频文件
     */
    fun importAudioFile(filePath: String, fileName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            repository.importAudioFile(filePath, fileName)
                .onSuccess { _ ->
                    _errorMessage.value = null
                    _successMessage.value = "文件导入成功"
                }
                .onFailure { error ->
                    _successMessage.value = null
                    _errorMessage.value = "导入失败: ${error.message}"
                }
            
            _isLoading.value = false
        }
    }

    /**
     * 删除录音
     */
    fun deleteRecord(record: CallRecordEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            
            repository.deleteRecord(record)
                .onSuccess {
                    _errorMessage.value = null
                    _successMessage.value = "删除成功"
                }
                .onFailure { error ->
                    _successMessage.value = null
                    _errorMessage.value = "删除失败: ${error.message}"
                }
            
            _isLoading.value = false
        }
    }

    /**
     * 转写录音（后台执行）
     */
    fun transcribeRecord(record: CallRecordEntity) {
        val context = appContext
        if (context == null) {
            _errorMessage.value = "无法启动后台任务：应用上下文丢失"
            return
        }
        if (isRecordAlreadyProcessing(record.id)) {
            _successMessage.value = "该录音已在转写队列中"
            return
        }
        if (!ApiKeyProvider.hasApiKey()) {
            val message = "API Key 未配置，请先在设置中填写后再转写"
            _errorMessage.value = message
            updateRecordStage(record.id, RecordProcessStage.TRANSCRIBE_FAILED)
            TaskNotificationHelper.showFailureNotification(context, message)
            return
        }

        AppLogger.i("ViewModel", "提交后台转写任务（含纪要）: ${record.fileName}")
        val enqueued = TranscriptionWorkScheduler.enqueue(
            context = context,
            recordId = record.id,
            generateMinute = true
        )
        if (enqueued) {
            updateRecordStage(record.id, RecordProcessStage.QUEUED)
            syncQueuedRecordStage(record.id)
            _successMessage.value = "任务已加入后台队列，退出应用后会继续执行"
        } else {
            _successMessage.value = "该录音已在后台队列中"
        }
    }

    /**
     * 生成会谈纪要（后台执行）
     */
    private fun generateMinute(transcript: TranscriptEntity, record: CallRecordEntity) {
        viewModelScope.launch {
            updateRecordStage(record.id, RecordProcessStage.GENERATING_MINUTE)
            AppLogger.i("ViewModel", "调用生成纪要（后台），转写ID: ${transcript.id}")
            
            repository.generateMeetingMinute(transcript, record)
                .onSuccess { minute ->
                    AppLogger.i("ViewModel", "纪要生成成功，ID: ${minute.id}")
                    updateRecordStage(record.id, RecordProcessStage.COMPLETED)
                    appContext?.let { TaskNotificationHelper.showSuccessNotification(it, "纪要已生成: ${minute.title}") }
                    // 刷新纪要列表
                    loadMinutes()
                }
                .onFailure { error ->
                    AppLogger.e("ViewModel", "纪要生成失败: ${error.message}", error)
                    updateRecordStage(record.id, RecordProcessStage.MINUTE_FAILED)
                    _errorMessage.value = "纪要生成失败: ${error.message}"
                    appContext?.let { TaskNotificationHelper.showFailureNotification(it, "纪要生成失败: ${error.message}") }
                }
        }
    }

    /**
     * Retry generate minute for a record that previously failed (stage = MINUTE_FAILED).
     * Finds the existing transcript and re-runs minute generation.
     */
    fun retryGenerateMinute(record: CallRecordEntity) {
        viewModelScope.launch {
            val transcriptId = record.transcriptId
            if (transcriptId == null) {
                AppLogger.e("ViewModel", "无法重试纪要生成：录音未关联转写记录")
                _errorMessage.value = "无法重试：录音未关联转写记录"
                return@launch
            }
            val transcript = repository.getTranscriptByRecordId(record.id)
            if (transcript == null) {
                AppLogger.e("ViewModel", "无法重试纪要生成：找不到转写记录")
                _errorMessage.value = "无法重试：找不到转写记录"
                return@launch
            }
            // Delete existing minute if any (for regeneration of completed records)
            repository.deleteMinuteByTranscriptId(transcriptId)
            AppLogger.i("ViewModel", "重试生成纪要: ${record.fileName}")
            generateMinute(transcript, record)
        }
    }

    /**
     * Get the meeting minute associated with a record (if any).
     * Returns null if no minute exists for this record.
     */
    suspend fun getMinuteForRecord(record: CallRecordEntity): MeetingMinuteEntity? {
        val transcriptId = record.transcriptId ?: return null
        return repository.getMinuteByTranscriptId(transcriptId)
    }

    /**
     * Save edited transcript text.
     * Updates fullText and speakers in the database.
     */
    fun saveEditedTranscript(transcriptId: Long, editedText: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.updateTranscriptText(transcriptId, editedText)
                .onSuccess {
                    AppLogger.i("ViewModel", "转写内容已更新")
                    _successMessage.value = "转写内容已更新"
                    onComplete(true)
                }
                .onFailure { error ->
                    AppLogger.e("ViewModel", "更新转写内容失败: ${error.message}", error)
                    _errorMessage.value = "更新转写内容失败: ${error.message}"
                    onComplete(false)
                }
        }
    }

    /**
     * Delete a meeting minute
     */
    fun deleteMinute(minute: MeetingMinuteEntity) {
        viewModelScope.launch {
            try {
                repository.deleteMinute(minute)
                AppLogger.i("ViewModel", "纪要已删除: ${minute.title}")
                _successMessage.value = "纪要已删除"
                loadMinutes()
            } catch (e: Exception) {
                AppLogger.e("ViewModel", "删除纪要失败: ${e.message}", e)
                _errorMessage.value = "删除纪要失败: ${e.message}"
            }
        }
    }

    /**
     * Get transcript for a record (used by RecordDetailScreen)
     */
    suspend fun getTranscriptForRecord(recordId: Long): TranscriptEntity? {
        return repository.getTranscriptByRecordId(recordId)
    }

    /**
     * Get transcript by its ID (used by MinuteDetailScreen)
     */
    suspend fun getTranscriptById(transcriptId: Long): TranscriptEntity? {
        return repository.getTranscriptById(transcriptId)
    }

    /**
     * 搜索录音
     */
    fun searchRecords(query: String) {
        _recordSearchQuery.value = query
        // Cancel any previous search collect
        searchRecordsJob?.cancel()
        if (query.isBlank()) {
            // Restart full list collect only if not already running
            if (loadRecordsJob?.isActive != true) {
                loadRecords()
            }
        } else {
            // Cancel full list collect to prevent racing
            loadRecordsJob?.cancel()
            searchRecordsJob = viewModelScope.launch {
                repository.searchRecords(query).collect { records ->
                    _records.value = records
                }
            }
        }
    }

    /**
     * 搜索纪要
     */
    fun searchMinutes(query: String) {
        _minuteSearchQuery.value = query
        // Cancel any previous search collect
        searchMinutesJob?.cancel()
        if (query.isBlank()) {
            // Restart full list collect only if not already running
            if (loadMinutesJob?.isActive != true) {
                loadMinutes()
            }
        } else {
            // Cancel full list collect to prevent racing
            loadMinutesJob?.cancel()
            loadMinutesWithContactJob?.cancel()
            searchMinutesJob = viewModelScope.launch {
                repository.searchMinutes(query).collect { minutes ->
                    _minutes.value = minutes
                }
            }
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 清除成功信息
     */
    fun clearSuccess() {
        _successMessage.value = null
    }
    
    /**
     * 刷新纪要列表
     */
    fun refreshMinutes() {
        AppLogger.i("ViewModel", "手动刷新纪要列表")
        loadMinutes()
    }
    
    /**
     * 为已转写的录音重新生成纪要
     */
    fun regenerateMinutes() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            AppLogger.i("ViewModel", "开始为已转写的录音重新生成纪要")
            
            repository.regenerateMinutesForTranscribed()
                .onSuccess { count ->
                    AppLogger.i("ViewModel", "成功生成 $count 个纪要")
                    _successMessage.value = "成功生成 $count 个纪要"
                    loadMinutes()
                }
                .onFailure { error ->
                    AppLogger.e("ViewModel", "重新生成纪要失败: ${error.message}", error)
                    _errorMessage.value = "重新生成纪要失败: ${error.message}"
                }
            
            _isLoading.value = false
        }
    }
    
    /**
     * Set pending shared files to show the AudioReceiveScreen.
     */
    fun setPendingShareFiles(pending: List<PendingShareFile>) {
        _shareError.value = null
        _pendingShareFiles.value = pending
            .filter { it.fileName.isNotBlank() }
            .distinctBy { "${it.uri}|${it.fileName}" }
    }

    /**
     * Clear pending shared files (dismiss AudioReceiveScreen).
     */
    fun clearPendingShareFiles() {
        _pendingShareFiles.value = emptyList()
        _shareError.value = null
    }

    fun setShareError(message: String?) {
        _shareError.value = message
    }

    /**
     * Backward-compatible single-file import entry.
     */
    fun importWithOptions(
        filePath: String,
        fileName: String,
        tier: AudioImportTier,
        contactName: String?
    ) {
        importBatchWithOptions(
            files = listOf(LocalImportAudioFile(filePath = filePath, fileName = fileName)),
            tier = tier,
            contactName = contactName
        )
    }

    /**
     * Import copied local files in batch and apply selected processing tier.
     */
    fun importBatchWithOptions(
        files: List<LocalImportAudioFile>,
        tier: AudioImportTier,
        contactName: String?,
        copyFailedFileNames: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _errorMessage.value = null
            if (files.isEmpty()) {
                _shareError.value = "导入失败：没有可导入的文件"
                return@launch
            }

            val importedRecords = mutableListOf<CallRecordEntity>()
            val importFailedNames = mutableListOf<String>()

            files.forEach { file ->
                repository.importAudioFile(file.filePath, file.fileName, contactName)
                    .onSuccess { record ->
                        importedRecords += record
                        AppLogger.i("ViewModel", "文件导入成功(${tier.label}): ${record.fileName}")
                    }
                    .onFailure { error ->
                        AppLogger.e("ViewModel", "导入失败: ${file.fileName} - ${error.message}", error)
                        importFailedNames += file.fileName
                    }
            }

            if (importedRecords.isEmpty()) {
                val failed = (copyFailedFileNames + importFailedNames).distinct()
                val preview = failed.take(3).joinToString("、")
                _shareError.value = if (preview.isNotBlank()) {
                    "导入失败：${preview}${if (failed.size > 3) " 等${failed.size}个文件" else ""}"
                } else {
                    "导入失败：文件不可用"
                }
                return@launch
            }

            _pendingShareFiles.value = emptyList()
            _shareError.value = null

            importedRecords.forEach { record ->
                when (tier) {
                    AudioImportTier.STORE_ONLY -> Unit
                    AudioImportTier.STORE_AND_TRANSCRIBE -> transcribeRecordOnly(record)
                    AudioImportTier.FULL_PIPELINE -> transcribeRecord(record)
                }
            }

            val failedTotal = copyFailedFileNames.size + importFailedNames.size
            _successMessage.value = when {
                failedTotal == 0 && importedRecords.size == 1 -> "文件导入成功"
                failedTotal == 0 -> "成功导入 ${importedRecords.size} 个文件"
                else -> "成功导入 ${importedRecords.size} 个文件，失败 $failedTotal 个"
            }

            if (failedTotal > 0) {
                val failedNames = (copyFailedFileNames + importFailedNames).distinct()
                val preview = failedNames.take(3).joinToString("、")
                _errorMessage.value = if (failedNames.size > 3) {
                    "部分文件处理失败：$preview 等 ${failedNames.size} 个"
                } else {
                    "部分文件处理失败：$preview"
                }
            }
        }
    }

    /**
     * Transcribe a record without generating a minute afterward
     */
    private fun transcribeRecordOnly(record: CallRecordEntity) {
        val context = appContext
        if (context == null) {
            _shareError.value = "无法启动后台任务：应用上下文丢失"
            return
        }
        if (isRecordAlreadyProcessing(record.id)) {
            _successMessage.value = "该录音已在转写队列中"
            return
        }
        if (!ApiKeyProvider.hasApiKey()) {
            val message = "API Key 未配置，请先在设置中填写后再转写"
            _shareError.value = message
            updateRecordStage(record.id, RecordProcessStage.TRANSCRIBE_FAILED)
            TaskNotificationHelper.showFailureNotification(context, message)
            return
        }

        AppLogger.i("ViewModel", "提交后台转写任务（仅转写）: ${record.fileName}")
        val enqueued = TranscriptionWorkScheduler.enqueue(
            context = context,
            recordId = record.id,
            generateMinute = false
        )
        if (enqueued) {
            updateRecordStage(record.id, RecordProcessStage.QUEUED)
            syncQueuedRecordStage(record.id)
            _successMessage.value = "转写任务已加入后台队列，退出应用后会继续执行"
        } else {
            _successMessage.value = "该录音已在后台队列中"
        }
    }

    private fun syncQueuedRecordStage(recordId: Long) {
        viewModelScope.launch {
            when (getTranscriptionWorkState(recordId)) {
                TranscriptionWorkState.RUNNING -> updateRecordStage(recordId, RecordProcessStage.TRANSCRIBING)
                TranscriptionWorkState.QUEUED -> updateRecordStage(recordId, RecordProcessStage.QUEUED)
                TranscriptionWorkState.NONE -> Unit
            }
            scheduleQueueStageSync()
        }
    }

    private fun scheduleQueueStageSync() {
        if (queueStageSyncJob?.isActive == true) {
            return
        }

        queueStageSyncJob = viewModelScope.launch {
            while (true) {
                val hasPipelineActivity = _recordProcessStages.value.values.any { stage ->
                    stage == RecordProcessStage.QUEUED ||
                        stage == RecordProcessStage.TRANSCRIBING ||
                        stage == RecordProcessStage.GENERATING_MINUTE
                }
                if (!hasPipelineActivity) {
                    break
                }
                restoreProcessStages(_records.value)
                delay(2_000L)
            }
        }
    }

    private fun isRecordAlreadyProcessing(recordId: Long): Boolean {
        return when (_recordProcessStages.value[recordId]) {
            RecordProcessStage.QUEUED,
            RecordProcessStage.TRANSCRIBING,
            RecordProcessStage.GENERATING_MINUTE -> true
            else -> false
        }
    }

    /**
     * Generate a timeline brief from multiple selected minutes.
     * Called from MinuteListScreen via MainApp.
     */
    fun generateTimelineBrief(selectedMinutes: List<MinuteWithContact>) {
        viewModelScope.launch {
            _isGeneratingBrief.value = true
            _timelineBriefResult.value = TimelineBriefResult() // Reset
            AppLogger.i("ViewModel", "开始生成脉络简报，选中 ${selectedMinutes.size} 条纪要")

            repository.generateTimelineBrief(selectedMinutes)
                .onSuccess { result ->
                    AppLogger.i("ViewModel", "脉络简报生成成功: ${result.title}")
                    _timelineBriefResult.value = result
                }
                .onFailure { error ->
                    AppLogger.e("ViewModel", "脉络简报生成失败: ${error.message}", error)
                    _errorMessage.value = "脉络简报生成失败: ${error.message}"
                }

            _isGeneratingBrief.value = false
        }
    }

    /**
     * Update contact name for all records with the given phone number.
     */
    fun updateContactName(phoneNumber: String, newName: String) {
        viewModelScope.launch {
            repository.updateContactName(phoneNumber, newName)
                .onSuccess {
                    AppLogger.i("ViewModel", "联系人名称已更新: $phoneNumber -> $newName")
                    _successMessage.value = "联系人名称已更新"
                }
                .onFailure { error ->
                    AppLogger.e("ViewModel", "更新联系人名称失败: ${error.message}", error)
                    _errorMessage.value = "更新联系人名称失败: ${error.message}"
                }
        }
    }

    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String? {
        return AppLogger.getLogFilePath()
    }
}
