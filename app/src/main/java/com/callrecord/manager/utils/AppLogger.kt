package com.callrecord.manager.utils

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 应用日志工具
 */
object AppLogger {
    private const val TAG = "CallRecordManager"
    
    // 日志文件
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    // 日志级别
    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }
    
    // 日志监听器，用于在UI显示日志
    private val listeners = mutableListOf<LogListener>()
    
    interface LogListener {
        fun onLog(level: Level, tag: String, message: String, throwable: Throwable?)
    }
    
    /**
     * 初始化日志文件
     */
    fun init(logDir: File) {
        try {
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            logFile = File(logDir, "app_log_$timestamp.txt")
            logFile?.writeText("=== 应用日志开始 ===\n时间: ${dateFormat.format(Date())}\n\n")
            Log.i(TAG, "日志文件已创建: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "创建日志文件失败", e)
        }
    }
    
    /**
     * 获取当前日志文件路径
     */
    fun getLogFilePath(): String? = logFile?.absolutePath
    
    fun addListener(listener: LogListener) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }
    
    fun d(tag: String, message: String) {
        Log.d(TAG, "[$tag] $message")
        writeToFile(Level.DEBUG, tag, message, null)
        notifyListeners(Level.DEBUG, tag, message, null)
    }
    
    fun i(tag: String, message: String) {
        Log.i(TAG, "[$tag] $message")
        writeToFile(Level.INFO, tag, message, null)
        notifyListeners(Level.INFO, tag, message, null)
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(TAG, "[$tag] $message", throwable)
        writeToFile(Level.WARN, tag, message, throwable)
        notifyListeners(Level.WARN, tag, message, throwable)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $message", throwable)
        writeToFile(Level.ERROR, tag, message, throwable)
        notifyListeners(Level.ERROR, tag, message, throwable)
    }
    
    /**
     * 写入日志到文件
     */
    private fun writeToFile(level: Level, tag: String, message: String, throwable: Throwable?) {
        try {
            logFile?.let { file ->
                val timestamp = dateFormat.format(Date())
                val levelStr = level.name.padEnd(5)
                val logLine = StringBuilder()
                logLine.append("$timestamp [$levelStr] [$tag] $message\n")
                
                throwable?.let {
                    logLine.append("异常: ${it.message}\n")
                    logLine.append(it.stackTraceToString())
                    logLine.append("\n")
                }
                
                file.appendText(logLine.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入日志文件失败", e)
        }
    }
    
    private fun notifyListeners(level: Level, tag: String, message: String, throwable: Throwable?) {
        listeners.forEach { listener ->
            try {
                listener.onLog(level, tag, message, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying log listener", e)
            }
        }
    }
    
    /**
     * 格式化日志消息用于UI显示
     */
    fun formatForUI(level: Level, tag: String, message: String, throwable: Throwable?): String {
        val icon = when (level) {
            Level.DEBUG -> "🔍"
            Level.INFO -> "ℹ️"
            Level.WARN -> "⚠️"
            Level.ERROR -> "❌"
        }
        
        val error = throwable?.let { "\n错误: ${it.message}" } ?: ""
        return "$icon [$tag] $message$error"
    }
}
