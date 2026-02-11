package com.callrecord.manager.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.ContactsContract
import com.callrecord.manager.data.local.*
import com.callrecord.manager.data.remote.*
import com.callrecord.manager.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

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
                    
                    val files = dir.listFiles { file ->
                        file.extension.lowercase() in listOf("mp3", "m4a", "wav", "amr", "3gp", "aac", "ogg")
                    }
                    
                    files?.forEach { file ->
                        totalFiles++
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
            val existingKeys = existingRecords.map { "${it.contactName ?: it.phoneNumber}|${it.recordTime}" }.toSet()
            val newRecordings = recordings.filter { record ->
                val key = "${record.contactName ?: record.phoneNumber}|${record.recordTime}"
                key !in existingKeys
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
            // 删除文件
            val file = File(record.filePath)
            if (file.exists()) {
                file.delete()
            }
            
            // 删除数据库记录
            callRecordDao.deleteRecord(record)
            
            // 删除关联的转写和纪要
            record.transcriptId?.let { transcriptId ->
                transcriptDao.deleteTranscriptByRecordId(record.id)
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
    suspend fun transcribeRecord(record: CallRecordEntity): Result<TranscriptEntity> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i("转写", "开始转写录音: ${record.fileName}")
            AppLogger.d("转写", "录音ID: ${record.id}, 文件路径: ${record.filePath}")
            
            // 检查文件是否存在
            val file = File(record.filePath)
            if (!file.exists()) {
                AppLogger.e("转写", "录音文件不存在: ${record.filePath}")
                return@withContext Result.failure(Exception("录音文件不存在"))
            }
            
            val fileSizeMB = file.length() / (1024.0 * 1024.0)
            AppLogger.d("转写", "文件大小: ${"%.2f".format(fileSizeMB)}MB")
            
            // File size check: reject files over 25MB
            if (fileSizeMB > 25.0) {
                AppLogger.e("转写", "文件过大: ${"%.2f".format(fileSizeMB)}MB，超过25MB限制")
                return@withContext Result.failure(Exception("文件过大（${"%.1f".format(fileSizeMB)}MB），请压缩至25MB以内后重试"))
            }
            
            // 保存上传的文件信息
            saveApiLog("transcribe_request", "录音ID: ${record.id}\n文件: ${file.name}\n大小: ${file.length()}字节")
            
            // 更新状态为处理中
            val transcript = TranscriptEntity(
                recordId = record.id,
                fullText = "",
                speakers = emptyList(),
                status = TranscriptStatus.PROCESSING
            )
            val transcriptId = transcriptDao.insertTranscript(transcript)
            AppLogger.i("转写", "创建转写记录，ID: $transcriptId")

            // 准备文件上传
            AppLogger.d("转写", "准备上传文件到API")
            
            val apiKey = requireApiKey()
            
            // Retry up to 3 times for network errors (DNS resolution, timeout, etc.)
            var lastException: Exception? = null
            var response: retrofit2.Response<StepFunAsrResponse>? = null
            for (attempt in 1..3) {
                try {
                    val requestFile = file.asRequestBody("audio/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                    val modelBody = "step-asr".toRequestBody("text/plain".toMediaTypeOrNull())
                    val responseFormatBody = "json".toRequestBody("text/plain".toMediaTypeOrNull())

                    AppLogger.i("转写", "调用阶跃星辰ASR API (第${attempt}次)")
                    response = apiService.transcribeAudioFile(
                        authorization = "Bearer $apiKey",
                        file = body,
                        model = modelBody,
                        responseFormat = responseFormatBody
                    )
                    lastException = null
                    break // Success, exit retry loop
                } catch (e: java.net.UnknownHostException) {
                    lastException = e
                    AppLogger.w("转写", "网络请求失败(第${attempt}次): DNS解析失败，${if (attempt < 3) "${attempt * 3}秒后重试" else "放弃重试"}", e)
                    if (attempt < 3) kotlinx.coroutines.delay(attempt * 3000L)
                } catch (e: java.net.SocketException) {
                    lastException = e
                    AppLogger.w("转写", "网络请求失败(第${attempt}次): 连接异常，${if (attempt < 3) "${attempt * 3}秒后重试" else "放弃重试"}", e)
                    if (attempt < 3) kotlinx.coroutines.delay(attempt * 3000L)
                } catch (e: java.io.IOException) {
                    lastException = e
                    AppLogger.w("转写", "网络请求失败(第${attempt}次): IO异常，${if (attempt < 3) "${attempt * 3}秒后重试" else "放弃重试"}", e)
                    if (attempt < 3) kotlinx.coroutines.delay(attempt * 3000L)
                }
            }
            
            if (lastException != null || response == null) {
                // Update transcript status to FAILED before throwing
                transcriptDao.updateTranscript(
                    transcript.copy(id = transcriptId, status = TranscriptStatus.FAILED)
                )
                throw lastException ?: Exception("网络请求失败，已重试3次")
            }

            AppLogger.d("转写", "API响应码: ${response.code()}")
            
            if (response.isSuccessful && response.body() != null) {
                val asrResponse = response.body()!!
                AppLogger.i("转写", "转写成功，文本长度: ${asrResponse.text.length}字符")
                
                // 保存 API 响应
                saveApiLog("transcribe_response", "录音ID: ${record.id}\n转写文本:\n${asrResponse.text}")
                
                // 由于 step-asr 不支持说话人分离，创建一个简单的分段
                val speakers = listOf(
                    SpeakerSegment(
                        speaker = "Speaker 1",
                        text = asrResponse.text,
                        startTime = 0.0,
                        endTime = 0.0
                    )
                )

                // 更新转写记录
                val updatedTranscript = transcript.copy(
                    id = transcriptId,
                    fullText = asrResponse.text,
                    speakers = speakers,
                    status = TranscriptStatus.COMPLETED,
                    updateTime = System.currentTimeMillis()
                )
                transcriptDao.updateTranscript(updatedTranscript)
                AppLogger.i("转写", "转写记录已更新")

                // 更新录音记录
                callRecordDao.updateRecord(
                    record.copy(
                        isTranscribed = true,
                        transcriptId = transcriptId
                    )
                )
                AppLogger.i("转写", "录音记录已标记为已转写")

                Result.success(updatedTranscript)
            } else {
                val errorBody = response.errorBody()?.string()
                AppLogger.e("转写", "API调用失败: ${response.code()} - ${response.message()}")
                AppLogger.e("转写", "错误详情: $errorBody")
                
                // 保存错误响应
                saveApiLog("transcribe_error", "录音ID: ${record.id}\n错误码: ${response.code()}\n错误: $errorBody")
                
                // 更新状态为失败
                transcriptDao.updateTranscript(
                    transcript.copy(
                        id = transcriptId,
                        status = TranscriptStatus.FAILED
                    )
                )
                Result.failure(Exception("API错误 ${response.code()}: ${response.message()}\n$errorBody"))
            }
        } catch (e: Exception) {
            AppLogger.e("转写", "转写过程发生异常", e)
            saveApiLog("transcribe_exception", "异常: ${e.message}\n${e.stackTraceToString()}")
            Result.failure(e)
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
