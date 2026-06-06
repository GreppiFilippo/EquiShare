/** Provides shared refresh and cache flow behavior for repositories. */
package it.unibo.equishare.data.repositories

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class RefreshableRepository {
    // Pair: (count, isForced). isForced=true bypasses TTL in refreshableCacheFirst loaders.
    private val trigger = MutableStateFlow(0 to false)

    // Single scope shared by all auth watchers; lives as long as the repository (singleton).
    // Protected so subclasses can launch fire-and-forget background work (e.g. remote writes).
    protected val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    protected fun <T> refreshable(loader: suspend () -> T): Flow<T> =
        trigger.map { loader() }

    // Stale-while-revalidate variant: the lambda receives `isForced` so it can
    // decide whether to bypass the TTL check on explicit refreshes.
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun <T> refreshableCacheFirst(loader: suspend FlowCollector<T>.(isForced: Boolean) -> Unit): Flow<T> =
        trigger.flatMapLatest { (_, forced) -> flow { loader(forced) } }

    open fun refresh() {
        trigger.update { (count, _) -> (count + 1) to true }
    }

    /**
     * Re-reads from cache without forcing a remote fetch.
     * Use this for optimistic local updates (e.g. toggling a favorite) where
     * the UI should reflect the change instantly without waiting for a round-trip.
     * The next full [refresh] will reconcile with remote as usual.
     */
    fun refreshLocal() {
        trigger.update { (count, _) -> (count + 1) to false }
    }

    /**
     * Call from `init` in subclasses that hold user-scoped flows.
     * Every time the user signs in (false→true transition), a forced refresh
     * is triggered so flows that previously emitted empty data (null uid) are
     * re-evaluated with the now-available user ID.
     */
    protected fun watchAuth(isSignedIn: Flow<Boolean>) {
        repositoryScope.launch {
            isSignedIn
                .distinctUntilChanged()
                .filter { it }
                .collect { refresh() }
        }
    }
}
