package com.voicemind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.voicemind.data.repository.NavPreferenceRepository
import com.voicemind.ui.auth.AuthViewModel
import com.voicemind.ui.auth.SignInScreen
import com.voicemind.ui.navigation.AppNavHost
import com.voicemind.ui.theme.VoiceMindAITheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var navPreferenceRepository: NavPreferenceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoiceMindAITheme {
                val authViewModel: AuthViewModel = hiltViewModel()
                val isSignedIn by authViewModel.isSignedIn.collectAsState()

                if (isSignedIn) {
                    AppNavHost(
                        onSignOut = { authViewModel.signOut() },
                        navPreferenceRepository = navPreferenceRepository,
                    )
                } else {
                    SignInScreen(viewModel = authViewModel)
                }
            }
        }
    }
}
