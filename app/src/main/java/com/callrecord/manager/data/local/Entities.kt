package com.callrecord.manager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 通话录音实体
 */
@Entity(tableName = "call_records")
data class CallRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,              // 文件名
    val filePath: String,              // 文件路径
    val phoneNumber: String?,          // 电话号码
    val contactName: String?,          // 联系人名称
    val duration: Long,                // 时长（秒）
    val fileSize: Long,                // 文件大小（字节）
    val recordTime: Long,              // 录音时间戳
    val createTime: Long = System.currentTimeMillis(),  // 创建时间
    val isTranscribed: Boolean = false,  // 是否已转写
    val transcriptId: Long? = null     // 关联的转写记录ID
)

/**
 * 转写记录实体
 */
@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordId: Long,                // 关联的录音ID
    val fullText: String,              // 完整文本
    val speakers: List<SpeakerSegment>, // 说话人分段
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis(),
    val status: TranscriptStatus = TranscriptStatus.PENDING
)

/**
 * 会谈纪要实体
 */
@Entity(tableName = "meeting_minutes")
data class MeetingMinuteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transcriptId: Long,            // 关联的转写ID
    val title: String,                 // 标题
    val summary: String,               // 摘要
    val keyPoints: List<String>,       // 关键要点
    val participants: List<Participant>, // 参与者
    val actionItems: List<ActionItem>, // 待办事项
    val fullContent: String,           // 完整纪要内容
    val createTime: Long = System.currentTimeMillis(),
    val updateTime: Long = System.currentTimeMillis()
)

/**
 * 说话人分段
 */
data class SpeakerSegment(
    val speaker: String,               // 说话人标识（如 "Speaker 1", "Speaker 2"）
    val text: String,                  // 说话内容
    val startTime: Double,             // 开始时间（秒）
    val endTime: Double                // 结束时间（秒）
)

/**
 * 参与者信息
 */
data class Participant(
    val name: String,                  // 姓名
    val role: String? = null,          // 角色
    val speakerId: String              // 对应的说话人ID
)

/**
 * 待办事项
 */
data class ActionItem(
    val description: String,           // 描述
    val assignee: String? = null,      // 负责人
    val deadline: String? = null,      // 截止日期
    val priority: Priority = Priority.MEDIUM
)

/**
 * 优先级
 */
enum class Priority {
    LOW, MEDIUM, HIGH, URGENT
}

/**
 * 转写状态
 */
enum class TranscriptStatus {
    PENDING,      // 待处理
    PROCESSING,   // 处理中
    COMPLETED,    // 已完成
    FAILED        // 失败
}

/**
 * Recording process pipeline stage (UI state, not persisted to DB)
 */
enum class RecordProcessStage {
    IDLE,               // Not yet processed
    TRANSCRIBING,       // Transcription in progress
    TRANSCRIBE_DONE,    // Transcription completed
    GENERATING_MINUTE,  // Meeting minute generation in progress
    COMPLETED,          // All processing completed
    TRANSCRIBE_FAILED,  // Transcription failed
    MINUTE_FAILED       // Minute generation failed
}

/**
 * Meeting minute with associated contact name from the call record.
 * Used for grouping minutes by contact in the UI.
 */
data class MinuteWithContact(
    // All fields from MeetingMinuteEntity
    val id: Long,
    val transcriptId: Long,
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val participants: List<Participant>,
    val actionItems: List<ActionItem>,
    val fullContent: String,
    val createTime: Long,
    val updateTime: Long,
    // Extra fields from joined query
    val contactName: String?,
    val recordTime: Long? = null,     // Recording time from call_records
    val recordId: Long? = null,       // Recording ID for linking back to record
    val recordFileName: String? = null // Recording file name for display
) {
    /** Convert to MeetingMinuteEntity */
    fun toEntity() = MeetingMinuteEntity(
        id = id, transcriptId = transcriptId, title = title,
        summary = summary, keyPoints = keyPoints, participants = participants,
        actionItems = actionItems, fullContent = fullContent,
        createTime = createTime, updateTime = updateTime
    )
}

/**
 * Room 类型转换器
 */
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromSpeakerSegmentList(value: List<SpeakerSegment>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toSpeakerSegmentList(value: String): List<SpeakerSegment> {
        val type = object : TypeToken<List<SpeakerSegment>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromParticipantList(value: List<Participant>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toParticipantList(value: String): List<Participant> {
        val type = object : TypeToken<List<Participant>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromActionItemList(value: List<ActionItem>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toActionItemList(value: String): List<ActionItem> {
        val type = object : TypeToken<List<ActionItem>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromTranscriptStatus(value: TranscriptStatus): String {
        return value.name
    }

    @TypeConverter
    fun toTranscriptStatus(value: String): TranscriptStatus {
        return TranscriptStatus.valueOf(value)
    }

    @TypeConverter
    fun fromPriority(value: Priority): String {
        return value.name
    }

    @TypeConverter
    fun toPriority(value: String): Priority {
        return Priority.valueOf(value)
    }
}
