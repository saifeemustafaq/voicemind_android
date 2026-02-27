package com.voicemind.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.VmDeepViolet
import com.voicemind.ui.theme.VmLightLavender
import com.voicemind.ui.theme.VmTextSecondary

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Routes) -> Unit,
) {
    NavigationBar(
        containerColor = VmLightLavender.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
    ) {
        Routes.drawerItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = VmDeepViolet,
                    selectedTextColor = VmDeepViolet,
                    unselectedIconColor = VmTextSecondary,
                    unselectedTextColor = VmTextSecondary,
                    indicatorColor = VmDeepViolet.copy(alpha = 0.12f),
                )
            )
        }
    }
}
