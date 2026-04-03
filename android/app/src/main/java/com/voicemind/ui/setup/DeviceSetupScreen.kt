package com.voicemind.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voicemind.ui.components.PrimaryButton
import com.voicemind.ui.theme.VmDimens

@Composable
fun DeviceSetupScreen(
    viewModel: DeviceSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VmDimens.ScreenHorizontalPadding, vertical = VmDimens.SpaceXxxl),
            verticalArrangement = Arrangement.spacedBy(VmDimens.SpaceLg),
        ) {
            Text(
                text = "Set Up Local Storage",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Text(
                text = "VoiceMind will store your recordings and metadata locally for instant playback and offline access. Your data is always backed up to the cloud. You can manage storage usage in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(VmDimens.SpaceSm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VmDimens.SpaceSm),
            ) {
                Checkbox(
                    checked = state.hasAgreed,
                    onCheckedChange = { viewModel.toggleAgreed() },
                )
                Text(
                    text = "I understand and agree",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.isConfirming) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(VmDimens.IconXl)
                        .align(Alignment.CenterHorizontally),
                )
            } else {
                PrimaryButton(
                    text = "Set Up",
                    onClick = { viewModel.confirm() },
                    enabled = state.hasAgreed,
                )
            }
        }
    }
}
