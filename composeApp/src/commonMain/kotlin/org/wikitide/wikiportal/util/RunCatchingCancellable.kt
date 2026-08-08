package org.wikitide.wikiportal.util

import kotlinx.coroutines.CancellationException

/**
 * A coroutine-safe replacement for [kotlin.runCatching]. kotlin.runCatching
 * catches everything, including [CancellationException], which is
 * exactly the exception structured concurrency relies on propagating to
 * actually cancel a coroutine, and everything it started, when whatever
 * owns it goes away, for example a ViewModel getting cleared, a wiki
 * switch happening mid fetch, or a screen closing. Swallowing it here
 * wouldn't just misreport what happened. The coroutine that was
 * supposed to die keeps running, silently, doing network work nothing
 * is waiting on anymore, which is exactly the failure mode structured
 * concurrency exists to prevent.
 *
 * This behaves identically to [kotlin.runCatching] for every other
 * exception, and only every other exception. [CancellationException] is
 * rethrown instead of captured. Every runCatching in the network
 * package, see [ActionApiClient], [RestApiClient],
 * [MediaWikiApi.getFaviconUrlFromHtml], and [MediaWikiApi.getRawBytes],
 * goes through this instead of the stdlib one.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
