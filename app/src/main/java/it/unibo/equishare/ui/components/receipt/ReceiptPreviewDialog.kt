/** Shows a receipt image preview dialog. */
package it.unibo.equishare.ui.components.receipt

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import it.unibo.equishare.R
import it.unibo.equishare.ui.components.image.ImagePreviewDialog

@Composable
fun ReceiptPreviewDialog(
    imageUri: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onChangeReceipt: (() -> Unit)? = null,
) {
    ImagePreviewDialog(
        imageUri = imageUri,
        contentDescription = stringResource(R.string.receipt),
        title = stringResource(R.string.receipt),
        onDismiss = onDismiss,
        onEdit = onChangeReceipt,
        modifier = modifier,
    )
}
