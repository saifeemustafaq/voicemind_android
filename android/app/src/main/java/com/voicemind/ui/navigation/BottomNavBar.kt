package com.voicemind.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.voicemind.ui.theme.IosAccent
import com.voicemind.ui.theme.IosSecondaryLabel
import com.voicemind.ui.theme.IosSeparator
import com.voicemind.ui.theme.IosWhite

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Routes) -> Unit,
) {
    Column {
        HorizontalDivider(color = IosSeparator, thickness = 0.5.dp)
        NavigationBar(
            containerColor = IosWhite,
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
                        selectedIconColor = IosAccent,
                        selectedTextColor = IosAccent,
                        unselectedIconColor = IosSecondaryLabel,
                        unselectedTextColor = IosSecondaryLabel,
                        indicatorColor = Color.Transparent,
                    )
                )
            }
        }
    }
}
