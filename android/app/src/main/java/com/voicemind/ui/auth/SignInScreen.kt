package com.voicemind.ui.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.components.PrimaryButton
import com.voicemind.ui.theme.VmBlushPink
import com.voicemind.ui.theme.VmCoolSkyBlue
import com.voicemind.ui.theme.VmDeepViolet
import com.voicemind.ui.theme.VmLightLavender
import com.voicemind.ui.theme.VmPastelViolet
import com.voicemind.ui.theme.VmSoftPeriwinkleMist
import com.voicemind.ui.theme.VmTextPrimary
import com.voicemind.ui.theme.VmTextSecondary
import kotlinx.coroutines.launch
import timber.log.Timber

private const val WEB_CLIENT_ID = "685270102033-tupn4a0mm03k7pdrnd1lhlv53gbq605t.apps.googleusercontent.com"

@Composable
fun SignInScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "VoiceMind",
                modifier = Modifier.size(64.dp),
                tint = VmBlushPink
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "VoiceMind AI",
                style = MaterialTheme.typography.headlineLarge,
                color = VmTextPrimary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Capture your thoughts with voice",
                style = MaterialTheme.typography.bodyMedium,
                color = VmTextSecondary
            )

            Spacer(modifier = Modifier.height(48.dp))

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                innerPadding = 20.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (uiState.isCreateAccount) "Create Account" else "Sign In",
                        style = MaterialTheme.typography.titleMedium,
                        color = VmTextPrimary
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VmPastelViolet,
                            unfocusedBorderColor = VmLightLavender.copy(alpha = 0.4f),
                            focusedContainerColor = VmSoftPeriwinkleMist.copy(alpha = 0.2f),
                            unfocusedContainerColor = VmSoftPeriwinkleMist.copy(alpha = 0.2f),
                        )
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VmPastelViolet,
                            unfocusedBorderColor = VmLightLavender.copy(alpha = 0.4f),
                            focusedContainerColor = VmSoftPeriwinkleMist.copy(alpha = 0.2f),
                            unfocusedContainerColor = VmSoftPeriwinkleMist.copy(alpha = 0.2f),
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    PrimaryButton(
                        text = if (uiState.isCreateAccount) "Create Account" else "Sign In",
                        onClick = {
                            if (uiState.isCreateAccount) {
                                viewModel.signUpWithEmail(email, password)
                            } else {
                                viewModel.signInWithEmail(email, password)
                            }
                        },
                        enabled = !uiState.isLoading
                    )

                    TextButton(
                        onClick = { viewModel.toggleCreateAccount() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (uiState.isCreateAccount) "Already have an account? Sign in" else "Don't have an account? Create one",
                            color = VmDeepViolet,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = VmLightLavender.copy(alpha = 0.5f)
                )
                Text("or", style = MaterialTheme.typography.bodySmall, color = VmTextSecondary)
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = VmLightLavender.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(WEB_CLIENT_ID)
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            val result = credentialManager.getCredential(context as Activity, request)
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                            viewModel.signInWithGoogle(googleIdTokenCredential.idToken)
                        } catch (e: Exception) {
                            Timber.e(e, "Google Sign-In failed")
                            viewModel.clearError()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VmLightLavender),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VmTextPrimary
                )
            ) {
                Text(
                    text = "Continue with Google",
                    style = MaterialTheme.typography.titleSmall,
                    color = VmTextPrimary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = VmDeepViolet,
                    strokeWidth = 3.dp
                )
            }
        }

        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss", color = Color.White)
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}
