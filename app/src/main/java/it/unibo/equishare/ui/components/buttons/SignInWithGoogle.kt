/** Renders the Google sign-in button. */
package it.unibo.equishare.ui.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.animations.pressScale
import it.unibo.equishare.ui.theme.EquiShareTheme

enum class GoogleAuthMode { SIGN_IN, SIGN_UP }

private val DefaultGoogleButtonHeight: Dp = 48.dp

@Composable
fun GoogleAuthButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mode: GoogleAuthMode = GoogleAuthMode.SIGN_IN,
    height: Dp = DefaultGoogleButtonHeight,
) {
    val labelRes = when (mode) {
        GoogleAuthMode.SIGN_IN -> R.string.sign_in_with_google
        GoogleAuthMode.SIGN_UP -> R.string.sign_up_with_google
    }
    val minWidth = when (mode) {
        GoogleAuthMode.SIGN_IN -> 210.dp
        GoogleAuthMode.SIGN_UP -> 216.dp
    }
    val interactionSource = remember { MutableInteractionSource() }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerColor = if (isDark) Color(0xFF131314) else Color.White
    val contentColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)
    val borderColor = if (isDark) Color(0xFF8E918F) else Color(0xFF747775)

    Surface(
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
            .height(height)
            .defaultMinSize(minWidth = minWidth)
            .pressScale(interactionSource, pressedScale = 0.97f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}