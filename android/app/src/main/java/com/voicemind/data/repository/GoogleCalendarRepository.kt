package com.voicemind.data.repository

import android.app.Activity
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val WEB_CLIENT_ID =
    "685270102033-tupn4a0mm03k7pdrnd1lhlv53gbq605t.apps.googleusercontent.com"

private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events"

sealed interface CalendarConnectResult {
    data object Success : CalendarConnectResult
    data class NeedsConsent(val result: AuthorizationResult) : CalendarConnectResult
    data class Error(val message: String) : CalendarConnectResult
}

@Singleton
class GoogleCalendarRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
) {
    private val uid: String
        get() = requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid

    /**
     * Step 1 of the connect flow. Requests calendar scope with offline access.
     * Returns [CalendarConnectResult.NeedsConsent] if the user must grant permission
     * via a resolution intent, or proceeds to token exchange if already authorized.
     */
    suspend fun requestCalendarAccess(activity: Activity): CalendarConnectResult {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE)))
                .requestOfflineAccess(WEB_CLIENT_ID)
                .build()

            val result = Identity.getAuthorizationClient(activity)
                .authorize(request)
                .await()

            if (result.hasResolution()) {
                CalendarConnectResult.NeedsConsent(result)
            } else {
                val authCode = result.serverAuthCode
                if (authCode != null) {
                    exchangeAuthCode(authCode)
                } else {
                    CalendarConnectResult.Error("No authorization code received")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Calendar authorization request failed")
            CalendarConnectResult.Error("Failed to request calendar access")
        }
    }

    /**
     * Step 2: called after the user grants consent via the resolution intent.
     * Extracts the auth code from the result and exchanges it.
     */
    suspend fun handleConsentResult(result: AuthorizationResult): CalendarConnectResult {
        return try {
            val authCode = result.serverAuthCode
                ?: return CalendarConnectResult.Error("No authorization code received")
            exchangeAuthCode(authCode)
        } catch (e: Exception) {
            Timber.e(e, "Calendar consent handling failed")
            CalendarConnectResult.Error("Failed to connect Google Calendar")
        }
    }

    private suspend fun exchangeAuthCode(authCode: String): CalendarConnectResult {
        return try {
            val result = functions
                .getHttpsCallable("exchangeCalendarAuthCode")
                .call(hashMapOf("authCode" to authCode))
                .await()
            val data = result.getData() as? Map<*, *>
            if (data?.get("success") == true) {
                CalendarConnectResult.Success
            } else {
                CalendarConnectResult.Error("Token exchange failed")
            }
        } catch (e: Exception) {
            Timber.e(e, "Calendar auth code exchange failed")
            CalendarConnectResult.Error("Failed to connect Google Calendar. Please try again.")
        }
    }

    suspend fun disconnectCalendar(): Boolean {
        return try {
            val result = functions
                .getHttpsCallable("disconnectCalendar")
                .call(null)
                .await()
            val data = result.getData() as? Map<*, *>
            data?.get("success") == true
        } catch (e: Exception) {
            Timber.e(e, "Calendar disconnect failed")
            false
        }
    }

    fun observeCalendarConnected(): Flow<Boolean> = callbackFlow {
        val registration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeCalendarConnected")
                    trySend(false)
                    return@addSnapshotListener
                }
                val connected = snapshot?.getBoolean("calendarConnected") ?: false
                trySend(connected)
            }
        awaitClose { registration.remove() }
    }
}
