/** Implements the Device Token repository using Supabase and local data. */
package it.unibo.equishare.data.repositories

import it.unibo.equishare.data.remote.datasource.SupabaseDeviceTokenDataSource
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.DeviceTokenRepository

class SupabaseDeviceTokenRepository(
    private val remote: SupabaseDeviceTokenDataSource,
    private val auth: AuthRepository,
) : DeviceTokenRepository {

    override suspend fun registerToken(token: String) {
        if (auth.currentUserId == null) return
        remote.registerToken(token)
    }

    override suspend fun unregisterToken(token: String) {
        val uid = auth.currentUserId ?: return
        remote.unregisterToken(uid, token)
    }
}
