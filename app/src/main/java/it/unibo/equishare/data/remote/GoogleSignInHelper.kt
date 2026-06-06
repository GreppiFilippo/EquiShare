/** Handles Google credential sign-in for Supabase Auth. */
package it.unibo.equishare.data.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import it.unibo.equishare.BuildConfig

class GoogleSignInHelper {

    suspend fun getCredential(context: Context): GoogleSignInCredential {
        val credential = requestGoogleCredential(context)
        return GoogleSignInCredential(
            idToken = credential.idToken,
            email = credential.id,
        )
    }

    suspend fun getIdToken(context: Context): String {
        return requestGoogleCredential(context).idToken
    }

    private suspend fun requestGoogleCredential(context: Context): GoogleIdTokenCredential {
        require(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
            "GOOGLE_WEB_CLIENT_ID missing: fill it in local.properties"
        }

        val activityContext = context.findActivity()
            ?: error("Google Sign-In requires an Activity context")

        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val response = try {
            CredentialManager.create(activityContext).getCredential(
                context = activityContext,
                request = request,
            )
        } catch (e: NoCredentialException) {
            throw GoogleSignInNoCredentialException(e)
        }

        val credential = response.credential
        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) {
            "Unexpected credential type: ${credential::class.java.name}"
        }
        return GoogleIdTokenCredential.createFrom(credential.data)
    }
}

data class GoogleSignInCredential(
    val idToken: String,
    val email: String,
)

class GoogleSignInNoCredentialException(
    cause: NoCredentialException,
) : IllegalStateException("No Google credential is available for sign-in.", cause)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
