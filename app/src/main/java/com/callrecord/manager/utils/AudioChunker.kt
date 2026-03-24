package com.callrecord.manager.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

data class AudioChunk(
    val file: File,
    val startMs: Long,
    val endMs: Long
)

/**
 * Split long local audio files into smaller chunks for more stable ASR uploads.
 *
 * - MP3 source: write frame data to .mp3 chunks directly.
 * - Other source types: remux to .m4a chunks via MediaMuxer.
 */
object AudioChunker {
    private const val TAG = "AudioChunker"
    private const val DEFAULT_BUFFER_SIZE = 1024 * 1024
    private const val MIME_MP3 = "audio/mpeg"

    fun split(
        sourceFile: File,
        outputDir: File,
        chunkDurationSec: Long
    ): Result<List<AudioChunk>> {
        if (!sourceFile.exists()) {
            return Result.failure(IllegalArgumentException("音频文件不存在: ${sourceFile.absolutePath}"))
        }
        if (chunkDurationSec <= 0) {
            return Result.failure(IllegalArgumentException("切片时长必须大于0"))
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            return Result.failure(IllegalStateException("无法创建切片目录: ${outputDir.absolutePath}"))
        }

        return runCatching {
            val durationUs = resolveDurationUs(sourceFile)
            if (durationUs <= 0L) {
                throw IllegalStateException("无法读取音频时长")
            }
            val trackMime = resolveAudioMime(sourceFile)
            val outputExtension = resolveOutputExtension(trackMime)

            val chunkDurationUs = chunkDurationSec * 1_000_000L
            if (durationUs <= chunkDurationUs) {
                return@runCatching emptyList()
            }

            val ranges = mutableListOf<Pair<Long, Long>>()
            var startUs = 0L
            while (startUs < durationUs) {
                val endUs = min(startUs + chunkDurationUs, durationUs)
                ranges += startUs to endUs
                startUs = endUs
            }

            val chunks = mutableListOf<AudioChunk>()
            ranges.forEachIndexed { index, (rangeStartUs, rangeEndUs) ->
                val outFile = File(outputDir, "chunk_${index + 1}.$outputExtension")
                writeChunk(
                    sourceFile = sourceFile,
                    outputFile = outFile,
                    startUs = rangeStartUs,
                    endUs = rangeEndUs,
                    trackMime = trackMime
                )
                chunks += AudioChunk(
                    file = outFile,
                    startMs = rangeStartUs / 1000L,
                    endMs = rangeEndUs / 1000L
                )
            }
            chunks
        }.onFailure { error ->
            AppLogger.e(TAG, "音频切片失败", error)
            outputDir.deleteRecursively()
        }
    }

    private fun resolveDurationUs(sourceFile: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(sourceFile.absolutePath)
            val track = findAudioTrack(extractor)
                ?: throw IllegalStateException("未找到音频轨道")
            val format = track.format
            when {
                format.containsKey(MediaFormat.KEY_DURATION) -> format.getLong(MediaFormat.KEY_DURATION)
                else -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(sourceFile.absolutePath)
                        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull() ?: 0L
                        durationMs * 1000L
                    } finally {
                        retriever.release()
                    }
                }
            }
        } finally {
            extractor.release()
        }
    }

    private fun writeChunk(
        sourceFile: File,
        outputFile: File,
        startUs: Long,
        endUs: Long,
        trackMime: String
    ) {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(sourceFile.absolutePath)
            val track = findAudioTrack(extractor)
                ?: throw IllegalStateException("未找到音频轨道")

            if (trackMime.equals(MIME_MP3, ignoreCase = true)) {
                writeMp3Chunk(
                    extractor = extractor,
                    track = track,
                    outputFile = outputFile,
                    startUs = startUs,
                    endUs = endUs
                )
                return
            }

            writeMuxedChunk(
                extractor = extractor,
                track = track,
                outputFile = outputFile,
                startUs = startUs,
                endUs = endUs
            )
        } finally {
            extractor.release()
        }
    }

    private fun writeMuxedChunk(
        extractor: MediaExtractor,
        track: AudioTrack,
        outputFile: File,
        startUs: Long,
        endUs: Long
    ) {
        var muxer: MediaMuxer? = null
        try {
            extractor.selectTrack(track.index)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(track.format)
            muxer.start()

            val bufferSize = if (track.format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                max(DEFAULT_BUFFER_SIZE, track.format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            } else {
                DEFAULT_BUFFER_SIZE
            }

            val byteBuffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var wroteSample = false

            while (true) {
                byteBuffer.clear()
                val sampleSize = extractor.readSampleData(byteBuffer, 0)
                if (sampleSize < 0) {
                    break
                }

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0 || sampleTimeUs >= endUs) {
                    break
                }

                if (sampleTimeUs < startUs || extractor.sampleTrackIndex != track.index) {
                    extractor.advance()
                    continue
                }

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = sampleTimeUs - startUs
                bufferInfo.flags = toMuxerBufferFlags(extractor.sampleFlags)
                muxer.writeSampleData(muxerTrack, byteBuffer, bufferInfo)
                wroteSample = true
                extractor.advance()
            }

            if (!wroteSample) {
                throw IllegalStateException("切片区间无有效音频样本")
            }
        } finally {
            runCatching {
                muxer?.stop()
            }
            runCatching {
                muxer?.release()
            }
        }
    }

    private fun writeMp3Chunk(
        extractor: MediaExtractor,
        track: AudioTrack,
        outputFile: File,
        startUs: Long,
        endUs: Long
    ) {
        extractor.selectTrack(track.index)
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val bufferSize = if (track.format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            max(DEFAULT_BUFFER_SIZE, track.format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
        } else {
            DEFAULT_BUFFER_SIZE
        }

        val byteBuffer = ByteBuffer.allocateDirect(bufferSize)
        var wroteSample = false

        FileOutputStream(outputFile).use { output ->
            while (true) {
                byteBuffer.clear()
                val sampleSize = extractor.readSampleData(byteBuffer, 0)
                if (sampleSize < 0) {
                    break
                }

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0 || sampleTimeUs >= endUs) {
                    break
                }

                if (sampleTimeUs < startUs || extractor.sampleTrackIndex != track.index) {
                    extractor.advance()
                    continue
                }

                byteBuffer.position(0)
                byteBuffer.limit(sampleSize)
                val frame = ByteArray(sampleSize)
                byteBuffer.get(frame)
                output.write(frame)
                wroteSample = true
                extractor.advance()
            }
        }

        if (!wroteSample) {
            throw IllegalStateException("切片区间无有效音频样本")
        }
    }

    /**
     * MediaExtractor sample flags and MediaCodec buffer flags are different constant sets.
     * Map only flags meaningful for muxer writes to avoid invalid-constant issues.
     */
    private fun toMuxerBufferFlags(sampleFlags: Int): Int {
        var muxerFlags = 0
        if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            muxerFlags = muxerFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        return muxerFlags
    }

    private fun resolveAudioMime(sourceFile: File): String {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(sourceFile.absolutePath)
            findAudioTrack(extractor)?.mime ?: ""
        } finally {
            extractor.release()
        }
    }

    private fun resolveOutputExtension(trackMime: String): String {
        return if (trackMime.equals(MIME_MP3, ignoreCase = true)) {
            "mp3"
        } else {
            "m4a"
        }
    }

    private data class AudioTrack(
        val index: Int,
        val format: MediaFormat,
        val mime: String
    )

    private fun findAudioTrack(extractor: MediaExtractor): AudioTrack? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                return AudioTrack(index = i, format = format, mime = mime)
            }
        }
        return null
    }
}
