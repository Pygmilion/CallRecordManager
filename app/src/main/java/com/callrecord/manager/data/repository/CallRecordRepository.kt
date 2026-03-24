package com.callrecord.manager.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.ContactsContract
import androidx.work.WorkManager
import com.callrecord.manager.data.local.*
import com.callrecord.manager.data.remote.*
import com.callrecord.manager.utils.AudioChunk
import com.callrecord.manager.utils.AudioChunker
import com.callrecord.manager.utils.AppLogger
import com.callrecord.manager.work.TranscriptionWorker
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest

/**
 * 通话录音仓库
 */
class CallRecordRepository(
    private val context: Context,
    private val callRecordDao: CallRecordDao,
    private val transcriptDao: TranscriptDao,
    private val meetingMinuteDao: MeetingMinuteDao,
    private val apiService: StepFunApiService
) {

    private companion object {
        private const val LONG_AUDIO_CHUNK_THRESHOLD_SECONDS = 8 * 60L
        private const val LONG_AUDIO_CHUNK_THRESHOLD_MB = 8.0
        private const val AUDIO_CHUNK_DURATION_SECONDS = 3 * 60L
        private const val TRANSCRIBE_RETRY_MAX_ATTEMPTS = 3
        private const val TRANSCRIBE_RETRY_DELAY_BASE_MS = 3_000L
        private const val ASR_SINGLE_ATTEMPT_TIMEOUT_MS = 4 * 60 * 1000L
        private const val PROCESSING_ORPHAN_TIMEOUT_MS = 2 * 60 * 1000L
        private const val CHECKPOINT_VERSION = 1
        private val RETRYABLE_HTTP_CODES = setOf(408, 429, 500, 502, 503, 504)
    }

    private data class TranscriptionCheckpoint(
        val version: Int = CHECKPOINT_VERSION,
        val recordId: Long,
        val sourcePathHash: String,
        val sourceSize: Long,
        val sourceLastModified: Long,
        val chunkDirPath: String,
        val totalChunks: Int,
        val nextChunkIndex: Int,
        val mergedText: String,
        val updateTime: Long = System.currentTimeMillis()
    )

    private data class SourceFingerprint(
        val pathHash: String,
        val size: Long,
        val lastModified: Long
    )
    
    /**
     * Get current API Key from ApiKeyProvider.
     * Throws IllegalStateException if not configured.
     */
    private fun requireApiKey(): String {
        val key = ApiKeyProvider.getApiKey()
        if (key.isBlank()) {
            throw IllegalStateException("API Key 未配置，请前往设置页面配置 API Key")
        }
        return key
    }
    
    // API 日志目录
    private val apiLogDir = File(context.getExternalFilesDir(null), "ApiLogs").apply {
        if (!exists()) mkdirs()
    }

    private val checkpointSerializer = Gson()
    private val checkpointRootDir = File(context.filesDir, "transcription_checkpoints").apply {
        if (!exists()) mkdirs()
    }
    
    /**
     * 保存 API 日志
     */
    private fun saveApiLog(type: String, content: String) {
        try {
            val timestamp = System.currentTimeMillis()
            val fileName = "${type}_${timestamp}.txt"
            val logFile = File(apiLogDir, fileName)
            logFile.writeText("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}\n\n$content")
            AppLogger.d("API日志", "已保存日志: ${logFile.absolutePath}")
        } catch (e: Exception) {
            AppLogger.e("API日志", "保存日志失败", e)
        }
    }

    /**
     * 获取所有录音记录
     */
    fun getAllRecords(): Flow<List<CallRecordEntity>> {
        return callRecordDao.getAllRecords()
    }

    /**
     * 获取未转写的录音
     */
    fun getUntranscribedRecords(): Flow<List<CallRecordEntity>> {
        return callRecordDao.getUntranscribedRecords()
    }

    /**
     * 搜索录音
     */
    fun searchRecords(query: String): Flow<List<CallRecordEntity>> {
        return callRecordDao.searchRecords(query)
    }

    /**
     * 扫描系统录音文件
     */
    suspend fun scanSystemRecordings(): Result<List<CallRecordEntity>> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("扫描录音", "开始扫描系统录音文件")
            val recordings = mutableListOf<CallRecordEntity>()
            val supportedAudioExtensions = setOf("mp3", "m4a", "wav", "amr", "3gp", "aac", "ogg")
            val scannedFilePaths = mutableSetOf<String>()
            
            // 常见的录音文件目录
            val recordingDirs = listOf(
                // 应用内部目录
                File(context.getExternalFilesDir(null), "Recordings"),
                File(context.getExternalFilesDir(null), "CallRecordings"),
                File(context.getExternalFilesDir(null), "ImportedRecordings"),
                
                // 外部存储常见目录
                File("/storage/emulated/0/Recordings"),
                File("/storage/emulated/0/CallRecordings"),
                File("/storage/emulated/0/录音"),
                File("/storage/emulated/0/通话录音"),
                File("/storage/emulated/0/DCIM/Recordings"),
                File("/storage/emulated/0/Music/Recordings"),
                File("/storage/emulated/0/Music/Recordings/CallRecordings"),
                File("/storage/emulated/0/Documents/Recordings"),
                File("/storage/emulated/0/Download/Recordings"),
                
                // 品牌特定目录
                File("/storage/emulated/0/MIUI/sound_recorder"),  // 小米
                File("/storage/emulated/0/Sounds"),  // 华为
                File("/storage/emulated/0/Record"),  // OPPO/Vivo
                File("/storage/emulated/0/PhoneRecord"),  // 三星
                File("/storage/emulated/0/CallRecord"),
                
                // 第三方录音应用目录
                File("/storage/emulated/0/Android/data/com.android.providers.telephony/files"),
            )

            var totalFiles = 0
            var validFiles = 0
            
            for (dir in recordingDirs) {
                if (dir.exists() && dir.isDirectory) {
                    AppLogger.d("扫描录音", "扫描目录: ${dir.absolutePath}")

                    val files = collectAudioFilesRecursively(
                        rootDir = dir,
                        supportedExtensions = supportedAudioExtensions
                    )
                    AppLogger.d("扫描录音", "目录命中文件数: ${files.size} (${dir.absolutePath})")

                    files.forEach { file ->
                        totalFiles++
                        val normalizedPath = normalizeFilePath(file)
                        if (!scannedFilePaths.add(normalizedPath)) {
                            AppLogger.d("扫描录音", "跳过重复文件: ${file.absolutePath}")
                            return@forEach
                        }
                        try {
                            val record = createRecordFromFile(file)
                            recordings.add(record)
                            validFiles++
                            AppLogger.d("扫描录音", "找到录音: ${file.name}")
                        } catch (e: Exception) {
                            AppLogger.w("扫描录音", "处理文件失败: ${file.name}", e)
                        }
                    }
                } else {
                    AppLogger.d("扫描录音", "目录不存在: ${dir.absolutePath}")
                }
            }

            AppLogger.i("扫描录音", "扫描完成: 总文件数=$totalFiles, 有效录音=$validFiles")
            
            // Dedup: filter out recordings that already exist in DB by contactName + recordTime
            val existingRecords = callRecordDao.getAllRecordsOnce()
            val existingPaths = existingRecords
                .map { normalizeFilePath(File(it.filePath)) }
                .toSet()
            val existingKeys = existingRecords.map { "${it.contactName ?: it.phoneNumber}|${it.recordTime}" }.toSet()
            val newRecordings = recordings.filter { record ->
                val normalizedPath = normalizeFilePath(File(record.filePath))
                val key = "${record.contactName ?: record.phoneNumber}|${record.recordTime}"
                normalizedPath !in existingPaths && key !in existingKeys
            }
            AppLogger.i("扫描录音", "去重后: 新增 ${newRecordings.size} 个, 已存在 ${recordings.size - newRecordings.size} 个")

            // 保存到数据库
            if (newRecordings.isNotEmpty()) {
                callRecordDao.insertRecords(newRecordings)
                AppLogger.i("扫描录音", "已保存 ${newRecordings.size} 个录音到数据库")
            }
            
            Result.success(recordings)
        } catch (e: Exception) {
            AppLogger.e("扫描录音", "扫描失败", e)
            Result.failure(e)
        }
    }

    private fun collectAudioFilesRecursively(
        rootDir: File,
        supportedExtensions: Set<String>
    ): List<File> {
        return runCatching {
            rootDir.walkTopDown()
                .onFail { current, error ->
                    AppLogger.w("扫描录音", "遍历目录失败: ${current.absolutePath}", error)
                }
                .filter { file ->
                    file.isFile && file.extension.lowercase() in supportedExtensions
                }
                .toList()
        }.onFailure { error ->
            AppLogger.w("扫描录音", "扫描目录失败: ${rootDir.absolutePath}", error)
        }.getOrDefault(emptyList())
    }

    private fun normalizeFilePath(file: File): String {
        return runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
    }

    /**
     * 从文件创建录音记录
     */
    private fun createRecordFromFile(file: File): CallRecordEntity {
        val retriever = MediaMetadataRetriever()
        var duration = 0L
        
        try {
            retriever.setDataSource(file.absolutePath)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            duration /= 1000  // 转换为秒
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }

        // 解析文件名：格式为 "联系人_yymmddHHMM.扩展名" 或 "联系人-yymmddHHMM.扩展名"
        val fileNameInfo = parseFileName(file.name)
        
        // 直接使用解析出的联系人名称，不需要在通讯录中查找
        // 如果解析出的是电话号码，再尝试从通讯录获取名称
        val contactName = fileNameInfo.first?.let { parsedName ->
            // 判断是否为电话号码（全是数字）
            if (parsedName.all { it.isDigit() || it == '-' || it == ' ' }) {
                // 是电话号码，尝试从通讯录获取名称
                getContactName(parsedName) ?: parsedName
            } else {
                // 不是电话号码，直接使用解析出的名称
                parsedName
            }
        }

        return CallRecordEntity(
            fileName = file.name,
            filePath = file.absolutePath,
            phoneNumber = fileNameInfo.first?.takeIf { it.all { c -> c.isDigit() || c == '-' || c == ' ' } },
            contactName = contactName,
            duration = duration,
            fileSize = file.length(),
            recordTime = fileNameInfo.second ?: file.lastModified()
        )
    }

    /**
     * 解析文件名
     * 格式：联系人_yymmddHHMM.扩展名 或 联系人-yymmddHHMM.扩展名
     * 返回：Pair(联系人/电话号码, 录制时间戳)
     */
    private fun parseFileName(fileName: String): Pair<String?, Long?> {
        try {
            // 移除扩展名
            val nameWithoutExt = fileName.substringBeforeLast(".")
            
            // 查找最后一个下划线、横杠或空格
            val separatorIndex = nameWithoutExt.indexOfLast { it == '_' || it == '-' || it == ' ' }
            
            if (separatorIndex > 0) {
                val contactPart = nameWithoutExt.substring(0, separatorIndex).trim()
                val timePart = nameWithoutExt.substring(separatorIndex + 1).trim()
                
                // 解析时间部分：yymmddHHMM (10位数字)
                if (timePart.length == 10 && timePart.all { it.isDigit() }) {
                    try {
                        val yy = timePart.substring(0, 2).toInt()
                        val mm = timePart.substring(2, 4).toInt()
                        val dd = timePart.substring(4, 6).toInt()
                        val HH = timePart.substring(6, 8).toInt()
                        val MM = timePart.substring(8, 10).toInt()
                        
                        // 转换为完整年份（假设2000-2099年）
                        val year = 2000 + yy
                        
                        // 创建日期
                        val calendar = java.util.Calendar.getInstance()
                        calendar.set(year, mm - 1, dd, HH, MM, 0)
                        calendar.set(java.util.Calendar.MILLISECOND, 0)
                        
                        val timestamp = calendar.timeInMillis
                        
                        AppLogger.d("文件名解析", "文件: $fileName -> 联系人: $contactPart, 时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))}")
                        
                        return Pair(contactPart, timestamp)
                    } catch (e: Exception) {
                        AppLogger.w("文件名解析", "时间解析失败: $timePart", e)
                    }
                }
                
                // 如果时间解析失败，但有联系人信息
                AppLogger.d("文件名解析", "文件: $fileName -> 联系人: $contactPart (时间解析失败)")
                return Pair(contactPart, null)
            }
            
            // 尝试提取电话号码
            val phoneRegex = Regex("\\d{11}|\\d{3}-\\d{4}-\\d{4}|\\d{3}\\s\\d{4}\\s\\d{4}")
            val phoneNumber = phoneRegex.find(nameWithoutExt)?.value
            if (phoneNumber != null) {
                AppLogger.d("文件名解析", "文件: $fileName -> 电话: $phoneNumber")
                return Pair(phoneNumber, null)
            }
            
            AppLogger.w("文件名解析", "无法解析文件名: $fileName")
            
        } catch (e: Exception) {
            AppLogger.e("文件名解析", "解析文件名失败: $fileName", e)
        }
        
        return Pair(null, null)
    }

    /**
     * 从文件名提取电话号码（已废弃，使用 parseFileName）
     */
    @Deprecated("使用 parseFileName 代替")
    private fun extractPhoneNumber(fileName: String): String? {
        val phoneRegex = Regex("\\d{11}|\\d{3}-\\d{4}-\\d{4}|\\d{3}\\s\\d{4}\\s\\d{4}")
        return phoneRegex.find(fileName)?.value
    }

    /**
     * 根据电话号码获取联系人名称
     * Wrapped in try-catch to handle missing READ_CONTACTS runtime permission gracefully
     */
    private fun getContactName(phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
            
            null
        } catch (e: SecurityException) {
            // READ_CONTACTS permission not granted at runtime, fall back to phone number
            AppLogger.w("联系人查询", "无通讯录权限，使用电话号码: $phoneNumber", e)
            null
        } catch (e: Exception) {
            AppLogger.w("联系人查询", "查询联系人失败: $phoneNumber", e)
            null
        }
    }

    /**
     * 导入音频文件
     */
    suspend fun importAudioFile(filePath: String, fileName: String, contactNameOverride: String? = null): Result<CallRecordEntity> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("导入文件", "开始导入文件: $fileName")
            AppLogger.d("导入文件", "文件路径: $filePath")
            
            val file = File(filePath)
            if (!file.exists()) {
                AppLogger.e("导入文件", "文件不存在: $filePath")
                return@withContext Result.failure(Exception("文件不存在"))
            }

            AppLogger.d("导入文件", "文件大小: ${file.length() / 1024}KB")
            
            // 创建录音记录
            val record = createRecordFromFile(file).let { r ->
                if (contactNameOverride != null) r.copy(contactName = contactNameOverride) else r
            }
            AppLogger.d("导入文件", "创建录音记录: 时长=${record.duration}秒, 联系人=${record.contactName}")
            
            // 保存到数据库
            val recordId = callRecordDao.insertRecord(record)
            AppLogger.i("导入文件", "文件导入成功，记录ID: $recordId")
            
            Result.success(record.copy(id = recordId))
        } catch (e: Exception) {
            AppLogger.e("导入文件", "导入失败", e)
            Result.failure(e)
        }
    }

    /**
     * 删除录音
     */
    suspend fun deleteRecord(record: CallRecordEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Cancel queued/running transcription first to avoid "deleted but still processing" ghost state.
            runCatching {
                WorkManager.getInstance(context)
                    .cancelAllWorkByTag(TranscriptionWorker.recordTag(record.id))
            }.onFailure { error ->
                AppLogger.w("删除录音", "取消后台任务失败: recordId=${record.id}", error)
            }
            clearTranscriptionCheckpoint(record.id)

            // 删除文件
            val file = File(record.filePath)
            if (file.exists()) {
                file.delete()
            }
            
            // 删除数据库记录
            callRecordDao.deleteRecord(record)
            
            // 删除关联的转写和纪要
            transcriptDao.deleteTranscriptByRecordId(record.id)
            record.transcriptId?.let { transcriptId ->
                meetingMinuteDao.deleteMinuteByTranscriptId(transcriptId)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 转写录音
     */
    suspend fun transcribeRecord(
        record: CallRecordEntity,
        onProgress: (suspend (String) -> Unit)? = null
    ): Result<TranscriptEntity> = withContext(Dispatchers.IO) {
        var processingTranscript: TranscriptEntity? = null
        var processingTranscriptId: Long? = null

        try {
            AppLogger.i("转写", "开始转写录音: ${record.fileName}")
            AppLogger.d("转写", "录音ID: ${record.id}, 文件路径: ${record.filePath}")

            val file = File(record.filePath)
            if (!file.exists()) {
                AppLogger.e("转写", "录音文件不存在: ${record.filePath}")
                return@withContext Result.failure(Exception("录音文件不存在"))
            }

            val fileSizeMB = file.length() / (1024.0 * 1024.0)
            AppLogger.d("转写", "文件大小: ${"%.2f".format(fileSizeMB)}MB, 时长=${record.duration}s")

            saveApiLog(
                "transcribe_request",
                "录音ID: ${record.id}\n文件: ${file.name}\n大小: ${file.length()}字节\n时长: ${record.duration}秒"
            )

            val apiKey = requireApiKey()
            onProgress?.invoke("正在准备转写")

            val (processing, transcriptId) = prepareProcessingTranscript(record.id)
            processingTranscript = processing
            processingTranscriptId = transcriptId

            val fullText = when {
                shouldUseChunkTranscription(record, fileSizeMB) -> {
                    transcribeLargeFile(
                        record = record,
                        sourceFile = file,
                        apiKey = apiKey,
                        onProgress = onProgress
                    )
                }
                else -> {
                    onProgress?.invoke("正在上传录音并转写")
                    val response = requestTranscriptionWithRetry(
                        requestFile = file,
                        apiKey = apiKey,
                        requestLabel = "录音 ${record.fileName}"
                    ) { message ->
                        onProgress?.invoke(message)
                    }
                    response.text
                }
            }

            AppLogger.i("转写", "转写成功，文本长度: ${fullText.length}字符")
            saveApiLog("transcribe_response", "录音ID: ${record.id}\n转写文本:\n$fullText")

            val speakers = listOf(
                SpeakerSegment(
                    speaker = "Speaker 1",
                    text = fullText,
                    startTime = 0.0,
                    endTime = 0.0
                )
            )

            val updatedTranscript = processing.copy(
                id = transcriptId,
                fullText = fullText,
                speakers = speakers,
                status = TranscriptStatus.COMPLETED,
                updateTime = System.currentTimeMillis()
            )
            transcriptDao.updateTranscript(updatedTranscript)
            AppLogger.i("转写", "转写记录已更新")

            callRecordDao.updateRecord(
                record.copy(
                    isTranscribed = true,
                    transcriptId = transcriptId
                )
            )
            AppLogger.i("转写", "录音记录已标记为已转写")

            Result.success(updatedTranscript)
        } catch (e: CancellationException) {
            AppLogger.w("转写", "转写任务被取消: ${e.message}")
            markTranscriptFailed(processingTranscript, processingTranscriptId)
            Result.failure(IllegalStateException("转写任务被取消", e))
        } catch (e: Exception) {
            markTranscriptFailed(processingTranscript, processingTranscriptId)

            AppLogger.e("转写", "转写过程发生异常", e)
            saveApiLog("transcribe_exception", "异常: ${e.message}\n${e.stackTraceToString()}")
            Result.failure(e)
        }
    }

    private suspend fun markTranscriptFailed(
        processingTranscript: TranscriptEntity?,
        processingTranscriptId: Long?
    ) {
        val transcript = processingTranscript
        val transcriptId = processingTranscriptId
        if (transcript == null || transcriptId == null) {
            return
        }
        runCatching {
            transcriptDao.updateTranscript(
                transcript.copy(
                    id = transcriptId,
                    status = TranscriptStatus.FAILED,
                    updateTime = System.currentTimeMillis()
                )
            )
        }.onFailure { updateError ->
            AppLogger.w("转写", "更新失败状态时异常: ${updateError.message}", updateError)
        }
    }

    private suspend fun prepareProcessingTranscript(recordId: Long): Pair<TranscriptEntity, Long> {
        val now = System.currentTimeMillis()
        val latest = transcriptDao.getTranscriptByRecordId(recordId)

        if (latest != null && latest.status != TranscriptStatus.COMPLETED) {
            val reused = latest.copy(
                fullText = "",
                speakers = emptyList(),
                status = TranscriptStatus.PROCESSING,
                updateTime = now
            )
            transcriptDao.updateTranscript(reused)
            AppLogger.i("转写", "复用转写记录，ID: ${latest.id}")
            return reused to latest.id
        }

        val transcript = TranscriptEntity(
            recordId = recordId,
            fullText = "",
            speakers = emptyList(),
            status = TranscriptStatus.PROCESSING
        )
        val transcriptId = transcriptDao.insertTranscript(transcript)
        AppLogger.i("转写", "创建转写记录，ID: $transcriptId")
        return transcript.copy(id = transcriptId) to transcriptId
    }

    private fun shouldUseChunkTranscription(record: CallRecordEntity, fileSizeMB: Double): Boolean {
        if (record.duration >= LONG_AUDIO_CHUNK_THRESHOLD_SECONDS) {
            return true
        }
        if (fileSizeMB >= LONG_AUDIO_CHUNK_THRESHOLD_MB) {
            return true
        }
        return record.duration <= 0 && fileSizeMB >= LONG_AUDIO_CHUNK_THRESHOLD_MB / 2
    }

    private suspend fun transcribeLargeFile(
        record: CallRecordEntity,
        sourceFile: File,
        apiKey: String,
        onProgress: (suspend (String) -> Unit)?
    ): String {
        AppLogger.i(
            "转写",
            "检测到长音频，启用切片转写: recordId=${record.id}, duration=${record.duration}s"
        )
        val sourceFingerprint = buildSourceFingerprint(sourceFile)
        val checkpoint = loadTranscriptionCheckpoint(record.id)
        val reusableCheckpoint = checkpoint?.takeIf {
            isCheckpointCompatible(
                checkpoint = it,
                recordId = record.id,
                sourceFingerprint = sourceFingerprint
            )
        }

        var chunkDir: File
        var chunks: List<AudioChunk>
        var nextChunkIndex = 0
        val merged = StringBuilder()

        if (reusableCheckpoint != null) {
            val restoredChunks = loadChunksFromCheckpoint(reusableCheckpoint)
            if (restoredChunks != null) {
                chunkDir = File(reusableCheckpoint.chunkDirPath)
                chunks = restoredChunks
                nextChunkIndex = reusableCheckpoint.nextChunkIndex.coerceIn(0, chunks.size)
                if (reusableCheckpoint.mergedText.isNotBlank()) {
                    merged.append(reusableCheckpoint.mergedText)
                }
                if (nextChunkIndex < chunks.size) {
                    onProgress?.invoke("恢复上次进度：分片 ${nextChunkIndex + 1}/${chunks.size}")
                } else {
                    onProgress?.invoke("恢复上次进度：准备合并结果")
                }
                AppLogger.i(
                    "转写",
                    "命中断点续跑: recordId=${record.id}, nextChunk=${nextChunkIndex + 1}/${chunks.size}"
                )
            } else {
                AppLogger.w("转写", "检查点存在但分片文件不完整，重新切片: recordId=${record.id}")
                clearTranscriptionCheckpoint(record.id)
                val rebuilt = buildChunksForRecord(record, sourceFile, sourceFingerprint, onProgress)
                chunkDir = rebuilt.first
                chunks = rebuilt.second
            }
        } else {
            if (checkpoint != null) {
                AppLogger.w("转写", "检查点与当前源文件不匹配，重建切片: recordId=${record.id}")
                clearTranscriptionCheckpoint(record.id)
            }
            val rebuilt = buildChunksForRecord(record, sourceFile, sourceFingerprint, onProgress)
            chunkDir = rebuilt.first
            chunks = rebuilt.second
        }

        if (chunks.isEmpty()) {
            clearTranscriptionCheckpoint(record.id, chunkDir)
            return requestTranscriptionWithRetry(
                requestFile = sourceFile,
                apiKey = apiKey,
                requestLabel = "录音 ${record.fileName}"
            ) { message ->
                onProgress?.invoke(message)
            }.text
        }

        if (nextChunkIndex >= chunks.size) {
            val restoredText = merged.toString().trim()
            clearTranscriptionCheckpoint(record.id, chunkDir)
            return restoredText.ifBlank {
                throw IllegalStateException("检查点显示已完成，但文本为空")
            }
        }

        AppLogger.i("转写", "长音频切片就绪: ${chunks.size} 段, 从分片 ${nextChunkIndex + 1} 开始")
        for (index in nextChunkIndex until chunks.size) {
            val chunk = chunks[index]
            val label = "分片 ${index + 1}/${chunks.size}"
            onProgress?.invoke("正在转写$label")
            AppLogger.i(
                "转写",
                "开始转写$label: ${chunk.file.name}, 区间=${chunk.startMs}ms-${chunk.endMs}ms"
            )
            val response = requestTranscriptionWithRetry(
                requestFile = chunk.file,
                apiKey = apiKey,
                requestLabel = "录音${record.fileName}-$label"
            ) { message ->
                onProgress?.invoke("$label：$message")
            }
            val chunkText = response.text.trim()
            if (chunkText.isNotBlank()) {
                if (merged.isNotEmpty()) merged.append("\n")
                merged.append(chunkText)
            }
            persistTranscriptionCheckpoint(
                TranscriptionCheckpoint(
                    recordId = record.id,
                    sourcePathHash = sourceFingerprint.pathHash,
                    sourceSize = sourceFingerprint.size,
                    sourceLastModified = sourceFingerprint.lastModified,
                    chunkDirPath = chunkDir.absolutePath,
                    totalChunks = chunks.size,
                    nextChunkIndex = index + 1,
                    mergedText = merged.toString()
                )
            )
        }

        val mergedText = merged.toString().trim()
        clearTranscriptionCheckpoint(record.id, chunkDir)
        return mergedText.ifBlank {
            throw IllegalStateException("分片转写完成但未返回有效文本")
        }
    }

    private suspend fun buildChunksForRecord(
        record: CallRecordEntity,
        sourceFile: File,
        sourceFingerprint: SourceFingerprint,
        onProgress: (suspend (String) -> Unit)?
    ): Pair<File, List<AudioChunk>> {
        onProgress?.invoke("检测到长音频，正在切片")

        val chunkDir = buildChunkDirectory(record.id).apply {
            if (exists()) {
                deleteRecursively()
            }
            mkdirs()
        }

        val chunks = AudioChunker.split(
            sourceFile = sourceFile,
            outputDir = chunkDir,
            chunkDurationSec = AUDIO_CHUNK_DURATION_SECONDS
        ).getOrElse { error ->
            AppLogger.w("转写", "长音频切片失败，回退单文件上传: ${error.message}", error)
            chunkDir.deleteRecursively()
            onProgress?.invoke("长音频切片失败，回退整段转写")
            return Pair(
                chunkDir,
                emptyList()
            )
        }

        if (chunks.isEmpty()) {
            AppLogger.w("转写", "长音频切片结果为空，回退单文件上传")
            onProgress?.invoke("切片数量不足，回退整段转写")
            chunkDir.deleteRecursively()
            return Pair(
                chunkDir,
                emptyList()
            )
        }

        persistTranscriptionCheckpoint(
            TranscriptionCheckpoint(
                recordId = record.id,
                sourcePathHash = sourceFingerprint.pathHash,
                sourceSize = sourceFingerprint.size,
                sourceLastModified = sourceFingerprint.lastModified,
                chunkDirPath = chunkDir.absolutePath,
                totalChunks = chunks.size,
                nextChunkIndex = 0,
                mergedText = ""
            )
        )
        AppLogger.i("转写", "长音频切片成功: ${chunks.size} 段")
        return chunkDir to chunks
    }

    private fun buildSourceFingerprint(sourceFile: File): SourceFingerprint {
        return SourceFingerprint(
            pathHash = sha256(sourceFile.absolutePath),
            size = sourceFile.length(),
            lastModified = sourceFile.lastModified()
        )
    }

    private fun isCheckpointCompatible(
        checkpoint: TranscriptionCheckpoint,
        recordId: Long,
        sourceFingerprint: SourceFingerprint
    ): Boolean {
        if (checkpoint.version != CHECKPOINT_VERSION) return false
        if (checkpoint.recordId != recordId) return false
        if (checkpoint.sourcePathHash != sourceFingerprint.pathHash) return false
        if (checkpoint.sourceSize != sourceFingerprint.size) return false
        if (checkpoint.sourceLastModified != sourceFingerprint.lastModified) return false
        if (checkpoint.totalChunks <= 0) return false
        return checkpoint.nextChunkIndex in 0..checkpoint.totalChunks
    }

    private fun loadChunksFromCheckpoint(checkpoint: TranscriptionCheckpoint): List<AudioChunk>? {
        val chunkDir = File(checkpoint.chunkDirPath)
        if (!chunkDir.exists() || !chunkDir.isDirectory) {
            return null
        }

        val indexedFiles = chunkDir
            .listFiles()
            ?.filter { it.isFile }
            ?.mapNotNull { file ->
                parseChunkIndex(file.name)?.let { index -> index to file }
            }
            ?.sortedBy { it.first }
            ?: return null

        if (indexedFiles.size != checkpoint.totalChunks) {
            return null
        }

        val expectedIndexes = (1..checkpoint.totalChunks).toList()
        if (indexedFiles.map { it.first } != expectedIndexes) {
            return null
        }

        return indexedFiles.map { (index, file) ->
            val startMs = (index - 1L) * AUDIO_CHUNK_DURATION_SECONDS * 1000L
            val endMs = index.toLong() * AUDIO_CHUNK_DURATION_SECONDS * 1000L
            AudioChunk(
                file = file,
                startMs = startMs,
                endMs = endMs
            )
        }
    }

    private fun parseChunkIndex(fileName: String): Int? {
        val match = Regex("^chunk_(\\d+)\\.[A-Za-z0-9]+$").matchEntire(fileName) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun persistTranscriptionCheckpoint(checkpoint: TranscriptionCheckpoint) {
        val checkpointFile = getCheckpointFile(checkpoint.recordId)
        val tempFile = File(checkpointFile.parentFile, "${checkpointFile.name}.tmp")
        val payload = checkpointSerializer.toJson(checkpoint)
        tempFile.writeText(payload)

        val renamed = tempFile.renameTo(checkpointFile)
        if (!renamed) {
            checkpointFile.writeText(payload)
            tempFile.delete()
        }
    }

    private fun loadTranscriptionCheckpoint(recordId: Long): TranscriptionCheckpoint? {
        val checkpointFile = getCheckpointFile(recordId)
        if (!checkpointFile.exists()) {
            return null
        }
        return runCatching {
            checkpointSerializer.fromJson(
                checkpointFile.readText(),
                TranscriptionCheckpoint::class.java
            )
        }.onFailure { error ->
            AppLogger.w("转写", "读取检查点失败，已忽略: recordId=$recordId", error)
        }.getOrNull()
    }

    private fun clearTranscriptionCheckpoint(recordId: Long, knownChunkDir: File? = null) {
        val checkpoint = loadTranscriptionCheckpoint(recordId)
        val checkpointFile = getCheckpointFile(recordId)

        val chunkDirs = linkedSetOf<File>()
        knownChunkDir?.let(chunkDirs::add)
        checkpoint?.chunkDirPath?.takeIf { it.isNotBlank() }?.let { chunkDirs += File(it) }
        chunkDirs += buildChunkDirectory(recordId)

        chunkDirs.forEach { dir ->
            runCatching {
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }.onFailure { error ->
                AppLogger.w("转写", "清理切片目录失败: ${dir.absolutePath}", error)
            }
        }

        runCatching {
            if (checkpointFile.exists()) {
                checkpointFile.delete()
            }
        }.onFailure { error ->
            AppLogger.w("转写", "清理检查点文件失败: ${checkpointFile.absolutePath}", error)
        }
    }

    private fun getCheckpointFile(recordId: Long): File {
        return File(checkpointRootDir, "record_$recordId.json")
    }

    private fun buildChunkDirectory(recordId: Long): File {
        return File(context.cacheDir, "transcribe_chunks/record_$recordId")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private suspend fun requestTranscriptionWithRetry(
        requestFile: File,
        apiKey: String,
        requestLabel: String,
        onProgress: (suspend (String) -> Unit)? = null
    ): StepFunAsrResponse {
        var lastError: Exception? = null

        for (attempt in 1..TRANSCRIBE_RETRY_MAX_ATTEMPTS) {
            val suffix = "(第${attempt}次)"
            try {
                AppLogger.i("转写", "调用阶跃星辰ASR API: $requestLabel $suffix")
                onProgress?.invoke("上传中 $suffix")

                val response = withTimeout(ASR_SINGLE_ATTEMPT_TIMEOUT_MS) {
                    apiService.transcribeAudioFile(
                        authorization = "Bearer $apiKey",
                        file = MultipartBody.Part.createFormData(
                            "file",
                            requestFile.name,
                            requestFile.asRequestBody("audio/*".toMediaTypeOrNull())
                        ),
                        model = "step-asr".toRequestBody("text/plain".toMediaTypeOrNull()),
                        responseFormat = "json".toRequestBody("text/plain".toMediaTypeOrNull())
                    )
                }

                if (response.isSuccessful && response.body() != null) {
                    return response.body()!!
                }

                val code = response.code()
                val errorBody = response.errorBody()?.string()
                AppLogger.e("转写", "API调用失败: $requestLabel - $code ${response.message()}")
                saveApiLog("transcribe_error", "请求: $requestLabel\n错误码: $code\n错误: $errorBody")

                if (code in RETRYABLE_HTTP_CODES && attempt < TRANSCRIBE_RETRY_MAX_ATTEMPTS) {
                    val delayMs = attempt * TRANSCRIBE_RETRY_DELAY_BASE_MS
                    onProgress?.invoke("服务繁忙，${delayMs / 1000}秒后重试")
                    delay(delayMs)
                    continue
                }

                throw Exception("API错误 $code: ${response.message()}\n$errorBody")
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) {
                    throw e
                }

                val retryableError = normalizeRetryableException(e)
                if (!isRetryableNetworkException(retryableError)) {
                    throw retryableError
                }
                lastError = retryableError
                if (attempt < TRANSCRIBE_RETRY_MAX_ATTEMPTS) {
                    val delayMs = attempt * TRANSCRIBE_RETRY_DELAY_BASE_MS
                    AppLogger.w(
                        "转写",
                        "$requestLabel 网络异常${
                            if (attempt < TRANSCRIBE_RETRY_MAX_ATTEMPTS) "，${delayMs / 1000}秒后重试" else ""
                        }",
                        retryableError
                    )
                    onProgress?.invoke("网络波动，${delayMs / 1000}秒后重试")
                    delay(delayMs)
                }
            }
        }

        throw lastError ?: Exception("转写请求失败，已重试${TRANSCRIBE_RETRY_MAX_ATTEMPTS}次")
    }

    private fun normalizeRetryableException(error: Exception): Exception {
        if (error is TimeoutCancellationException) {
            return SocketTimeoutException("ASR请求超时").apply {
                initCause(error)
            }
        }
        return error
    }

    private fun isRetryableNetworkException(error: Exception): Boolean {
        return when (error) {
            is UnknownHostException,
            is SocketTimeoutException,
            is SocketException,
            is IOException -> true
            else -> false
        }
    }

    /**
     * 生成会谈纪要
     */
    suspend fun generateMeetingMinute(transcript: TranscriptEntity, record: CallRecordEntity): Result<MeetingMinuteEntity> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("生成纪要", "开始生成会谈纪要，转写ID: ${transcript.id}")
            AppLogger.d("生成纪要", "转写文本长度: ${transcript.fullText.length}字符")
            
            // 获取联系人名称
            val contactName = record.contactName ?: record.phoneNumber ?: "对方"
            AppLogger.d("生成纪要", "参与者: 我 和 $contactName")
            
            // 构建 Prompt
            val prompt = buildMeetingMinutePrompt(transcript, record)
            AppLogger.d("生成纪要", "Prompt长度: ${prompt.length}字符")
            
            // 保存请求信息
            saveApiLog("minute_request", "转写ID: ${transcript.id}\nPrompt:\n$prompt")
            
            val request = StepFunLlmRequest(
                model = "step-3.5-flash",
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = """你是一名专业的商务/个人事务会谈纪要助手。你的任务是根据通话录音的转写文本，生成高质量的结构化会谈纪要。

## 输出要求
- **语言**：全部使用中文输出
- **格式**：仅返回合法的 JSON 对象，不要添加任何 Markdown 代码块标记、注释或额外文字
- **严格遵循**下方 JSON Schema

## JSON 格式
{
  "title": "(string) 会谈标题，10-30字，精炼概括核心议题",
  "summary": "(string) 会谈摘要，100-200字，涵盖背景、讨论要点和结论",
  "keyPoints": ["(string) 关键要点，3-7条，每条15-50字，按重要性排序"],
  "participants": [{"name": "(string) 参与者姓名", "role": "(string|null) 角色/职务", "speakerId": "(string) 说话人标识"}],
  "actionItems": [{"description": "(string) 待办事项描述", "assignee": "(string|null) 负责人", "deadline": "(string|null) 截止日期", "priority": "(enum: HIGH|MEDIUM|LOW) 优先级"}],
  "fullContent": "(string) 整理后的完整会谈内容，保留关键细节，按逻辑分段"
}

## 注意事项
- 如果转写文本质量较低或内容较短，仍需尽力提取有价值信息
- 待办事项的 priority 只能是 HIGH、MEDIUM、LOW 三者之一
- participants 中至少包含通话双方
- 如果无法识别具体待办事项，actionItems 返回空数组 []"""
                    ),
                    ChatMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                temperature = 0.5,
                max_tokens = 8192
            )

            val apiKey = requireApiKey()
            
            AppLogger.i("生成纪要", "调用阶跃星辰LLM API")
            
            // Retry up to 3 times for network errors (DNS resolution, timeout, etc.)
            var lastException: Exception? = null
            var response: retrofit2.Response<com.callrecord.manager.data.remote.StepFunLlmResponse>? = null
            for (attempt in 1..3) {
                try {
                    response = apiService.chatCompletion(
                        authorization = "Bearer $apiKey",
                        request = request
                    )
                    lastException = null
                    break // Success, exit retry loop
                } catch (e: java.net.UnknownHostException) {
                    lastException = e
                    AppLogger.w("生成纪要", "网络请求失败(第${attempt}次): DNS解析失败，${if (attempt < 3) "${3-attempt}秒后重试" else "放弃重试"}", e)
                    if (attempt < 3) kotlinx.coroutines.delay(attempt * 3000L)
                } catch (e: java.net.SocketException) {
                    lastException = e
                    AppLogger.w("生成纪要", "网络请求失败(第${attempt}次): 连接异常，${if (attempt < 3) "${3-attempt}秒后重试" else "放弃重试"}", e)
                    if (attempt < 3) kotlinx.coroutines.delay(attempt * 3000L)
                } catch (e: java.io.IOException) {
                    lastException = e
                    AppLogger.w("生成纪要", "网络请求失败(第${attempt}次): IO异常，${if (attempt < 3) "${3-attempt}秒后重试" else "放弃重试"}", e)
                    if (attempt < 3) kotlinx.coroutines.delay(attempt * 3000L)
                }
            }
            
            if (lastException != null || response == null) {
                throw lastException ?: Exception("网络请求失败，已重试3次")
            }

            AppLogger.d("生成纪要", "API响应码: ${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val llmResponse = response.body()!!
                val content = llmResponse.choices.firstOrNull()?.message?.content
                    ?: throw Exception("Empty response from LLM")

                AppLogger.i("生成纪要", "LLM生成成功，响应长度: ${content.length}字符")
                AppLogger.d("生成纪要", "Token使用: ${llmResponse.usage.totalTokens}")
                
                // 保存响应
                saveApiLog("minute_response", "转写ID: ${transcript.id}\n响应内容:\n$content")
                
                // 解析 JSON 响应
                AppLogger.d("生成纪要", "开始解析JSON响应")
                val minuteResult = parseMinuteResult(content)
                AppLogger.i("生成纪要", "解析成功: 标题=${minuteResult.title}")
                
                // 创建纪要实体
                val minute = MeetingMinuteEntity(
                    transcriptId = transcript.id,
                    title = minuteResult.title,
                    summary = minuteResult.summary,
                    keyPoints = minuteResult.keyPoints,
                    participants = minuteResult.participants.map {
                        Participant(it.name, it.role, it.speakerId)
                    },
                    actionItems = minuteResult.actionItems.map {
                        ActionItem(
                            it.description,
                            it.assignee,
                            it.deadline,
                            Priority.valueOf(it.priority)
                        )
                    },
                    fullContent = minuteResult.fullContent
                )

                val minuteId = meetingMinuteDao.insertMinute(minute)
                AppLogger.i("生成纪要", "纪要保存成功，ID: $minuteId")
                Result.success(minute.copy(id = minuteId))
            } else {
                val errorBody = response.errorBody()?.string()
                AppLogger.e("生成纪要", "API调用失败: ${response.code()} - ${response.message()}")
                AppLogger.e("生成纪要", "错误详情: $errorBody")
                
                // 保存错误响应
                saveApiLog("minute_error", "转写ID: ${transcript.id}\n错误码: ${response.code()}\n错误: $errorBody")
                
                Result.failure(Exception("API错误 ${response.code()}: ${response.message()}\n$errorBody"))
            }
        } catch (e: Exception) {
            AppLogger.e("生成纪要", "生成纪要过程发生异常", e)
            saveApiLog("minute_exception", "转写ID: ${transcript.id}\n异常: ${e.message}\n${e.stackTraceToString()}")
            Result.failure(e)
        }
    }

    /**
     * Build prompt for meeting minute generation.
     * Includes recording metadata context (contact, time, duration) to help LLM.
     */
    private fun buildMeetingMinutePrompt(transcript: TranscriptEntity, record: CallRecordEntity): String {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val contactName = record.contactName ?: record.phoneNumber ?: "未知联系人"
        val recordTime = dateFormat.format(java.util.Date(record.recordTime))
        val durationMinutes = record.duration / 60
        val durationSeconds = record.duration % 60
        val durationStr = if (durationMinutes > 0) "${durationMinutes}分${durationSeconds}秒" else "${durationSeconds}秒"

        val speakerTexts = transcript.speakers.groupBy { it.speaker }
            .map { (speaker, segments) ->
                "$speaker:\n" + segments.joinToString("\n") { "  ${it.text}" }
            }
            .joinToString("\n\n")

        return """
## 录音元数据
- 联系人：$contactName
- 通话时间：$recordTime
- 通话时长：$durationStr

## 转写内容
$speakerTexts

## 完整文本
${transcript.fullText}

请根据以上信息生成结构化的会谈纪要（JSON格式）。
        """.trimIndent()
    }

    /**
     * Parse LLM response into MeetingMinuteResult.
     * Attempts to fix common JSON formatting issues before falling back to defaults.
     */
    private fun parseMinuteResult(content: String): MeetingMinuteResult {
        // Step 1: Strip Markdown code block wrappers if present
        var cleaned = content.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").trimStart()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").trimStart()
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```").trimEnd()
        }

        // Step 2: Extract JSON object
        val jsonStart = cleaned.indexOf("{")
        val jsonEnd = cleaned.lastIndexOf("}") + 1
        var jsonContent = if (jsonStart >= 0 && jsonEnd > jsonStart) {
            cleaned.substring(jsonStart, jsonEnd)
        } else {
            cleaned
        }

        // Step 3: Try to fix common JSON issues
        // Fix trailing commas before } or ] (use simple loop to avoid Android ICU regex issues)
        jsonContent = fixTrailingCommas(jsonContent)

        return try {
            val result = com.google.gson.Gson().fromJson(jsonContent, MeetingMinuteResult::class.java)
            // Validate priority enum values
            val fixedActionItems = result.actionItems.map { item ->
                val validPriority = when (item.priority.uppercase()) {
                    "HIGH", "MEDIUM", "LOW" -> item.priority.uppercase()
                    else -> "MEDIUM"
                }
                item.copy(priority = validPriority)
            }
            result.copy(actionItems = fixedActionItems)
        } catch (e: Exception) {
            AppLogger.w("解析纪要", "JSON解析失败，尝试修复后仍无法解析: ${e.message}")
            AppLogger.d("解析纪要", "原始内容: ${jsonContent.take(500)}")
            // Fallback to default structure
            MeetingMinuteResult(
                title = "会谈纪要",
                summary = content.take(200),
                keyPoints = listOf("详见完整内容"),
                participants = emptyList(),
                actionItems = emptyList(),
                fullContent = content
            )
        }
    }

    /**
     * Fix trailing commas in JSON content without using regex (Android ICU regex has issues with certain patterns).
     */
    private fun fixTrailingCommas(json: String): String {
        val sb = StringBuilder(json.length)
        var inString = false
        var escape = false
        var i = 0
        while (i < json.length) {
            val c = json[i]
            if (escape) {
                sb.append(c)
                escape = false
                i++
                continue
            }
            if (c == '\\') {
                sb.append(c)
                escape = true
                i++
                continue
            }
            if (c == '"') {
                inString = !inString
                sb.append(c)
                i++
                continue
            }
            if (!inString && c == ',') {
                // Look ahead: skip whitespace, check if next non-ws char is } or ]
                var j = i + 1
                while (j < json.length && json[j].isWhitespace()) j++
                if (j < json.length && (json[j] == '}' || json[j] == ']')) {
                    // Skip this trailing comma
                    i++
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /**
     * Clean raw JSON artifacts from LLM fallback content.
     * When JSON parsing fails, the raw LLM response may contain JSON structure characters
     * like braces, brackets, quotes, and field names that are confusing to read.
     * This method extracts readable text from the raw content.
     */
    private fun cleanJsonArtifacts(content: String): String {
        // Try to extract values from JSON fields even if parsing failed
        val extractedParts = mutableListOf<String>()

        // Try to extract known text fields
        val fieldPatterns = listOf("title", "summary", "fullContent", "trendAnalysis", "currentStatus")
        for (field in fieldPatterns) {
            val startKey = "\"$field\""
            val idx = content.indexOf(startKey)
            if (idx >= 0) {
                // Find the colon after the key
                val colonIdx = content.indexOf(':', idx + startKey.length)
                if (colonIdx >= 0) {
                    // Find the opening quote of the value
                    val quoteStart = content.indexOf('"', colonIdx + 1)
                    if (quoteStart >= 0) {
                        // Find the closing quote (handle escaped quotes)
                        var j = quoteStart + 1
                        val sb = StringBuilder()
                        while (j < content.length) {
                            if (content[j] == '\\' && j + 1 < content.length) {
                                sb.append(content[j + 1])
                                j += 2
                            } else if (content[j] == '"') {
                                break
                            } else {
                                sb.append(content[j])
                                j++
                            }
                        }
                        val value = sb.toString().trim()
                        if (value.isNotBlank() && value.length > 5) {
                            extractedParts.add(value)
                        }
                    }
                }
            }
        }

        // Also try to extract keyPoints array values
        val keyPointsIdx = content.indexOf("\"keyPoints\"")
        if (keyPointsIdx >= 0) {
            val bracketStart = content.indexOf('[', keyPointsIdx)
            if (bracketStart >= 0) {
                val bracketEnd = content.indexOf(']', bracketStart)
                val section = if (bracketEnd >= 0) content.substring(bracketStart, bracketEnd + 1) else content.substring(bracketStart)
                // Extract string values from array
                var k = 0
                while (k < section.length) {
                    if (section[k] == '"') {
                        val sb = StringBuilder()
                        k++
                        while (k < section.length && section[k] != '"') {
                            if (section[k] == '\\' && k + 1 < section.length) {
                                sb.append(section[k + 1])
                                k += 2
                            } else {
                                sb.append(section[k])
                                k++
                            }
                        }
                        val value = sb.toString().trim()
                        if (value.isNotBlank() && value.length > 3) {
                            extractedParts.add("• $value")
                        }
                    }
                    k++
                }
            }
        }

        return if (extractedParts.isNotEmpty()) {
            extractedParts.joinToString("\n\n")
        } else {
            // Last resort: strip JSON syntax characters
            content
                .replace(Regex("\"[a-zA-Z_]+\"\\s*:"), "")
                .replace(Regex("[{}\\[\\]]"), "")
                .replace(Regex(",\\s*\n"), "\n")
                .replace(Regex("\""), "")
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }
    }

    /**
     * Get transcript by record ID
     */
    suspend fun getTranscriptByRecordId(recordId: Long): TranscriptEntity? {
        return transcriptDao.getTranscriptByRecordId(recordId)
    }

    /**
     * 修复无活跃 Work 的孤儿转写状态，避免条目永久停留在“转写中”。
     */
    suspend fun repairOrphanProcessingTranscript(
        recordId: Long,
        hasActiveWork: Boolean
    ): TranscriptEntity? = withContext(Dispatchers.IO) {
        val latest = transcriptDao.getTranscriptByRecordId(recordId) ?: return@withContext null
        val status = latest.status
        if (hasActiveWork || (status != TranscriptStatus.PROCESSING && status != TranscriptStatus.PENDING)) {
            return@withContext latest
        }

        val now = System.currentTimeMillis()
        if (now - latest.updateTime < PROCESSING_ORPHAN_TIMEOUT_MS) {
            return@withContext latest
        }

        val fixed = latest.copy(
            status = TranscriptStatus.FAILED,
            updateTime = now
        )
        transcriptDao.updateTranscript(fixed)
        AppLogger.w(
            "转写",
            "检测到孤儿 PROCESSING 状态并已修复为 FAILED: recordId=$recordId, transcriptId=${latest.id}"
        )
        fixed
    }

    /**
     * Get transcript by its ID
     */
    suspend fun getTranscriptById(transcriptId: Long): TranscriptEntity? {
        return transcriptDao.getTranscriptById(transcriptId)
    }

    /**
     * Get meeting minute by transcript ID
     */
    suspend fun getMinuteByTranscriptId(transcriptId: Long): MeetingMinuteEntity? {
        return meetingMinuteDao.getMinuteByTranscriptId(transcriptId)
    }

    /**
     * Delete meeting minute by transcript ID (used before regeneration)
     */
    suspend fun deleteMinuteByTranscriptId(transcriptId: Long) {
        meetingMinuteDao.deleteMinuteByTranscriptId(transcriptId)
    }

    /**
     * Delete a meeting minute entity
     */
    suspend fun deleteMinute(minute: MeetingMinuteEntity) {
        meetingMinuteDao.deleteMinute(minute)
    }

    /**
     * 获取所有纪要
     */
    fun getAllMinutes(): Flow<List<MeetingMinuteEntity>> {
        return meetingMinuteDao.getAllMinutes()
    }

    /**
     * Get all minutes with associated contact name for grouped display
     */
    fun getAllMinutesWithContact(): Flow<List<MinuteWithContact>> {
        return meetingMinuteDao.getAllMinutesWithContact()
    }

    /**
     * 搜索纪要
     */
    fun searchMinutes(query: String): Flow<List<MeetingMinuteEntity>> {
        return meetingMinuteDao.searchMinutes(query)
    }
    
    /**
     * 为已转写的录音重新生成纪要
     */
    suspend fun regenerateMinutesForTranscribed(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("重新生成纪要", "开始检查已转写但没有纪要的录音")
            
            // 获取所有已转写的录音
            val transcribedRecords = callRecordDao.getAllRecords().first()
                .filter { it.isTranscribed && it.transcriptId != null }
            
            AppLogger.d("重新生成纪要", "找到 ${transcribedRecords.size} 个已转写的录音")
            
            var generatedCount = 0
            
            for (record in transcribedRecords) {
                // 检查是否已有纪要
                val existingMinute = meetingMinuteDao.getMinuteByTranscriptId(record.transcriptId!!)
                if (existingMinute == null) {
                    AppLogger.i("重新生成纪要", "录音 ${record.fileName} 没有纪要，开始生成")
                    
                    // 获取转写记录
                    val transcript = transcriptDao.getTranscriptById(record.transcriptId!!)
                    if (transcript != null && transcript.status == TranscriptStatus.COMPLETED) {
                        generateMeetingMinute(transcript, record)
                            .onSuccess {
                                generatedCount++
                                AppLogger.i("重新生成纪要", "成功为录音 ${record.fileName} 生成纪要")
                            }
                            .onFailure { e ->
                                AppLogger.e("重新生成纪要", "为录音 ${record.fileName} 生成纪要失败", e)
                            }
                    }
                }
            }
            
            AppLogger.i("重新生成纪要", "完成，共生成 $generatedCount 个纪要")
            Result.success(generatedCount)
        } catch (e: Exception) {
            AppLogger.e("重新生成纪要", "重新生成纪要失败", e)
            Result.failure(e)
        }
    }

    /**
     * Generate a timeline brief from multiple meeting minutes.
     * Minutes are sorted by time and sent to step-3.5-flash with a dedicated prompt.
     */
    suspend fun generateTimelineBrief(
        minutesWithContact: List<MinuteWithContact>
    ): Result<com.callrecord.manager.ui.screen.TimelineBriefResult> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("脉络简报", "开始生成脉络简报，共 ${minutesWithContact.size} 条纪要")

            // Sort by recordTime ascending (earliest first), falling back to createTime
            val sorted = minutesWithContact.sortedBy { it.recordTime ?: it.createTime }

            // Build user prompt with each minute annotated by date and contact
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            val minuteEntries = sorted.mapIndexed { idx, m ->
                val date = dateFormat.format(java.util.Date(m.recordTime ?: m.createTime))
                val contact = m.contactName ?: "未知联系人"
                """
### 第${idx + 1}条纪要
- 日期：$date
- 联系人：$contact
- 标题：${m.title}
- 摘要：${m.summary}
- 关键要点：
${m.keyPoints.joinToString("\n") { "  - $it" }}
- 待办事项：
${m.actionItems.joinToString("\n") { "  - [${it.priority}] ${it.description}${if (it.assignee != null) "（${it.assignee}）" else ""}" }}
- 完整内容：
${m.fullContent}
                """.trimIndent()
            }.joinToString("\n\n---\n\n")

            val userPrompt = """
以下是按时间排序的多条会谈纪要，它们记录了同一事件或相关事件在不同时间的讨论。请根据这些纪要生成一份事件发展脉络简报。

$minuteEntries

请生成脉络简报（JSON格式）。
            """.trimIndent()

            val request = StepFunLlmRequest(
                model = "step-3.5-flash",
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = """你是一名专业的事件分析师。你的任务是根据多条不同时间的会谈纪要，梳理事件发展脉络，生成一份结构化的事件发展脉络简报。

## 输出要求
- **语言**：全部使用中文输出
- **格式**：仅返回合法的 JSON 对象，不要添加任何 Markdown 代码块标记、注释或额外文字
- **严格遵循**下方 JSON Schema

## JSON 格式
{
  "title": "(string) 事件总标题，10-30字，精炼概括贯穿多次会谈的核心事件",
  "timelineNodes": [
    {
      "date": "(string) 该次会谈的日期时间",
      "summary": "(string) 该时间节点的关键进展摘要，50-100字"
    }
  ],
  "trendAnalysis": "(string) 事件发展趋势分析，100-200字，分析事件从开始到现在的演变趋势",
  "currentStatus": "(string) 当前最新状态总结，50-150字",
  "followUpSuggestions": ["(string) 后续跟进建议，3-5条，每条20-50字"]
}

## 注意事项
- timelineNodes 必须按时间从早到晚排序
- 每条纪要对应一个 timelineNode
- 重点提取每次会谈中事件的关键状态变化
- 分析事件发展趋势时要体现时间维度的变化
- followUpSuggestions 要基于事件当前状态给出具体可操作的建议"""
                    ),
                    ChatMessage(
                        role = "user",
                        content = userPrompt
                    )
                ),
                temperature = 0.5,
                max_tokens = 8192
            )

            val apiKey = requireApiKey()
            
            saveApiLog("timeline_request", "纪要数量: ${sorted.size}\nPrompt长度: ${userPrompt.length}")

            // Retry up to 3 times for network errors
            var lastException: Exception? = null
            var response: retrofit2.Response<StepFunLlmResponse>? = null
            for (attempt in 1..3) {
                try {
                    response = apiService.chatCompletion(
                        authorization = "Bearer $apiKey",
                        request = request
                    )
                    lastException = null
                    break
                } catch (e: java.net.UnknownHostException) {
                    lastException = e
                    AppLogger.w("脉络简报", "网络请求失败(第${attempt}次): DNS解析失败", e)
                    if (attempt < 3) kotlinx.coroutines.delay(attempt * 3000L)
                } catch (e: java.net.SocketException) {
                    lastException = e
                    AppLogger.w("脉络简报", "网络请求失败(第${attempt}次): 连接异常", e)
                    if (attempt < 3) kotlinx.coroutines.delay(attempt * 3000L)
                } catch (e: java.io.IOException) {
                    lastException = e
                    AppLogger.w("脉络简报", "网络请求失败(第${attempt}次): IO异常", e)
                    if (attempt < 3) kotlinx.coroutines.delay(attempt * 3000L)
                }
            }

            if (lastException != null || response == null) {
                throw lastException ?: Exception("网络请求失败，已重试3次")
            }

            if (response.isSuccessful && response.body() != null) {
                val llmResponse = response.body()!!
                val content = llmResponse.choices.firstOrNull()?.message?.content
                    ?: throw Exception("Empty response from LLM")

                AppLogger.i("脉络简报", "LLM生成成功，响应长度: ${content.length}字符")
                saveApiLog("timeline_response", "响应:\n$content")

                val briefResult = parseTimelineBriefResult(content)
                AppLogger.i("脉络简报", "解析成功: ${briefResult.title}")
                Result.success(briefResult)
            } else {
                val errorBody = response.errorBody()?.string()
                AppLogger.e("脉络简报", "API调用失败: ${response.code()} - ${response.message()}")
                saveApiLog("timeline_error", "错误码: ${response.code()}\n$errorBody")
                Result.failure(Exception("API错误 ${response.code()}: ${response.message()}\n$errorBody"))
            }
        } catch (e: Exception) {
            AppLogger.e("脉络简报", "生成脉络简报异常", e)
            saveApiLog("timeline_exception", "异常: ${e.message}\n${e.stackTraceToString()}")
            Result.failure(e)
        }
    }

    /**
     * Parse LLM response into TimelineBriefResult.
     * Uses same JSON-cleaning strategy as parseMinuteResult.
     */
    private fun parseTimelineBriefResult(content: String): com.callrecord.manager.ui.screen.TimelineBriefResult {
        var cleaned = content.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").trimStart()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").trimStart()
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```").trimEnd()
        }

        val jsonStart = cleaned.indexOf("{")
        val jsonEnd = cleaned.lastIndexOf("}") + 1
        var jsonContent = if (jsonStart >= 0 && jsonEnd > jsonStart) {
            cleaned.substring(jsonStart, jsonEnd)
        } else {
            cleaned
        }

        // Fix trailing commas (use same safe method as parseMinuteResult)
        jsonContent = fixTrailingCommas(jsonContent)

        return try {
            val gson = com.google.gson.Gson()
            gson.fromJson(jsonContent, com.callrecord.manager.ui.screen.TimelineBriefResult::class.java)
        } catch (e: Exception) {
            AppLogger.w("脉络简报", "JSON解析失败: ${e.message}")
            AppLogger.d("脉络简报", "原始内容: ${jsonContent.take(500)}")
            com.callrecord.manager.ui.screen.TimelineBriefResult(
                title = "事件脉络简报（解析异常）",
                timelineNodes = emptyList(),
                trendAnalysis = content.take(300),
                currentStatus = "JSON解析失败，请重试",
                followUpSuggestions = listOf("建议重新选择纪要并生成简报")
            )
        }
    }

    /**
     * Update contact name for all records with the given phone number.
     */
    suspend fun updateContactName(phoneNumber: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            callRecordDao.updateContactNameByPhone(phoneNumber, newName)
            AppLogger.i("编辑联系人", "已将手机号 $phoneNumber 的联系人名称更新为: $newName")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e("编辑联系人", "更新联系人名称失败", e)
            Result.failure(e)
        }
    }

    /**
     * Update transcript text after user editing.
     * Merges speakers into a single "Edited" segment and updates fullText.
     */
    suspend fun updateTranscriptText(transcriptId: Long, editedText: String): Result<TranscriptEntity> = withContext(Dispatchers.IO) {
        try {
            val transcript = transcriptDao.getTranscriptById(transcriptId)
                ?: return@withContext Result.failure(Exception("转写记录不存在"))

            val updatedSpeakers = listOf(
                SpeakerSegment(
                    speaker = "Edited",
                    text = editedText,
                    startTime = 0.0,
                    endTime = 0.0
                )
            )

            val updatedTranscript = transcript.copy(
                fullText = editedText,
                speakers = updatedSpeakers,
                updateTime = System.currentTimeMillis()
            )

            transcriptDao.updateTranscript(updatedTranscript)
            AppLogger.i("编辑转写", "转写内容已更新，ID: $transcriptId, 文本长度: ${editedText.length}")

            Result.success(updatedTranscript)
        } catch (e: Exception) {
            AppLogger.e("编辑转写", "更新转写内容失败", e)
            Result.failure(e)
        }
    }
}
