package com.voicemind.data.repository

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
    val currentUser: FirebaseUser? get() = auth.currentUser

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
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error("Unable to sign in with Google. Please try again.")
        }
    }

    fun signOut() {
        auth.signOut()
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
