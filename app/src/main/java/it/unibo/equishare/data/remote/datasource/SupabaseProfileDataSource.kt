/** Defines Supabase Profile Data Source app code. */
package it.unibo.equishare.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import it.unibo.equishare.data.remote.dto.ProfileDto

class SupabaseProfileDataSource(private val client: SupabaseClient) {

    suspend fun fetchProfile(uid: String): ProfileDto? =
        client.postgrest.from("profiles")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull()

    suspend fun updateName(uid: String, fullName: String) {
        client.postgrest.from("profiles").update({
            set("full_name", fullName)
        }) { filter { eq("id", uid) } }
    }

    suspend fun updateAvatarUrl(uid: String, url: String?) {
        client.postgrest.from("profiles").update({
            set("avatar_url", url)
        }) { filter { eq("id", uid) } }
    }

    suspend fun updateDefaultCurrency(uid: String, currencyCode: String) {
        client.postgrest.from("profiles").update({
            set("default_currency", currencyCode)
        }) { filter { eq("id", uid) } }
    }

    suspend fun uploadAvatar(uid: String, bytes: ByteArray, mimeType: String): String {
        val extension = storageExtensionForMime(mimeType)
        val path = "$uid/avatar.$extension"
        val bucket = client.storage.from(AVATARS_BUCKET)
        bucket.upload(path = path, data = bytes) { upsert = true }
        return cacheBustedStorageUrl(bucket.publicUrl(path))
    }

    private companion object {
        const val AVATARS_BUCKET = "avatars"
    }
}
