/** Renders the main app top bar. */
package it.unibo.equishare.ui.components.topbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.avatar.Avatar
import it.unibo.equishare.ui.components.logo.EquiShareLogo

@Composable
fun EquiShareAppBar(
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    displayName: String? = null,
    onAvatarClick: () -> Unit = {}
) {
    val topInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
        .coerceAtMost(EquiShareTopBarMaxStatusInset)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .height(EquiShareTopBarHeight)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppBarTitle(modifier = Modifier.weight(1f))
            AvatarAction(
                avatarUrl = avatarUrl,
                displayName = displayName,
                onAvatarClick = onAvatarClick,
            )
        }
    }
}

@Composable
private fun AppBarTitle(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        EquiShareLogo(modifier = Modifier.size(EquiShareTopBarIconSize))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AvatarAction(
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    displayName: String? = null,
    onAvatarClick: () -> Unit = {}
) {
    IconButton(
        onClick = onAvatarClick,
        modifier = modifier.padding(end = 4.dp)
    ) {
        Avatar(
            imageUrl = avatarUrl,
            displayName = displayName,
            size = EquiShareTopBarIconSize,
            onClicked = onAvatarClick,
            contentDescription = stringResource(R.string.profile_picture)
        )
    }
}
