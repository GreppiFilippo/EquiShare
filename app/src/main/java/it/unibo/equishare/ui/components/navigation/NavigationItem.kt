/** Defines bottom navigation item metadata. */
package it.unibo.equishare.ui.components.navigation

import androidx.compose.ui.graphics.vector.ImageVector

interface NavigationItem {
    val label: String
    val icon: ImageVector
    val id: String
}
