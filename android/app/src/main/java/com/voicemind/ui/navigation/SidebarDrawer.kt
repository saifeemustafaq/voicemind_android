package com.voicemind.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SidebarDrawer(
    currentRoute: String?,
    onNavigate: (Routes) -> Unit,
    items: List<Routes> = Routes.drawerItems,
) {
    ModalDrawerSheet(
        // drawerContainerColor uses M3 default (surfaceContainerLow)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "VoiceMind AI",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(modifier = Modifier.height(8.dp))

        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = if (selected) item.icon else item.outlinedIcon,
                        contentDescription = item.label,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                selected = selected,
                onClick = { onNavigate(item) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                // colors use M3 defaults: secondaryContainer for selected, surface for unselected
            )
        }
    }
}
