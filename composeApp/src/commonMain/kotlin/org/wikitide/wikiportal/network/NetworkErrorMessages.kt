package org.wikitide.wikiportal.network

/**
 * Turns a network-layer failure into something a person can read and
 * act on, instead of the raw exception type and message that used to
 * get shown directly on the Dashboard, for example
 * "UnknownHostException: en.wikipedia.org". This matches loosely on
 * the exception's class name and message rather than its concrete
 * type, since the actual exception Ktor throws for what is
 * functionally the same failure, no connection, a bad host, a slow
 * server, differs across Android, iOS, desktop, and the web build.
 * The original exception is still worth logging separately wherever
 * this is called from, see AppLog, this only produces the text meant
 * for the screen.
 */
fun friendlyNetworkErrorMessage(error: Throwable): String {
    val signature = "${error::class.simpleName.orEmpty()} ${error.message.orEmpty()}".lowercase()
    return when {
        "unknownhost" in signature || "resolv" in signature || "dns" in signature ->
            "Can't reach this wiki. Check your connection or the wiki's address."
        "timeout" in signature || "timed out" in signature ->
            "This wiki took too long to respond."
        "refused" in signature ->
            "Couldn't connect to this wiki."
        "connect" in signature ->
            "Couldn't connect to this wiki. Check your connection."
        "ssl" in signature || "certificate" in signature || "handshake" in signature ->
            "This wiki's connection couldn't be verified."
        "serializ" in signature || "unexpected" in signature || "json" in signature ->
            "This wiki sent back something the app didn't expect."
        "cancell" in signature ->
            "The request was cancelled."
        else ->
            "Couldn't load this wiki right now."
    }
}
