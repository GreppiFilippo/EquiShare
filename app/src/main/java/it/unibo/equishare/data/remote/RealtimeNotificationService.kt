/** Keeps in-app activity notifications updated in realtime. */
package it.unibo.equishare.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import it.unibo.equishare.data.local.UserPreferencesDataSource
import it.unibo.equishare.data.remote.dto.ActivityLogDto
import it.unibo.equishare.domain.repository.ActivityRepository
import it.unibo.equishare.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

// System-tray notifications are handled by EquiShareFirebaseMessagingService.
// This class only refreshes the in-app badge/list when the app is in foreground.
// The polling loop is a fallback for when Realtime can't connect.
class RealtimeNotificationService(
    private val client: SupabaseClient,
    private val auth: AuthRepository,
    private val activityRepository: ActivityRepository,
    private val userPreferences: UserPreferencesDataSource,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            try {
                client.realtime.connect()
                listenForActivity()
            } catch (e: Exception) {
                // Realtime might fail on network issues, polling fallback will handle it
            }
        }
        startPolling()
    }

    private fun listenForActivity() {
        val channel = client.channel("activity_notifications")
        val changes = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "activity_log"
        }

        changes.onEach { action ->
            val log = action.decodeRecord<ActivityLogDto>()
            // Refresh the Activity Center; system notification is delivered via FCM.
            refreshActivityFeed()
            userPreferences.setLastNotifiedAt(log.createdAt)
        }.launchIn(scope)

        scope.launch { channel.subscribe() }
    }

    private fun startPolling() {
        scope.launch {
            while (true) {
                try {
                    val currentUid = auth.currentUserId
                    if (currentUid != null) {
                        val lastNotified = userPreferences.lastNotifiedAt.first()
                        val lastNotifiedTime = lastNotified?.let { OffsetDateTime.parse(it) }

                        val logs = client.postgrest.from("activity_log")
                            .select {
                                order("created_at", Order.DESCENDING)
                                limit(10)
                            }
                            .decodeList<ActivityLogDto>()

                        val newLogs = logs.filter { log ->
                            val logTime = OffsetDateTime.parse(log.createdAt)
                            lastNotifiedTime == null || logTime.isAfter(lastNotifiedTime)
                        }
                        if (newLogs.isNotEmpty()) {
                            refreshActivityFeed()
                            // Track the newest timestamp so we don't reprocess.
                            newLogs.maxByOrNull { it.createdAt }?.let {
                                userPreferences.setLastNotifiedAt(it.createdAt)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore and retry next time
                }
                delay(60_000) // Poll every minute
            }
        }
    }

    private suspend fun refreshActivityFeed() {
        try {
            activityRepository.refresh()
        } catch (e: Exception) {
            // Repository might not be initialized or active, ignore
        }
    }
}
