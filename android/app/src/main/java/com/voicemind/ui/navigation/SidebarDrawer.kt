package com.voicemind.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.VmDeepViolet
import com.voicemind.ui.theme.VmLightLavender
import com.voicemind.ui.theme.VmSoftPeriwinkleMist
import com.voicemind.ui.theme.VmTextPrimary
import com.voicemind.ui.theme.VmTextSecondary

@Composable
fun SidebarDrawer(
    currentRoute: String?,
    onNavigate: (Routes) -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = VmSoftPeriwinkleMist,
        modifier = Modifier.width(280.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "VoiceMind AI",
            style = MaterialTheme.typography.titleLarge,
            color = VmTextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        HorizontalDivider(
            color = VmLightLavender.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Routes.drawerItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = if (selected) VmDeepViolet else VmTextSecondary,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) VmDeepViolet else VmTextPrimary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                selected = selected,
                onClick = { onNavigate(item) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = VmDeepViolet.copy(alpha = 0.1f),
                    unselectedContainerColor = VmSoftPeriwinkleMist,
                ),
            )
        }
    }
}
