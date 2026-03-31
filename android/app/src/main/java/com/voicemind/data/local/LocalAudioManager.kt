package com.voicemind.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio files in app-private internal storage.
 *
 * - Own recordings: [filesDir]/audio/{recordingId}.m4a
 * - Shared recordings: [filesDir]/shared_audio/{shareId}.m4a
 *
 * No runtime permission needed — app-specific internal storage is always accessible.
 */
@Singleton
class LocalAudioManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioDir = File(context.filesDir, "audio").also { it.mkdirs() }
    private val sharedAudioDir = File(context.filesDir, "shared_audio").also { it.mkdirs() }

    // --- Own recordings ---

    /**
     * Move [srcFile] into permanent local storage and return its absolute path.
     * Deletes [srcFile] after copying.
     */
    fun saveAudio(recordingId: String, srcFile: File): String {
        val dest = File(audioDir, "$recordingId.m4a")
        srcFile.copyTo(dest, overwrite = true)
        srcFile.delete()
        return dest.absolutePath
    }

    fun getAudioFile(recordingId: String): File? =
        File(audioDir, "$recordingId.m4a").takeIf { it.exists() }

    fun deleteAudio(recordingId: String) {
        val file = File(audioDir, "$recordingId.m4a")
        if (!file.delete()) Timber.w("deleteAudio: file not found for %s", recordingId)
    }

    fun audioExists(recordingId: String): Boolean = File(audioDir, "$recordingId.m4a").exists()

    // --- Shared recordings ---

    fun sharedAudioFile(shareId: String): File = File(sharedAudioDir, "$shareId.m4a")

    fun getSharedAudioFile(shareId: String): File? =
        sharedAudioFile(shareId).takeIf { it.exists() }

    fun sharedAudioExists(shareId: String): Boolean = sharedAudioFile(shareId).exists()

    fun deleteSharedAudio(shareId: String) {
        sharedAudioFile(shareId).delete()
    }

    // --- Storage stats ---

    fun getOwnAudioSizeBytes(): Long = audioDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun getSharedAudioSizeBytes(): Long = sharedAudioDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun getTotalSizeBytes(): Long = getOwnAudioSizeBytes() + getSharedAudioSizeBytes()

    // --- Cleanup ---

    fun clearSharedAudio() { sharedAudioDir.listFiles()?.forEach { it.delete() } }

    fun clearAllAudio() {
        audioDir.listFiles()?.forEach { it.delete() }
        sharedAudioDir.listFiles()?.forEach { it.delete() }
    }
}
