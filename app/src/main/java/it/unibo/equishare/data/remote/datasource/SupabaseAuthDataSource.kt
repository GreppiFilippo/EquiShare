/** Wraps Supabase calls for Auth data. */
package it.unibo.equishare.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseAuthDataSource(private val client: SupabaseClient) {

    val sessionStatus: Flow<SessionStatus> = client.auth.sessionStatus

    val currentUserId: String?
        get() = client.auth.currentUserOrNull()?.id

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, fullName: String) {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            this.data = buildJsonObject { put("full_name", fullName) }
        }
    }

    fun currentSessionOrNull() = client.auth.currentSessionOrNull()

    suspend fun signInWithGoogleIdToken(idToken: String) {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    suspend fun isEmailRegistered(email: String): Boolean {
        val response = client.postgrest.rpc(
            function = "email_is_registered",
            parameters = buildJsonObject { put("p_email", email.trim()) },
        )
        return response.decodeAs()
    }
}
