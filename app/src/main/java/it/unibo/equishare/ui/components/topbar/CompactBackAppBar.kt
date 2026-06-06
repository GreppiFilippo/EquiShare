/** Renders a compact top bar with back navigation. */
package it.unibo.equishare.ui.components.topbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.unibo.equishare.R

@Composable
fun CompactBackAppBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val topInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
        .coerceAtMost(EquiShareTopBarMaxStatusInset)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .height(EquiShareTopBarHeight)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
