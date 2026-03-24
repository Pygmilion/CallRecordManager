package com.callrecord.manager.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.callrecord.manager.utils.AppLogger
import java.util.concurrent.TimeUnit

/**
 * 统一封装后台转写任务调度逻辑，避免重复入队。
 */
object TranscriptionWorkScheduler {
    private const val QUEUE_WORK_NAME = "transcription_queue"

    fun enqueue(
        context: Context,
        recordId: Long,
        generateMinute: Boolean
    ): Boolean {
        val workManager = WorkManager.getInstance(context)
        val recordTag = TranscriptionWorker.recordTag(recordId)
        val hasActiveWork = runCatching {
            workManager
                .getWorkInfosByTag(recordTag)
                .get()
                .any { info ->
                    info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.BLOCKED
                }
        }.onFailure { error ->
            AppLogger.w("TranscriptionScheduler", "查询任务去重状态失败: recordId=$recordId", error)
        }.getOrDefault(false)

        if (hasActiveWork) {
            AppLogger.i("TranscriptionScheduler", "检测到重复任务，跳过入队: recordId=$recordId")
            return false
        }

        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(
                workDataOf(
                    TranscriptionWorker.KEY_RECORD_ID to recordId,
                    TranscriptionWorker.KEY_GENERATE_MINUTE to generateMinute
                )
            )
            .addTag(recordTag)
            .addTag(TranscriptionWorker.TAG_TRANSCRIPTION)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            QUEUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )

        AppLogger.i(
            "TranscriptionScheduler",
            "后台任务已入队(串行队列): recordId=$recordId, generateMinute=$generateMinute"
        )
        return true
    }
}
