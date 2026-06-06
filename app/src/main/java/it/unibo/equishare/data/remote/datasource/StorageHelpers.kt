/** Provides shared helpers for Supabase Storage uploads. */
package it.unibo.equishare.data.remote.datasource

internal fun storageExtensionForMime(mimeType: String): String =
    when (mimeType.substringBefore(';').trim().lowercase()) {
        "image/png"  -> "png"
        "image/webp" -> "webp"
        else         -> "jpg"
    }

internal fun cacheBustedStorageUrl(publicUrl: String): String =
    "$publicUrl?v=${System.currentTimeMillis()}"
