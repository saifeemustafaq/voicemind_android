package com.voicemind.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RecorderState {
    data object Idle : RecorderState
    data object Recording : RecorderState
    data object Paused : RecorderState
}

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    var state: RecorderState = RecorderState.Idle
        private set

    fun start(): File {
        val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.m4a")
        outputFile = file

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        state = RecorderState.Recording
        Timber.d("Recording started: ${file.absolutePath}")
        return file
    }

    fun pause() {
        recorder?.pause()
        state = RecorderState.Paused
        Timber.d("Recording paused")
    }

    fun resume() {
        recorder?.resume()
        state = RecorderState.Recording
        Timber.d("Recording resumed")
    }

    fun stop(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            state = RecorderState.Idle
            Timber.d("Recording stopped")
            outputFile
        } catch (e: Exception) {
            Timber.e(e, "Error stopping recorder")
            release()
            null
        }
    }

    fun release() {
        try {
            recorder?.release()
        } catch (_: Exception) { }
        recorder = null
        state = RecorderState.Idle
    }

    fun discardAndRelease() {
        release()
        outputFile?.delete()
        outputFile = null
    }
}
