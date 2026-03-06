package com.voicemind.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.Settings
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
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (onInfoClick != null) {
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            Lucide.Info,
                            contentDescription = "How summaries work",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(Lucide.ArrowLeft, contentDescription = "Back")
                }
                onOpenDrawer != null -> IconButton(onClick = onOpenDrawer) {
                    Icon(Lucide.Menu, contentDescription = "Menu")
                }
            }
        },
        actions = {
            extraActions()
            if (onSettings != null) {
                IconButton(onClick = onSettings) {
                    Icon(Lucide.Settings, contentDescription = "Settings")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
