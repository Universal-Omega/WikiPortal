package org.wikitide.wikiportal.data

import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.network.COMMON_SCRIPT_PATHS
import org.wikitide.wikiportal.network.MediaWikiApi
import org.wikitide.wikiportal.network.deriveArticlePathPrefix
import org.wikitide.wikiportal.network.deriveAvailableSkins
import org.wikitide.wikiportal.network.deriveUncuratedDefaultSkin
import org.wikitide.wikiportal.network.deriveWikiDefaultSkin
import org.wikitide.wikiportal.network.resolveDefaultSkin
import org.wikitide.wikiportal.network.resolveFaviconUrl

/**
 * A wiki's script path, article path prefix, favicon location, and set
 * of installed skins are all discovered once, when a custom wiki is
 * added, see AddWikiViewModel, or seeded from [PresetWikis]'s
 * hardcoded defaults for a preset, see AppRepository's startup
 * reconciliation, and then saved. But they describe the site's
 * own configuration, not anything under this app's control,
 * so they can go stale if a site admin later moves things,
 * for example reconfiguring $wgArticlePath, changing $wgFavicon,
 * installing or removing a skin, or migrating to a new script path
 * entirely, which would otherwise just break the wiki with no way
 * to recover short of removing and re-adding it. This applies
 * equally to presets and custom wikis. A preset is just a wiki this app
 * ships a starting definition for, not a fundamentally different kind
 * of thing, so there is no reason its favicon should go stale forever
 * while a custom wiki's gets fixed automatically.
 *
 * Favicon resolution specifically has a fallback chain. It tries
 * siteinfo's own general.favicon first, then, only if that is missing
 * or hits the un-interpolated placeholder bug some wikis have, see
 * resolveFaviconUrl's comment, parses it back out of the wiki's own
 * rendered HTML, then finally falls back to whatever was already
 * cached if both attempts come up empty this round.
 *
 * [WikiSite.skin], the person's actual choice of skin once they've made
 * one, is never overwritten here. See [resolveDefaultSkin]'s comment
 * for the narrow exception, where nobody has ever actually chosen
 * anything and either the wiki's own reported default, or what it
 * genuinely renders for a phone, see MediaWikiApi.getMobileDefaultSkin,
 * is better than this app's own generic fallback. [WikiSite.availableSkins],
 * meaning which choices are even offered, is a fully separate,
 * always-refreshed concern, from the same siprop=skins data, and
 * refreshing it is exactly the same kind of staleness problem as
 * articlePathPrefix or favicon.
 *
 * The same siteinfo call also reports the wiki's current main page
 * title, general.mainpage, cached directly as [WikiSite.mainPageTitle],
 * so Dashboard and ArticleHostScreen's "go to main page" buttons don't
 * need their own extra network round trip just to know where that
 * button should link. Refreshing it here piggybacks on a call this
 * class is already making, at no extra cost.
 *
 * [AppRepository] calls [refresh] once per app session, the first time
 * a given wiki becomes active, see AppRepository.setActiveWiki. This is
 * a "revalidate on use" approach rather than a scheduled background job,
 * since it only matters for wikis the person is actually using right
 * now, and only needs to happen once per launch to catch anything that
 * changed since last time. [refreshFavicon] is the same idea scoped
 * down for display-only contexts. See its own comment, and
 * AppRepository.refreshFaviconOnly for how the two stay deduped
 * independently of each other.
 */
class WikiMetadataRefresher(private val api: MediaWikiApi) {

