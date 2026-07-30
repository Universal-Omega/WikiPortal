package org.wikitide.wikiportal.offline

import io.ktor.http.URLBuilder
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.MediaWikiApi

/**
 * Modules a page's own script tags name by hand, RLPAGEMODULES's own
 * list, or the argument to a literal mw.loader.load(...)/using(...)
 * call. Both look like ["a.module", "another.module"] in the raw page
 * text, so one pattern covers both. This is plain text scanning, not
 * parsing, on purpose: MediaWiki has shipped this exact shape,
 * unchanged, for a very long time, and a real JS parser is a lot of
 * dependency weight for two known, stable call shapes.
 */
private val MODULE_LIST_ASSIGNMENT_OR_CALL = Regex("""(?:RLPAGEMODULES\s*=|mw\.loader\.(?:load|using)\()\s*\[([^]]*)]""")
private val QUOTED_STRING = Regex("""['"]([^'"]+)['"]""")

/**
 * Small, core, ship-with-every-install modules this app asks for
 * regardless of what scanning [html] turns up. jquery.makeCollapsible
 * is the actual plugin behind collapsible sections and navboxes
 * (mw-collapsible), and mediawiki.page.ready is the module that calls
 * it automatically on page load; a page using either almost never
 * names them directly in its own script text, since normally the
 * ResourceLoader startup module resolves that dependency at runtime
 * without it ever appearing as a literal string anywhere.
 */
private val ALWAYS_REQUESTED_MODULES = setOf("jquery.makeCollapsible", "mediawiki.page.ready")

private fun extractReferencedModuleNames(html: String): Set<String> {
    val names = mutableSetOf<String>()
    for (match in MODULE_LIST_ASSIGNMENT_OR_CALL.findAll(html)) {
        for (nameMatch in QUOTED_STRING.findAll(match.groupValues[1])) {
            names += nameMatch.groupValues[1]
        }
    }
    return names
}

/**
 * A live page only ships the ResourceLoader startup module up front,
 * in its own script tag, already picked up like any other by
 * inlineResourcesAsDataUris. Everything past that, the actual
 * per-page modules, loads afterward through mw.loader.using(...)
 * calls queued in the page's own inline scripts, which normally fetch
 * from load.php over the network at the moment they're needed.
 * Offline, that fetch can never succeed.
 *
 * This finds every module name the page's own scripts are going to
 * ask for, fetches them all up front as one combined load.php bundle,
 * script and style together the way a dynamically-loaded module
 * normally arrives, and returns it ready to inline as one more
 * `<script>` tag. By the time those mw.loader.using(...) calls
 * actually run, the modules they want are already registered locally,
 * and nothing needs the network at all.
 *
 * This is a best-effort emulation of a real ResourceLoader client
 * bootstrap, not a guarantee. It covers modules the page names
 * explicitly, plus the small fixed set above. A module some other,
 * indirect, runtime-computed path pulls in isn't something static
 * text scanning can find, and won't be available offline.
 */
suspend fun fetchOfflineModuleBundle(html: String, site: WikiSite, api: MediaWikiApi): String? {
    val moduleNames = extractReferencedModuleNames(html) + ALWAYS_REQUESTED_MODULES
    if (moduleNames.isEmpty()) return null

    val url = URLBuilder(site.loadUrl).apply {
        parameters.append("skin", site.skin)
        parameters.append("modules", moduleNames.joinToString("|"))
    }.buildString()

    val (_, bytes) = api.getRawBytes(url).getOrNull() ?: return null
    val script = bytes.decodeToString()
    return if (script.isNotBlank()) "<script>$script</script>" else null
}
