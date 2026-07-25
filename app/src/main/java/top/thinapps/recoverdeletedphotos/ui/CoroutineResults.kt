package top.thinapps.recoverdeletedphotos.ui

import kotlinx.coroutines.CancellationException

// preserves structured coroutine cancellation while retaining Result-based error handling
inline fun <R> runCatching(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
