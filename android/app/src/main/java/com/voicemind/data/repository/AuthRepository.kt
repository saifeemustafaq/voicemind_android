package com.voicemind.data.repository

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult
    data class Error(val message: String) : AuthResult
}

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    companion object {
        const val WEB_CLIENT_ID = "685270102033-tupn4a0mm03k7pdrnd1lhlv53gbq605t.apps.googleusercontent.com"
    }

    val currentUser: FirebaseUser? get() = auth.currentUser

    val isGoogleUser: Boolean
        get() = currentUser?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true

    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return AuthResult.Error("Sign in failed")
            syncProfileToFirestore(user)
            registerFcmToken()
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error("Unable to sign in. Please check your credentials and try again.")
        }
    }

    suspend fun signUpWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return AuthResult.Error("Account creation failed")
            syncProfileToFirestore(user)
            registerFcmToken()
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error("Unable to create account. Please try again.")
        }
    }

    suspend fun signInWithGoogleCredential(idToken: String): AuthResult {
        return try {
            val credential: AuthCredential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: return AuthResult.Error("Google sign in failed")
            syncProfileToFirestore(user)
            registerFcmToken()
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error("Unable to sign in with Google. Please try again.")
        }
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun reauthenticateWithEmail(email: String, password: String): Result<Unit> {
        val user = currentUser ?: return Result.failure(Exception("Not signed in"))
        return try {
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit> {
        val user = currentUser ?: return Result.failure(Exception("Not signed in"))
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        val user = currentUser ?: return Result.failure(Exception("Not signed in"))
        return try {
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Fire-and-forget: registers the current FCM token so the backend can send push notifications.
    fun registerFcmToken() {
        val uid = currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            firestore.collection("users/$uid/deviceTokens")
                .document(token.hashCode().toString())
                .set(mapOf("token" to token, "updatedAt" to FieldValue.serverTimestamp()))
                .addOnFailureListener { Timber.e(it, "Failed to register FCM token") }
        }
    }

    // Fire-and-forget: keeps displayName/email current; errors do not block sign-in.
    private fun syncProfileToFirestore(user: FirebaseUser) {
        val data = mapOf(
            "displayName" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
        )
        firestore.collection("users").document(user.uid)
            .set(data, SetOptions.merge())
            .addOnFailureListener { Timber.e(it, "Failed to sync profile to Firestore") }
    }
}
