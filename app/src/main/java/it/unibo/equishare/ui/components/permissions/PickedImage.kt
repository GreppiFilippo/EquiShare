/** Represents an image selected by the user. */
package it.unibo.equishare.ui.components.permissions

import android.content.Context
import android.net.Uri
import it.unibo.equishare.domain.model.ImageUpload

data class PickedImage(
    val uri: String,
    val upload: ImageUpload,
)

fun Uri.readPickedImage(context: Context): Result<PickedImage> = runCatching {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(this)
        ?.takeIf { it.startsWith("image/") }
        ?: "image/jpeg"
    val bytes = resolver.openInputStream(this)?.use { it.readBytes() }
        ?: error("Unable to read selected image")
    PickedImage(
        uri = toString(),
        upload = ImageUpload(bytes = bytes, mimeType = mimeType),
    )
}
