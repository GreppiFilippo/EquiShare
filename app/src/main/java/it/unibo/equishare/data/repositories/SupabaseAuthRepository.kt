/** Implements the Auth repository using Supabase and local data. */
package it.unibo.equishare.data.repositories

import io.github.jan.supabase.auth.status.SessionStatus
import it.unibo.equishare.data.local.EquiShareLocalDataSource
import it.unibo.equishare.data.local.UserPreferencesDataSource
import it.unibo.equishare.data.remote.datasource.SupabaseAuthDataSource
import it.unibo.equishare.domain.model.SignUpOutcome
import it.unibo.equishare.domain.repository.AuthRepository
import it.unibo.equishare.domain.repository.AuthState
import it.unibo.equishare.domain.repository.EmailAlreadyRegisteredException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseAuthRepository(
    private val remote: SupabaseAuthDataSource,
    private val preferences: UserPreferencesDataSource,
    private val local: EquiShareLocalDataSource,
) : AuthRepository {

    override val currentUserEmail: Flow<String?> = remote.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> status.session.user?.email
            else                           -> null
        }
    }

    override val isSignedIn: Flow<Boolean> =
        remote.sessionStatus.map { it is SessionStatus.Authenticated }

    override val authState: Flow<AuthState> =
        remote.sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Initializing   -> AuthState.LOADING
                is SessionStatus.Authenticated  -> AuthState.SIGNED_IN
                // RefreshFailure means a concurrent refresh race was lost; the SDK will
                // emit NotAuthenticated immediately after if the session is truly gone.
                // Map it to LOADING so the UI doesn't flash the login screen prematurely.
                is SessionStatus.RefreshFailure -> AuthState.LOADING
                is SessionStatus.NotAuthenticated -> AuthState.SIGNED_OUT
            }
        }

    override val currentUserId: String?
        get() = remote.currentUserId

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        runCatching { remote.signIn(email, password) }

    override suspend fun isEmailRegistered(email: String): Result<Boolean> =
        runCatching { remote.isEmailRegistered(email) }

    override suspend fun signUp(
        fullName: String,
        email: String,
        password: String,
    ): Result<SignUpOutcome> = runCatching {
        if (isEmailRegistered(email).getOrThrow()) {
            throw EmailAlreadyRegisteredException()
        }
        remote.signUp(email, password, fullName)
        if (remote.currentSessionOrNull() != null) SignUpOutcome.SignedIn
        else SignUpOutcome.EmailConfirmationSent
    }

    override suspend fun signInWithGoogleIdToken(googleIdToken: String): Result<Unit> =
        runCatching { remote.signInWithGoogleIdToken(googleIdToken) }

    override suspend fun signOut() {
        val uid = currentUserId
        uid?.let { local.clearUserData(it) }
        preferences.clear()
        remote.signOut()
    }
}
