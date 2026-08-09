package org.wikitide.wikiportal.util

/**
 * Strips or masks the categories of personally identifiable and
 * secret-ish information that could plausibly end up embedded in a
 * URL, header line, or error message this app might log: credentials
 * baked directly into a URL, common auth/session/token query and form
 * parameters, Authorization/Cookie header lines, bearer tokens, email
 * addresses, and IPv4 and IPv6 addresses. Applied once, centrally, in
 * AppLog.record, so every entry that goes into the log buffer or out
 * to the platform's own console, Logcat included, is already redacted
 * before it's stored anywhere, rather than trying to scrub it back out
 * later at display time. DeviceLogReader also runs this again over
 * whatever it parses back out of Android's real logcat, both as a
 * second pass over the same app-sourced text and, more importantly,
 * as the only pass over any non-app system log line that never went
 * through AppLog.record in the first place. Running this twice on the
 * same text is harmless: once a value has already been replaced with
 * a bracketed placeholder like [REDACTED], none of these patterns
 * match that placeholder text, so nothing here compounds.
 *
 * This is a best-effort pattern match over plain text, not a full
 * parse of URLs or HTTP headers, so it can miss a genuinely unusual
 * shape it wasn't written to expect. It deliberately does not touch
 * page titles or search queries a person types. Those are the actual
 * point of a log meant to help debug this app's own requests, not the
 * kind of information these patterns are aimed at. This is only
 * designed to reduce the chances a user accidentally discloses
 * PII in a public venue by posting the logs.
 */
fun redactPii(text: String): String {
    var result = text

    // Credentials embedded directly in a URL, https://user:pass@host/...
    result = result.replace(Regex("""://[^/\s:@]+:[^/\s:@]+@"""), "://[REDACTED]@")

    // Common auth/session/token query or form parameters, key=value,
    // keeping the key name for readability but masking the value.
    result = result.replace(
        Regex(
            """(?i)\b(token|password|passwd|pwd|secret|apikey|api_key|auth|session\w*|lgtoken|csrftoken|edittoken|logintoken|watchtoken|rollbacktoken|centralauthtoken|access_token|refresh_token)=[^&\s"']+""",
        ),
    ) { match -> "${match.groupValues[1]}=[REDACTED]" }

    // Authorization and Cookie header lines. Ktor's own request
    // logging normally omits headers at the LogLevel.INFO this app
    // uses, see MediaWikiApi.configureMediaWikiClient, but this still
    // catches them if that ever changes or a header shows up some
    // other way.
    result = result.replace(
        Regex("""(?im)^(authorization|cookie|set-cookie):.*$""")
    ) { match -> "${match.groupValues[1]}: [REDACTED]" }

    // A bearer token wherever one shows up, not just in a header line.
    result = result.replace(Regex("""(?i)\bBearer\s+[A-Za-z0-9\-._~+/]+=*"""), "Bearer [REDACTED]")

    // Email addresses.
    result = result.replace(Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"""), "[REDACTED EMAIL]")

    // IPv6, full 8-group form.
    result = result.replace(
        Regex("""(?<![0-9a-fA-F:])(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}(?![0-9a-fA-F:])"""),
        "[REDACTED IP]",
    )

    // IPv6, compressed form using "::".
    result = result.replace(
        Regex(
            """(?<![0-9a-fA-F:.])(?:(?:[0-9a-fA-F]{1,4}:){1,7}:(?:[0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{0,4}|::(?:[0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})(?![0-9a-fA-F:.])"""
        ),
        "[REDACTED IP]",
    )

    // IPv4 addresses.
    result = result.replace(Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b"""), "[REDACTED IP]")

    return result
}
