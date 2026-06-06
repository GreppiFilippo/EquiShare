/** Renders the app bottom navigation bar. */
package it.unibo.equishare.ui.components.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import it.unibo.equishare.R

enum class BottomNavDestination(
    override val label: String,
    override val icon: androidx.compose.ui.graphics.vector.ImageVector,
    override val id: String
) : NavigationItem {
    GROUPS("Groups", Icons.Default.Group, "groups"),
    ACTIVITY("Activity", Icons.Default.Notifications, "activity"),
    STATISTICS("Statistics", Icons.Default.BarChart, "statistics")
}

@Composable
fun EquiShareBottomNavigation(
    items: List<NavigationItem>,
    selectedItem: NavigationItem?,
    onNavigate: (NavigationItem) -> Unit,
    modifier: Modifier = Modifier,
    showActivityBadge: Boolean = false,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        items.forEach { item ->
            val isSelected = selectedItem?.id == item.id
            val showBadge = item.id == "activity" && showActivityBadge

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (showBadge) {
                                Badge(containerColor = MaterialTheme.colorScheme.error)
                            }
                        }
                    ) {
                        NavigationItemIcon(
                            item = item,
                            isSelected = isSelected
                        )
                    }
                },
                label = {
                    NavigationItemLabel(item = item)
                },
            )
        }
    }
}

@Composable
private fun NavigationItemIcon(
    item: NavigationItem,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = item.icon,
        contentDescription = localizedBottomNavLabel(item),
        modifier = modifier
    )
}

@Composable
private fun NavigationItemLabel(
    item: NavigationItem,
    modifier: Modifier = Modifier
) {
    Text(
        text = localizedBottomNavLabel(item),
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
    )
}

@Composable
private fun localizedBottomNavLabel(item: NavigationItem): String = when (item.id) {
    "groups" -> stringResource(R.string.bottom_nav_groups)
    "activity" -> stringResource(R.string.bottom_nav_activity)
    "statistics" -> stringResource(R.string.bottom_nav_statistics)
    else -> item.label
}

@Composable
fun EquiShareBottomBar(
    selectedDestination: BottomNavDestination,
    onNavigateToDestination: (BottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    showActivityBadge: Boolean = false,
) {
    EquiShareBottomNavigation(
        items = BottomNavDestination.entries,
        selectedItem = selectedDestination,
        onNavigate = { item ->
            val destination = item as? BottomNavDestination
            destination?.let { onNavigateToDestination(it) }
        },
        modifier = modifier,
        showActivityBadge = showActivityBadge
    )
}
