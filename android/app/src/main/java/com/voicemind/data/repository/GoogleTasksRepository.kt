package com.voicemind.data.repository

import android.accounts.Account
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

private const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events"

sealed interface TasksConnectResult {
    data object Success : TasksConnectResult
    data class NeedsConsent(val result: AuthorizationResult) : TasksConnectResult
    data class Error(val message: String) : TasksConnectResult
}

@Singleton
class GoogleTasksRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
) {
    private val uid: String
        get() = requireNotNull(authRepository.currentUser) { "User must be signed in" }.uid

    /**
     * Step 1 of the connect flow. Requests Tasks scope with offline access.
     * Returns [TasksConnectResult.NeedsConsent] if the user must grant permission
     * via a resolution intent, or proceeds to token exchange if already authorized.
     */
    suspend fun requestTasksAccess(activity: Activity): TasksConnectResult {
        return try {
            // If the user signed in with Google, pre-select that account so they
            // don't see an account picker — the Tasks permission flows to the same
            // Google account they used to log in.
            val googleEmail = authRepository.currentUser?.providerData
                ?.firstOrNull { it.providerId == "google.com" }
                ?.email

            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(TASKS_SCOPE), Scope(CALENDAR_SCOPE)))
                .requestOfflineAccess(WEB_CLIENT_ID)
                .apply {
                    if (googleEmail != null) {
                        setAccount(Account(googleEmail, "com.google"))
                    }
                }
                .build()

            val result = Identity.getAuthorizationClient(activity)
                .authorize(request)
                .await()

            if (result.hasResolution()) {
                TasksConnectResult.NeedsConsent(result)
            } else {
                val authCode = result.serverAuthCode
                if (authCode != null) {
                    exchangeAuthCode(authCode)
                } else {
                    TasksConnectResult.Error("No authorization code received")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Tasks authorization request failed")
            TasksConnectResult.Error("Failed to request Tasks access")
        }
    }

    /**
     * Step 2: called after the user grants consent via the resolution intent.
     * Extracts the auth code from the result and exchanges it.
     */
    suspend fun handleConsentResult(result: AuthorizationResult): TasksConnectResult {
        return try {
            val authCode = result.serverAuthCode
                ?: return TasksConnectResult.Error("No authorization code received")
            exchangeAuthCode(authCode)
        } catch (e: Exception) {
            Timber.e(e, "Tasks consent handling failed")
            TasksConnectResult.Error("Failed to connect Google Tasks")
        }
    }

    private suspend fun exchangeAuthCode(authCode: String): TasksConnectResult {
        return try {
            val result = functions
                .getHttpsCallable("exchangeTasksAuthCode")
                .call(hashMapOf("authCode" to authCode))
                .await()
            val data = result.getData() as? Map<*, *>
            if (data?.get("success") == true) {
                TasksConnectResult.Success
            } else {
                TasksConnectResult.Error("Token exchange failed")
            }
        } catch (e: Exception) {
            Timber.e(e, "Tasks auth code exchange failed")
            TasksConnectResult.Error("Failed to connect Google Tasks. Please try again.")
        }
    }

    suspend fun disconnectTasks(): Boolean {
        return try {
            val result = functions
                .getHttpsCallable("disconnectTasks")
                .call(null)
                .await()
            val data = result.getData() as? Map<*, *>
            data?.get("success") == true
        } catch (e: Exception) {
            Timber.e(e, "Tasks disconnect failed")
            false
        }
    }

    fun observeTasksConnected(): Flow<Boolean> = callbackFlow {
        val registration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeTasksConnected")
                    trySend(false)
                    return@addSnapshotListener
                }
                val connected = snapshot?.getBoolean("tasksConnected") ?: false
                trySend(connected)
            }
        awaitClose { registration.remove() }
    }
}