    /**
     * Returns null only if the lookup failed outright, for example
     * being offline or the site being temporarily down. Callers should
     * keep using cached values rather than treating a transient failure
     * as "the wiki broke". Returns [site] itself, unchanged, if nothing
     * differed, so callers can tell "nothing changed" apart from "this
     * needs to be saved" with a plain equality check, see
     * AppRepository.refreshWikiMetadata, without a separate flag.
     */
    suspend fun refresh(site: WikiSite): WikiSite? {
        var query = api.getSiteInfo(site).getOrNull()
        var workingScriptPath = site.scriptPath

        // The known script path is tried first, which is the common
        // case of nothing having changed, needing just one request.
        // Only if that now fails do we re-probe the same candidate
        // paths used when first adding the wiki.
        if (query?.general?.sitename == null) {
            for (path in COMMON_SCRIPT_PATHS) {
                if (path == site.scriptPath) continue
                val candidateQuery = api.getSiteInfo(site.copy(scriptPath = path)).getOrNull()
                if (candidateQuery?.general?.sitename != null) {
                    query = candidateQuery
                    workingScriptPath = path
                    break
                }
            }
        }

        val resolvedQuery = query ?: return null
        val resolved = resolvedQuery.general ?: return null
        val sitename = resolved.sitename ?: return null

        val curatedSkins = deriveAvailableSkins(resolvedQuery.skins)
        val uncuratedDefault = deriveUncuratedDefaultSkin(resolvedQuery.skins)
        val resolvedArticlePathPrefix = deriveArticlePathPrefix(site.baseUrl, resolved.articlepath)
        val resolvedMainPageTitle = resolved.mainpage?.takeIf { it.isNotBlank() } ?: "Main Page"
        // Only actually fetched when nobody has chosen a skin yet, the
        // one case resolveDefaultSkin can even use it for. Built on
        // this round's own scriptPath and articlePathPrefix, not
        // site's old ones, in case either just changed. See
        // MediaWikiApi.getMobileDefaultSkin.
        val skinStillUnset = !site.skinIsUserSet && site.skin == WikiSite.DEFAULT_SKIN
        val detectedMobileSkinCode = if (skinStillUnset) {
            val siteForMobileCheck = site.copy(scriptPath = workingScriptPath, articlePathPrefix = resolvedArticlePathPrefix)
            api.getMobileDefaultSkin(siteForMobileCheck, resolvedMainPageTitle).getOrNull()
        } else {
            null
        }
        // Only kept if it's actually one of this app's curated skins.
        // There's nothing to fall back to render wise for a code this
        // app doesn't support, so resolveDefaultSkin treats a null
        // here the same as never having checked at all.
        val detectedMobileSkin = curatedSkins?.firstOrNull { it.code == detectedMobileSkinCode }
        return site.copy(
            name = sitename,
            scriptPath = workingScriptPath,
            articlePathPrefix = resolvedArticlePathPrefix,
            discoveredFaviconUrl = resolveFavicon(resolved.favicon, site),
            // This falls back to whatever is already cached, not null or
            // empty, if this particular probe came back empty. See
            // deriveAvailableSkins' comment for why that is a distinct
            // case from "checked, and it's genuinely none of them".
            availableSkins = curatedSkins ?: site.availableSkins,
            // Refreshed alongside availableSkins, from the same siprop=skins
            // data, since it exists purely to back that field's own
            // last-resort fallback. See WikiSite.uncuratedDefaultSkin.
            uncuratedDefaultSkin = uncuratedDefault ?: site.uncuratedDefaultSkin,
            // resolveDefaultSkin leaves site.skin untouched in every
            // case except when nobody has ever chosen one. See its
            // own comment for the different ways it can still change
            // in that case.
            skin = resolveDefaultSkin(site, deriveWikiDefaultSkin(resolvedQuery.skins), uncuratedDefault, curatedSkins, detectedMobileSkin),
            mainPageTitle = resolvedMainPageTitle,
            mainPageIsDomainRoot = resolved.mainpageisdomainroot,
        )
    }

    /**
     * A deliberately narrow sibling of [refresh], for display contexts
     * that only need a favicon, specifically the wiki picker's row
     * icon, which is the one thing shown there that can actually be
     * wrong before a wiki has ever been revalidated. This skips
     * everything [refresh] does that a row doesn't show. There is no
     * script path re-probing, since the known path is already right in
     * the overwhelming common case, and if it's not, this just leaves
     * the favicon as is rather than paying for the retry loop on every
     * row to self heal something display only, and no
     * articlePathPrefix, availableSkins, skin, or main page title
     * resolution. This is one request in the common case, siteinfo, and
     * a second only if that doesn't yield a usable favicon. See
     * [resolveFavicon].
     *
     * This returns null if nothing usable was found and nothing was
     * already cached, the same "leave it alone on failure" contract as
     * [refresh].
     */
    suspend fun refreshFavicon(site: WikiSite): String? {
        val reportedFavicon = api.getSiteInfo(site).getOrNull()?.general?.favicon
        return resolveFavicon(reportedFavicon, site)
    }

    /**
     * Shared by [refresh] and [refreshFavicon]. This tries siteinfo's
     * own general.favicon first, then, only if that is missing or hits
     * the un-interpolated placeholder bug some wikis have, see
     * resolveFaviconUrl's comment, parses it back out of the wiki's own
     * rendered HTML, then finally falls back to whatever was already
     * cached if both attempts come up empty this round. A transient
     * failure, or a wiki that has simply never had a working favicon,
     * shouldn't wipe out a value from an earlier, successful
     * resolution.
     */
    private suspend fun resolveFavicon(reportedFavicon: String?, site: WikiSite): String? =
        resolveFaviconUrl(reportedFavicon, site.baseUrl)
            ?: api.getFaviconUrlFromHtml(site).getOrNull()
            ?: site.discoveredFaviconUrl
}
