/** Renders user and member avatars. */
package it.unibo.equishare.ui.components.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    displayName: String? = null,
    size: Dp = 32.dp,
    onClicked: () -> Unit = {},
    contentDescription: String = "Profile",
) {
    when {
        imageUrl != null -> {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .clickable { onClicked() }
            )
        }
        else -> {
            DefaultAvatar(
                modifier = modifier,
                displayName = displayName,
                size = size,
                onClicked = onClicked,
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
internal fun DefaultAvatar(
    modifier: Modifier = Modifier,
    displayName: String? = null,
    size: Dp = 32.dp,
    onClicked: () -> Unit = {},
    contentDescription: String = "Default profile",
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClicked() },
        contentAlignment = Alignment.Center,
    ) {
        val initials = displayName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            val parts = it.split("\\s+".toRegex())
            if (parts.size >= 2) {
                "${parts[0].take(1)}${parts[1].take(1)}"
            } else {
                parts[0].take(1)
            }
        }?.uppercase()

        if (initials != null) {
            Text(
                text = initials,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}
