package com.voicemind.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.VmDimens

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Routes) -> Unit,
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = VmDimens.HairlineBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Routes.drawerItems.forEach { item ->
                val selected = currentRoute == item.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = selected,
                            onClick = { onNavigate(item) },
                            role = Role.Tab,
                        )
                        .padding(vertical = VmDimens.SpaceSm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(VmDimens.SpaceXxs),
                ) {
                    Icon(
                        imageVector = if (selected) item.icon else item.outlinedIcon,
                        contentDescription = item.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(VmDimens.IconMd),
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
