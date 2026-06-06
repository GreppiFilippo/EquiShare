/** Defines the Auth repository contract. */
package it.unibo.equishare.domain.repository

import it.unibo.equishare.domain.model.SignUpOutcome
import kotlinx.coroutines.flow.Flow

enum class AuthState {
    LOADING,
    SIGNED_IN,
    SIGNED_OUT,
}

interface AuthRepository {
    val currentUserEmail: Flow<String?>
    val isSignedIn: Flow<Boolean>
    val authState: Flow<AuthState>
    val currentUserId: String?

    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun isEmailRegistered(email: String): Result<Boolean>
    suspend fun signUp(fullName: String, email: String, password: String): Result<SignUpOutcome>
    suspend fun signInWithGoogleIdToken(googleIdToken: String): Result<Unit>
    suspend fun signOut()
}

class EmailAlreadyRegisteredException : IllegalStateException("Email already registered")
