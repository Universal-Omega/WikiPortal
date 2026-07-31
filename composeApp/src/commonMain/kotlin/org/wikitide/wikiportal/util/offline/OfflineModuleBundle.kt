package org.wikitide.wikiportal.util.offline

import io.ktor.http.URLBuilder
import io.ktor.http.decodeURLQueryComponent
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
 * A real page's own CSS very often arrives through one or more plain,
 * static `<link>` tags the skin's own template writes directly,
 * modules=a|b|c and all, rather than through RLPAGEMODULES or a
 * loader call. Vector 2022's actual site.styles and skins.vector...
 * links work exactly this way. Naming those same modules again here,
 * fed into the guaranteed only=styles fetch below, is what actually
 * makes sure that CSS applies even if something about the original
 * static tag itself doesn't carry over cleanly once inlined.
 */
private val MODULES_QUERY_PARAM = Regex("""[?&]modules=([^&"'\s]+)""")

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
private val ALWAYS_REQUESTED_MODULES = setOf("mediawiki.page.ready")

private fun extractReferencedModuleNames(html: String): Set<String> {
    val decodedHtml = html.decodeHtmlEntities()
    val names = mutableSetOf<String>()
    for (match in MODULE_LIST_ASSIGNMENT_OR_CALL.findAll(decodedHtml)) {
        for (nameMatch in QUOTED_STRING.findAll(match.groupValues[1])) {
            names += nameMatch.groupValues[1]
        }
    }

    for (match in MODULES_QUERY_PARAM.findAll(decodedHtml)) {
        val decoded = match.groupValues[1].decodeURLQueryComponent()
        names += decoded.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }

    return names
}

/** [css] is ready to drop straight into a `<style>` tag, [js] into a `<script>` tag. Either is null if that request came back empty or failed. */
class OfflineModules(val css: String?, val js: String?)

/**
 * A live page only ships the ResourceLoader startup module up front,
 * in its own script tag, already picked up like any other by
 * inlineResourcesAsDataUris. Everything past that, the actual
 * per-page modules, normally loads afterward, at runtime, the moment
 * the page's own scripts ask for it, which can never succeed offline.
 *
 * Style and script are fetched as two separate requests here, with
 * only=styles and only=scripts, deliberately not one combined request
 * left for mw.loader.implement(...) to apply through the real
 * ResourceLoader JS runtime. only=styles hands back plain CSS text,
 * nothing to execute, nothing that can silently fail to apply; that
 * guarantee is worth a second network round trip. [css] and [js] are
 * injected separately by OfflinePageCapture, css as a static
 * `<style>` tag in head, js as one more best-effort `<script>` block
 * alongside the collapsible fallback, where it stays free to fail on
 * its own without taking styling down with it.
 *
 * This is still a best-effort emulation of a real page's own module
 * loading, not a guarantee. It covers modules the page names
 * explicitly, plus the small fixed set above. A module some other,
 * indirect, runtime-computed path pulls in isn't something static
 * text scanning can find, and won't be available offline.
 */
suspend fun fetchOfflineModules(html: String, site: WikiSite, api: MediaWikiApi): OfflineModules {
    val moduleNames = extractReferencedModuleNames(html) + ALWAYS_REQUESTED_MODULES
    if (moduleNames.isEmpty()) return OfflineModules(null, null)
    return OfflineModules(
        css = fetchModuleBundle(moduleNames, site, api, only = "styles"),
        js = fetchModuleBundle(moduleNames, site, api, only = "scripts"),
    )
}

private suspend fun fetchModuleBundle(moduleNames: Set<String>, site: WikiSite, api: MediaWikiApi, only: String): String? {
    val url = URLBuilder(site.loadUrl).apply {
        parameters.append("skin", site.skin)
        parameters.append("only", only)
        parameters.append("modules", moduleNames.joinToString("|"))
    }.buildString()

    val (_, bytes) = api.getRawBytes(url).getOrNull() ?: return null
    return bytes.decodeToString().takeIf { it.isNotBlank() }
}
