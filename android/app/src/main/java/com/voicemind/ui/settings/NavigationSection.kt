package com.voicemind.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voicemind.ui.components.GlassCard
import com.voicemind.ui.navigation.Routes
import com.voicemind.ui.theme.VmDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavigationSection(
    useSidebar: Boolean,
    defaultLandingPage: String,
    navOrder: List<String>,
    onToggleNavMode: () -> Unit,
    onSetDefaultLandingPage: (String) -> Unit,
    onMoveNavItem: (route: String, moveUp: Boolean) -> Unit,
) {
    val orderedNavItems = Routes.orderedItems(navOrder)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use sidebar navigation",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (useSidebar) "Swipe or tap menu to open drawer"
                               else "Tabs shown at the bottom of the screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = useSidebar,
                    onCheckedChange = { onToggleNavMode() },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

            Text(
                text = "Default landing page",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Screen shown when the app opens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

            val landingOptions = listOf("recordings" to "Recordings", "checklist" to "Checklist", "folders" to "Folders")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                landingOptions.forEachIndexed { index, (route, label) ->
                    SegmentedButton(
                        selected = defaultLandingPage == route,
                        onClick = { onSetDefaultLandingPage(route) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = landingOptions.size),
                        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = VmDimens.SpaceMd))

            Text(
                text = "Tab order",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Rearrange the order of navigation tabs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(VmDimens.SpaceSm))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.shapes.medium,
                    )
                    .padding(vertical = VmDimens.SpaceSm, horizontal = VmDimens.SpaceXs),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    orderedNavItems.forEachIndexed { index, item ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = item.outlinedIcon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(VmDimens.SpaceXxs))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.height(VmDimens.SpaceXs))
                            Row {
                                IconButton(
                                    onClick = { onMoveNavItem(item.route, true) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.KeyboardArrowLeft,
                                        contentDescription = "Move left",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (index > 0) MaterialTheme.colorScheme.onSurface
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveNavItem(item.route, false) },
                                    enabled = index < orderedNavItems.lastIndex,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.KeyboardArrowRight,
                                        contentDescription = "Move right",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (index < orderedNavItems.lastIndex) MaterialTheme.colorScheme.onSurface
                                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
