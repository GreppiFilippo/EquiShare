/** Renders the EquiShare logo mark. */
package it.unibo.equishare.ui.components.logo

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import it.unibo.equishare.R

@Composable
fun EquiShareLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.equishare_logo),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier,
    )
}
