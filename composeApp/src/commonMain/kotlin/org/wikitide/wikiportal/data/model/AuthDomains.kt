package org.wikitide.wikiportal.data.model

import io.ktor.http.Url

/**
 * Hosts that a wiki farm's shared login flow can bounce a browsing
 * session through mid-navigation, for example Miraheze's or
 * Wikimedia's central auth. A request to one of these is not really a
 * new destination the person is visiting, it is a detour on the way
 * back to whichever saved wiki sent them there, so it gets treated as
 * that same wiki everywhere this app cares about "which site is
 * this": it keeps that wiki's chosen skin rather than the auth host's
 * own default, never triggers the "leaving this site" confirmation,
 * and keeps being attributed to that wiki for the toolbar title, the
 * tabs list, and history, rather than showing up as a separate site
 * of its own. See ArticleHostScreen's RequestInterceptor and
 * WikiArticleReader's title tracking for where this gets applied.
 * Add another wiki farm's login host here to extend the same
 * treatment to it.
 */
object AuthDomains {
    private val hosts = setOf(
        "auth.miraheze.org",
        "auth.wikimedia.org",
    )

    fun matches(url: String): Boolean {
        val host = runCatching { Url(url).host }.getOrNull() ?: return false
        return host in hosts
    }
}
