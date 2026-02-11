package com.callrecord.manager.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 通话录音 DAO
 */
@Dao
interface CallRecordDao {
    @Query("SELECT * FROM call_records ORDER BY recordTime DESC")
    fun getAllRecords(): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records WHERE id = :id")
    suspend fun getRecordById(id: Long): CallRecordEntity?

    @Query("SELECT * FROM call_records WHERE isTranscribed = 0 ORDER BY recordTime DESC")
    fun getUntranscribedRecords(): Flow<List<CallRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CallRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<CallRecordEntity>)

    @Update
    suspend fun updateRecord(record: CallRecordEntity)

    @Delete
    suspend fun deleteRecord(record: CallRecordEntity)

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("SELECT COUNT(*) FROM call_records")
    suspend fun getRecordCount(): Int

    @Query("SELECT * FROM call_records WHERE phoneNumber LIKE '%' || :query || '%' OR contactName LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%'")
    fun searchRecords(query: String): Flow<List<CallRecordEntity>>

    @Query("SELECT * FROM call_records")
    suspend fun getAllRecordsOnce(): List<CallRecordEntity>

    @Query("UPDATE call_records SET contactName = :newName WHERE phoneNumber = :phoneNumber")
    suspend fun updateContactNameByPhone(phoneNumber: String, newName: String)
}

/**
 * 转写记录 DAO
 */
@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcripts ORDER BY createTime DESC")
    fun getAllTranscripts(): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE id = :id")
    suspend fun getTranscriptById(id: Long): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE recordId = :recordId")
    suspend fun getTranscriptByRecordId(recordId: Long): TranscriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscript(transcript: TranscriptEntity): Long

    @Update
    suspend fun updateTranscript(transcript: TranscriptEntity)

    @Delete
    suspend fun deleteTranscript(transcript: TranscriptEntity)

    @Query("DELETE FROM transcripts WHERE recordId = :recordId")
    suspend fun deleteTranscriptByRecordId(recordId: Long)

    @Query("SELECT * FROM transcripts WHERE status = :status")
    fun getTranscriptsByStatus(status: TranscriptStatus): Flow<List<TranscriptEntity>>
}

/**
 * 会谈纪要 DAO
 */
@Dao
interface MeetingMinuteDao {
    @Query("SELECT * FROM meeting_minutes ORDER BY createTime DESC")
    fun getAllMinutes(): Flow<List<MeetingMinuteEntity>>

    @Query("SELECT * FROM meeting_minutes WHERE id = :id")
    suspend fun getMinuteById(id: Long): MeetingMinuteEntity?

    @Query("SELECT * FROM meeting_minutes WHERE transcriptId = :transcriptId")
    suspend fun getMinuteByTranscriptId(transcriptId: Long): MeetingMinuteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMinute(minute: MeetingMinuteEntity): Long

    @Update
    suspend fun updateMinute(minute: MeetingMinuteEntity)

    @Delete
    suspend fun deleteMinute(minute: MeetingMinuteEntity)

    @Query("DELETE FROM meeting_minutes WHERE transcriptId = :transcriptId")
    suspend fun deleteMinuteByTranscriptId(transcriptId: Long)

    @Query("SELECT * FROM meeting_minutes WHERE title LIKE '%' || :query || '%' OR summary LIKE '%' || :query || '%'")
    fun searchMinutes(query: String): Flow<List<MeetingMinuteEntity>>

    @Query("SELECT COUNT(*) FROM meeting_minutes")
    suspend fun getMinuteCount(): Int

    /**
     * Get all minutes with associated contact name via join chain:
     * meeting_minutes.transcriptId → transcripts.recordId → call_records.contactName
     * Also includes recordTime, recordId and recordFileName for display and linking.
     */
    @Query("""
        SELECT m.*, 
               COALESCE(r.contactName, r.phoneNumber) AS contactName,
               r.recordTime AS recordTime,
               r.id AS recordId,
               r.fileName AS recordFileName
        FROM meeting_minutes m
        LEFT JOIN transcripts t ON m.transcriptId = t.id
        LEFT JOIN call_records r ON t.recordId = r.id
        ORDER BY COALESCE(r.recordTime, m.createTime) DESC
    """)
    fun getAllMinutesWithContact(): Flow<List<MinuteWithContact>>
}
