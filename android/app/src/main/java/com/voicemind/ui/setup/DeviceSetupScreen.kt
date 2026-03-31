package com.voicemind.ui.setup

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
                text = "VoiceMind can store your recordings locally so they play instantly without an internet connection. Choose how you want to sync your data to this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(VmDimens.SpaceSm))

            StrategyOption(
                title = "Download Everything",
                description = "Sync all metadata and download all audio files to this device. Best offline experience; uses the most storage.",
                selected = state.selectedStrategy == "full",
                onClick = { viewModel.selectStrategy("full") },
            )

            StrategyOption(
                title = "Download on Demand",
                description = "Sync metadata now; audio downloads the first time you play each recording. Balances storage and offline access.",
                selected = state.selectedStrategy == "on_demand",
                onClick = { viewModel.selectStrategy("on_demand") },
            )

            StrategyOption(
                title = "Metadata Only",
                description = "Sync metadata only. Audio always streams from the cloud. Lowest storage usage; requires internet to play recordings.",
                selected = state.selectedStrategy == "metadata_only",
                onClick = { viewModel.selectStrategy("metadata_only") },
            )

            Spacer(Modifier.height(VmDimens.SpaceSm))

            if (state.error != null) {
                Text(
                    text = state.error!!,
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
                )
            }

            Text(
                text = "This choice cannot be changed without reinstalling the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StrategyOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(VmDimens.HairlineBorder, borderColor, MaterialTheme.shapes.medium)
            .clickable(role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = VmDimens.SpaceLg,
                vertical = VmDimens.SpaceMd,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VmDimens.SpaceMd),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
