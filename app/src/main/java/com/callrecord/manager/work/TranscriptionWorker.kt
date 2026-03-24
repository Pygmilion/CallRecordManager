package com.callrecord.manager.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.callrecord.manager.data.local.AppDatabase
import com.callrecord.manager.data.remote.ApiClient
import com.callrecord.manager.data.repository.ApiKeyProvider
import com.callrecord.manager.data.repository.CallRecordRepository
import com.callrecord.manager.utils.AppLogger
import com.callrecord.manager.utils.TaskNotificationHelper
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

/**
 * 后台转写 Worker：支持“仅转写”或“转写+纪要”两种任务链路。
 */
class TranscriptionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val recordId = inputData.getLong(KEY_RECORD_ID, -1L)
        if (recordId <= 0L) {
            return failure("无效的录音ID")
        }
        val generateMinute = inputData.getBoolean(KEY_GENERATE_MINUTE, true)

        return try {
            TaskNotificationHelper.createChannel(applicationContext)
            trySetForeground(
                title = "后台任务启动中",
                text = "准备处理录音 #$recordId"
            )

            ApiKeyProvider.init(applicationContext)
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = CallRecordRepository(
                context = applicationContext,
                callRecordDao = database.callRecordDao(),
                transcriptDao = database.transcriptDao(),
                meetingMinuteDao = database.meetingMinuteDao(),
                apiService = ApiClient.createStepFunService { ApiKeyProvider.getApiKey() }
            )

            val record = database.callRecordDao().getRecordById(recordId)
                ?: return failure("录音不存在：$recordId")

            trySetForeground(
                title = "正在转写录音",
                text = record.fileName
            )
            AppLogger.i("TranscriptionWorker", "开始后台转写: ${record.fileName}, generateMinute=$generateMinute")

            val transcript = repository.transcribeRecord(record) { progressText ->
                trySetForeground(
                    title = "正在转写录音",
                    text = "${record.fileName} · $progressText"
                )
            }.getOrElse { error ->
                return handleTaskError(
                    step = "转写",
                    error = error
                )
            }

            if (!generateMinute) {
                AppLogger.i("TranscriptionWorker", "仅转写任务完成: ${record.fileName}")
                TaskNotificationHelper.showSuccessNotification(
                    applicationContext,
                    "转写完成: ${record.fileName}"
                )
                return terminalResult(
                    status = RESULT_STATUS_SUCCESS
                )
            }

            trySetForeground(
                title = "正在生成纪要",
                text = record.fileName
            )

            val minute = repository.generateMeetingMinute(transcript, record).getOrElse { error ->
                return handleTaskError(
                    step = "纪要生成",
                    error = error
                )
            }

            AppLogger.i("TranscriptionWorker", "后台任务完成: transcript=${transcript.id}, minute=${minute.id}")
            TaskNotificationHelper.showSuccessNotification(
                applicationContext,
                "纪要已生成: ${minute.title}"
            )
            terminalResult(
                status = RESULT_STATUS_SUCCESS
            )
        } catch (e: CancellationException) {
            AppLogger.w("TranscriptionWorker", "后台任务被系统取消: ${e.message}")
            handleTaskError(
                step = "后台任务",
                error = IllegalStateException("任务被取消：${e.message ?: "系统中断"}", e)
            )
        } catch (e: Exception) {
            handleTaskError(
                step = "后台任务",
                error = e
            )
        } finally {
            TaskNotificationHelper.cancelProgressNotification(applicationContext)
        }
    }

    private suspend fun trySetForeground(title: String, text: String) {
        if (!ENABLE_FOREGROUND_NOTIFICATION) {
            return
        }
        try {
            setForeground(
                TaskNotificationHelper.buildForegroundInfo(
                    applicationContext,
                    title,
                    text
                )
            )
        } catch (e: Exception) {
            if (isForegroundStartNotAllowed(e)) {
                // Android 14+ can deny background FGS starts; keep task running without foreground bridge.
                AppLogger.w("TranscriptionWorker", "前台通知受限，继续后台执行: ${e.message}")
                TaskNotificationHelper.cancelProgressNotification(applicationContext)
            } else {
                throw e
            }
        }
    }

    private fun handleTaskError(step: String, error: Throwable): Result {
        val reason = error.message ?: "未知错误"
        val message = "${step}失败: $reason"

        if (shouldRetry(error)) {
            AppLogger.w(
                "TranscriptionWorker",
                "$message，任务内重试已结束，跳过WorkManager重试以避免队列阻塞",
                error
            )
        } else {
            AppLogger.e("TranscriptionWorker", message, error)
        }
        TaskNotificationHelper.showFailureNotification(applicationContext, message)
        return terminalResult(
            status = RESULT_STATUS_FAILED,
            message = message
        )
    }

    private fun shouldRetry(error: Throwable): Boolean {
        var cursor: Throwable? = error
        while (cursor != null) {
            when (cursor) {
                is UnknownHostException,
                is SocketTimeoutException,
                is SocketException,
                is IOException -> return true
            }
            cursor = cursor.cause
        }

        val message = error.message?.lowercase(Locale.getDefault()).orEmpty()
        if (message.contains("timeout") ||
            message.contains("timed out") ||
            message.contains("connection reset") ||
            message.contains("software caused connection abort")
        ) {
            return true
        }

        val apiCode = extractApiErrorCode(error.message)
        return apiCode in RETRYABLE_HTTP_CODES
    }

    private fun isForegroundStartNotAllowed(error: Throwable): Boolean {
        var cursor: Throwable? = error
        while (cursor != null) {
            val msg = cursor.message?.lowercase(Locale.getDefault()).orEmpty()
            if (msg.contains("startforegroundservice() not allowed") ||
                msg.contains("foregroundservicestartnotallowedexception")
            ) {
                return true
            }
            cursor = cursor.cause
        }
        return false
    }

    private fun extractApiErrorCode(message: String?): Int? {
        if (message.isNullOrBlank()) return null
        val match = Regex("API错误\\s*(\\d{3})").find(message) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun failure(message: String): Result {
        return terminalResult(
            status = RESULT_STATUS_FAILED,
            message = message
        )
    }

    private fun terminalResult(status: String, message: String? = null): Result {
        // Keep queue moving even if a single record fails.
        return Result.success(
            workDataOf(
                KEY_RESULT_STATUS to status,
                KEY_ERROR_MESSAGE to message.orEmpty()
            )
        )
    }

    companion object {
        const val KEY_RECORD_ID = "record_id"
        const val KEY_GENERATE_MINUTE = "generate_minute"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_RESULT_STATUS = "result_status"
        const val TAG_TRANSCRIPTION = "transcription_task"

        const val RESULT_STATUS_SUCCESS = "success"
        const val RESULT_STATUS_FAILED = "failed"

        private const val ENABLE_FOREGROUND_NOTIFICATION = true
        private val RETRYABLE_HTTP_CODES = setOf(408, 429, 500, 502, 503, 504)

        fun recordTag(recordId: Long): String = "transcription_record_$recordId"
    }
}
