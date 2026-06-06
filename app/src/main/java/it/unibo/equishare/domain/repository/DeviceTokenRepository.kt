/** Defines the Device Token repository contract. */
package it.unibo.equishare.domain.repository

/**
 * Persists the FCM registration token associated with the current user.
 *
 * Each device has exactly one row in `user_devices` keyed by `(user_id, token)`.
 * When the user signs out, the row for the current token is removed so a
 * different user signing in on the same device doesn't keep receiving the
 * previous user's push notifications.
 */
interface DeviceTokenRepository {
    /** Upsert the FCM token for the currently signed-in user. No-op if logged out. */
    suspend fun registerToken(token: String)

    /** Remove the given token (called on sign-out). */
    suspend fun unregisterToken(token: String)
}
