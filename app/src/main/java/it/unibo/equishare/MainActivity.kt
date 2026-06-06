/** Hosts the Compose app and navigation entry point. */
package it.unibo.equishare

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import it.unibo.equishare.data.local.AppLanguageManager
import it.unibo.equishare.domain.repository.ProfileRepository
import it.unibo.equishare.ui.EquiShareNavGraph
import it.unibo.equishare.ui.notifications.NotificationNavigationTarget
import it.unibo.equishare.ui.screens.profile.ThemeOption
import it.unibo.equishare.ui.theme.EquiShareTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.KoinAndroidContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {

    private val supabase: SupabaseClient by inject()
    private val profileRepository: ProfileRepository by inject()
    private val appLanguageManager: AppLanguageManager by inject()
    private val notificationTarget = MutableStateFlow<NotificationNavigationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Handle the deep link that opened the app (coming from
        // an email confirmation link).
        supabase.handleDeeplinks(intent)
        if (savedInstanceState == null) {
            notificationTarget.value = NotificationNavigationTarget.fromIntent(intent)
        }
        lifecycleScope.launch { appLanguageManager.syncWithSystemAppLanguage() }
        setContent {
            KoinAndroidContext {
                val selectedTheme by profileRepository.theme.collectAsStateWithLifecycle(
                    initialValue = ThemeOption.SYSTEM
                )
                val selectedLanguageTag by appLanguageManager.languageTag.collectAsStateWithLifecycle(
                    initialValue = appLanguageManager.currentSystemAppLanguageTag()
                )
                val pendingNotificationTarget by notificationTarget.collectAsStateWithLifecycle()
                val darkTheme = when (selectedTheme) {
                    ThemeOption.LIGHT -> false
                    ThemeOption.DARK -> true
                    ThemeOption.SYSTEM -> isSystemInDarkTheme()
                }
                val baseContext = LocalContext.current
                val localizedContext = remember(baseContext, selectedLanguageTag) {
                    appLanguageManager.localizedContext(baseContext, selectedLanguageTag)
                }

                CompositionLocalProvider(LocalContext provides localizedContext) {
                    EquiShareTheme(darkTheme = darkTheme) {
                        val navController = rememberNavController()
                        EquiShareNavGraph(
                            navController = navController,
                            notificationTarget = pendingNotificationTarget,
                            onNotificationTargetConsumed = { notificationTarget.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { appLanguageManager.syncWithSystemAppLanguage() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // App was already running and a deep link brought it to the front.
        supabase.handleDeeplinks(intent)
        notificationTarget.value = NotificationNavigationTarget.fromIntent(intent)
    }
}
