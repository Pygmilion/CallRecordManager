package com.callrecord.manager.data.remote

import com.google.gson.annotations.SerializedName

/**
 * 阶跃星辰 ASR 响应
 * Note: ASR uses step-asr model via Multipart upload (transcribeAudioFile).
 * No JSON body request class is needed.
 */
data class StepFunAsrResponse(
    val text: String,
    val segments: List<AsrSegment>? = null,
    val duration: Double? = null
)

data class AsrSegment(
    val id: Int,
    val start: Double,
    val end: Double,
    val text: String,
    val speaker: String? = null  // 说话人标识
)

/**
 * 阶跃星辰 LLM 请求
 */
data class StepFunLlmRequest(
    val model: String = "step-3.5-flash",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 4096,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,  // "system", "user", "assistant"
    val content: String
)

/**
 * 阶跃星辰 LLM 响应
 */
data class StepFunLlmResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

/**
 * 通用 API 响应包装
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null
)

/**
 * 纪要生成结果
 */
data class MeetingMinuteResult(
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val participants: List<ParticipantInfo>,
    val actionItems: List<ActionItemInfo>,
    val fullContent: String
)

data class ParticipantInfo(
    val name: String,
    val role: String?,
    val speakerId: String
)

data class ActionItemInfo(
    val description: String,
    val assignee: String?,
    val deadline: String?,
    val priority: String
)
