package com.voicemind.data.repository

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    private val storage: FirebaseStorage,
    private val authRepository: AuthRepository,
) {
    private val uid: String get() = requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid

    suspend fun uploadAudio(recordingId: String, file: File): String {
        val path = "users/$uid/audio/$recordingId.m4a"
        val ref = storage.reference.child(path)
        ref.putFile(Uri.fromFile(file)).await()
        Timber.d("Audio uploaded successfully")
        return path
    }

    suspend fun getDownloadUrl(audioPath: String): Uri {
        return storage.reference.child(audioPath).downloadUrl.await()
    }

    suspend fun downloadAudio(audioPath: String, destinationFile: File) {
        storage.reference.child(audioPath).getFile(destinationFile).await()
    }

    suspend fun downloadFromUrl(url: String, destinationFile: File) = withContext(Dispatchers.IO) {
        try {
            java.net.URL(url).openStream().use { input ->
                destinationFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            destinationFile.delete()
            throw e
        }
    }
}
