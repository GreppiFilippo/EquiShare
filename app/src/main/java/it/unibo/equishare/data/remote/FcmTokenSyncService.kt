/** Synchronizes the device FCM token with the backend. */
package it.unibo.equishare.data.remote

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.AuthState
import it.unibo.equishare.domain.repository.DeviceTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FcmTokenSyncService(
    private val auth: AuthRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Observe authState (not isSignedIn) so we distinguish LOADING
        // (Supabase session still initializing from DataStore) from a genuine
        // SIGNED_OUT.  Using isSignedIn caused a race condition: LOADING mapped
        // to false, so unregisterCurrentToken() launched an async fetchFcmToken()
        // call; by the time that completed (~200 ms) the session had loaded and
        // currentUserId was no longer null — causing the token that
        // syncCurrentToken() had just written to be immediately deleted.
        auth.authState
            .distinctUntilChanged()
            .onEach { state ->
                when (state) {
                    AuthState.SIGNED_IN  -> syncCurrentToken()
                    AuthState.SIGNED_OUT -> unregisterCurrentToken()
                    AuthState.LOADING    -> { /* Session initializing — do nothing */ }
                }
            }
            .launchIn(scope)
    }

    private suspend fun syncCurrentToken() {
        try {
            val token = fetchFcmToken()
            deviceTokenRepository.registerToken(token)
            Log.d(TAG, "FCM token registered (…${token.takeLast(8)})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync FCM token", e)
        }
    }

    private fun unregisterCurrentToken() {
        scope.launch {
            try {
                val token = fetchFcmToken()
                deviceTokenRepository.unregisterToken(token)
            } catch (_: Exception) { }
        }
    }

    private suspend fun fetchFcmToken(): String =
        suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> cont.resume(token) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    private companion object { const val TAG = "FcmTokenSync" }
}
