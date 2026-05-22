package com.voicemind.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class NtsSettings(
    val enabled: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val intervalMinutes: Int = 30,
)

@Singleton
class UserSettingsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) {
    private val uid: String
        get() = requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid

    private fun userDoc() = firestore.collection("users").document(uid)

    fun observeNtsSettings(): Flow<NtsSettings> = callbackFlow {
        val registration = userDoc().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.e(error, "observeNtsSettings")
                trySend(NtsSettings())
                return@addSnapshotListener
            }
            val settings = NtsSettings(
                enabled = snapshot?.getBoolean("ntsEnabled") ?: false,
                startHour = snapshot?.getLong("ntsStartHour")?.toInt() ?: 22,
                startMinute = snapshot?.getLong("ntsStartMinute")?.toInt() ?: 0,
                intervalMinutes = snapshot?.getLong("ntsIntervalMinutes")?.toInt() ?: 30,
            )
            trySend(settings)
        }
        awaitClose { registration.remove() }
    }

    suspend fun setNtsEnabled(enabled: Boolean) {
        userDoc().update("ntsEnabled", enabled).await()
    }

    /** Atomically writes ntsEnabled and timezone in one update to avoid race conditions. */
    suspend fun setNtsEnabledWithTimezone(enabled: Boolean, timezoneId: String) {
        userDoc().update(
            mapOf(
                "ntsEnabled" to enabled,
                "timezone" to timezoneId,
            )
        ).await()
    }

    suspend fun setNtsStartTime(hour: Int, minute: Int) {
        userDoc().update(
            mapOf(
                "ntsStartHour" to hour,
                "ntsStartMinute" to minute,
            )
        ).await()
    }

    suspend fun setNtsIntervalMinutes(interval: Int) {
        userDoc().update("ntsIntervalMinutes", interval).await()
    }

    suspend fun syncTimezoneToFirestore(timezoneId: String) {
        userDoc().update("timezone", timezoneId).await()
    }

    fun observeDiscoverable(): Flow<Boolean> = callbackFlow {
        val registration = userDoc().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.e(error, "observeDiscoverable")
                trySend(true)
                return@addSnapshotListener
            }
            // Default to true when field is absent (PRD Section 28.3)
            trySend(snapshot?.getBoolean("discoverable") ?: true)
        }
        awaitClose { registration.remove() }
    }

    suspend fun setDiscoverable(enabled: Boolean) {
        userDoc().update("discoverable", enabled).await()
    }
}
