package com.callrecord.manager.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 阶跃星辰 API 服务接口
 */
interface StepFunApiService {

    /**
     * Upload audio file for transcription via Multipart.
     * Uses step-asr model.
     */
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudioFile(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody
    ): Response<StepFunAsrResponse>

    /**
     * LLM 对话 API
     */
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: StepFunLlmRequest
    ): Response<StepFunLlmResponse>
}

/**
 * API 客户端
 */
object ApiClient {
    private const val BASE_URL = "https://api.stepfun.com/"
    
    /**
     * Create StepFunApiService with dynamic API Key retrieval.
     * @param apiKeyProvider function that returns the current API Key on each invocation
     */
    fun createStepFunService(apiKeyProvider: () -> String): StepFunApiService {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val currentApiKey = apiKeyProvider()
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Authorization", "Bearer $currentApiKey")
                    .header("Content-Type", "application/json")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()

        return retrofit.create(StepFunApiService::class.java)
    }
}
