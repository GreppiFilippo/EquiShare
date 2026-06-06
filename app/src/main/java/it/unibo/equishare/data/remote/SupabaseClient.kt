/** Creates and configures the Supabase client. */
package it.unibo.equishare.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import it.unibo.equishare.BuildConfig

fun createSupabase(): SupabaseClient = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
) {
    install(Auth) {
        autoLoadFromStorage = true
        autoSaveToStorage = true
        // Must match the <intent-filter> in AndroidManifest.xml; Supabase rewrites
        // auth links (email confirmation, OAuth callback) to equishare://login-callback.
        scheme = "equishare"
        host   = "login-callback"
    }
    install(Postgrest)
    install(Storage)
    install(Realtime)
}
