/** Renders the shared floating action button. */
package it.unibo.equishare.ui.components.buttons

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import it.unibo.equishare.ui.components.animations.pressScale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquiShareFab(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Il Box esterno riceve il modifier di layout (align, padding, ecc.) dal
    // chiamante. TooltipBox non è un semplice layout node: internamente wrappa
    // il contenuto in un proprio Box, rendendo il ParentDataModifier (align)
    // invisibile al Box genitore se applicato direttamente su di esso.
    // Separando i due livelli il posizionamento funziona correttamente in
    // qualsiasi contesto (Scaffold floatingActionButton o Box manuale).
    Box(modifier = modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
            ),
            tooltip = {
                // Il tooltip è visibile solo quando il FAB è collassato: fornisce
                // un'etichetta accessibile per i lettori di schermo che altrimenti
                // non vedrebbero il testo nascosto.
                if (!expanded) {
                    PlainTooltip(
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Assertive
                            paneTitle = contentDescription
                        },
                    ) {
                        Text(contentDescription)
                    }
                }
            },
            state = rememberTooltipState(),
        ) {
            ExtendedFloatingActionButton(
                onClick = { if (enabled) onClick() },
                expanded = expanded,
                icon = icon,
                text = text,
                containerColor = containerColor,
                contentColor = contentColor,
                shape = RoundedCornerShape(16.dp),
                interactionSource = interactionSource,
                modifier = Modifier.pressScale(interactionSource, pressedScale = 0.92f),
            )
        }
    }
}
