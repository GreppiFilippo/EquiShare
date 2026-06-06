/** Maps repository errors to user-facing failures. */
package it.unibo.equishare.data.repositories

import kotlinx.coroutines.CancellationException

internal fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
