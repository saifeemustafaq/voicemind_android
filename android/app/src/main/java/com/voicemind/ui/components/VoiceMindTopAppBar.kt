package com.voicemind.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosSecondaryLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceMindTopAppBar(
    title: String,
    icon: ImageVector,
    onOpenDrawer: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = IosAccent,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (onInfoClick != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "How summaries work",
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onInfoClick() },
                        tint = IosSecondaryLabel,
                    )
                }
            }
        },
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                onOpenDrawer != null -> IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            }
        },
        actions = {
            extraActions()
            if (onSettings != null) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
