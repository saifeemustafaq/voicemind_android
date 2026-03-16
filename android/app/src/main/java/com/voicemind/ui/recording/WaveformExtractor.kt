package com.voicemind.ui.recording

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteOrder
import kotlin.math.sqrt

object WaveformExtractor {
    /**
     * Decodes the audio at [url] and returns a list of [buckets] normalized RMS values (0..1).
     * Returns an empty list if extraction fails or the audio is silent.
     */
    suspend fun extract(url: String, buckets: Int = 300): List<Float> = withContext(Dispatchers.IO) {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(url)

            val trackIdx = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: run {
                extractor.release()
                return@withContext emptyList()
            }

            extractor.selectTrack(trackIdx)
            val format = extractor.getTrackFormat(trackIdx)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcm = mutableListOf<Short>()
            val info = MediaCodec.BufferInfo()
            var sawEOS = false

            while (!sawEOS) {
                val inIdx = codec.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawEOS = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    val shorts = ShortArray(buf.remaining() / 2)
                    buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                    pcm.addAll(shorts.toList())
                    codec.releaseOutputBuffer(outIdx, false)
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            if (pcm.isEmpty()) return@withContext emptyList()

            val bucketSize = (pcm.size / buckets).coerceAtLeast(1)
            val rms = (0 until buckets).map { b ->
                val start = b * bucketSize
                val end = minOf(start + bucketSize, pcm.size)
                if (start >= pcm.size) return@map 0f
                val slice = pcm.subList(start, end)
                sqrt(slice.sumOf { it.toLong() * it }.toDouble() / slice.size).toFloat()
            }

            val max = rms.maxOrNull()?.takeIf { it > 0f } ?: return@withContext emptyList()
            rms.map { it / max }
        } catch (e: Exception) {
            Timber.w(e, "WaveformExtractor: extraction failed")
            emptyList()
        }
    }
}
