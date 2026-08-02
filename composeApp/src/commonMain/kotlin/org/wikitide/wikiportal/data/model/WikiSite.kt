package org.wikitide.wikiportal.data.model

import io.ktor.http.URLBuilder
import kotlinx.serialization.Serializable

/** Describes a single MediaWiki site the app can connect to. */
@Serializable
data class WikiSite(
    val id: String,
    val name: String,
    val description: String = "",
    val baseUrl: String,
    val scriptPath: String = "/w",
    val skin: String = DEFAULT_SKIN,
    val isCustom: Boolean = false,
    val articlePathPrefix: String? = null,
    val discoveredFaviconUrl: String? = null,
    val availableSkins: List<SkinOption>? = null,
    /**
     * The wiki's own reported default skin, with its own real display
     * name, regardless of whether it's one of this app's curated
     * [WikiSkins.options]. This exists purely as a last-resort fallback
     * for the skin picker: when [availableSkins] comes back as a real,
     * successfully-fetched but genuinely empty list, meaning the wiki
     * uses no skin this app curates at all, showing the full curated
     * list would be misleading, since none of those are actually
     * installed there. Showing this one skin instead, uncurated, is
     * still more useful than either an empty picker or a list of skins
     * that don't exist on this wiki. See [skinChoices] and
     * [hasNoSkinData].
     */
    val uncuratedDefaultSkin: SkinOption? = null,
    /**
     * Whether [skin] is something the person actually chose, through the
     * "Change skin" picker, as opposed to still being whatever this wiki
     * was seeded or added with. This matters for presets specifically.
     * AppRepository's startup reconciliation uses this to decide whether
     * it is safe to re-sync an existing preset row's skin to match a new
     * shipped default after an app update. It is safe when the person
     * never touched it, and never once they have, since overwriting an
     * explicit choice would be a real regression, not a helpful sync.
     * See AppRepository.init and AppRepository.setWikiSkin.
     */
    val skinIsUserSet: Boolean = false,
    /**
     * The wiki's own reported main page title, general.mainpage from the
     * same siteinfo call that resolves articlePathPrefix,
     * discoveredFaviconUrl, and availableSkins. Null until
     * WikiMetadataRefresher has resolved it at least once. See
     * FeedViewModel and ArticleHostScreen's "go to main page" button
     * for where this is used, and AppRepository.updateMainPageTitle for
     * the one place it's updated outside of a full metadata refresh.
     */
    val mainPageTitle: String? = null,
    /**
     * The wiki's own general.mainpageisdomainroot from that same
     * siteinfo call, true when $wgMainPageIsDomainRoot is set
     * to true.
     */
    val mainPageIsDomainRoot: Boolean = false,
    /**
     * Which [WikiFolder] this wiki is grouped under in the wiki picker,
     * or null for a wiki that sits ungrouped. Presets are seeded with
     * one of [PresetFolders] here so the picker can show a small number
     * of folders instead of a long flat list, and more presets can be
     * added under an existing folder later without the list growing.
     * Custom wikis start out null, meaning "not in a folder yet", and
     * the person can move them into a folder of their own from
     * WikiPickerScreen. See AppRepository.moveWikiToFolder.
     */
    val folderId: String? = null,
    /**
     * Overrides the app-wide "Disable safe mode" setting on for this
     * one wiki, without having to turn it on for every wiki. Combined
     * with the global setting, not a replacement for it, see
     * ArticleHostScreen's effectiveDisableSafeMode: either one being on
     * is enough to disable safe mode for this wiki. See
     * AppRepository.setWikiDisableSafeMode.
     */
    val disableSafeMode: Boolean = false,
) {
    val apiUrl: String get() = "$baseUrl$scriptPath/api.php"
    val indexUrl: String get() = "$baseUrl$scriptPath/index.php"
    val restUrl: String get() = "$baseUrl$scriptPath/rest.php"
    val loadUrl: String get() = "$baseUrl$scriptPath/load.php"

    val faviconUrl: String get() = discoveredFaviconUrl ?: "$baseUrl/favicon.ico"

    val hasNoSkinData: Boolean get() = availableSkins == null
    val skinChoices: List<SkinOption>
        get() {
            val base = when {
                availableSkins.isNullOrEmpty() -> listOfNotNull(uncuratedDefaultSkin)
                else -> availableSkins
            }
            return if (base.any { it.code == skin }) base else base + SkinOption(skin, skin)
        }

    fun articleUrl(
        title: String,
        useAppSkin: Boolean = true,
        safeMode: Boolean = true,
    ): String {
        val encoded = title.replace(" ", "_")
        val base = articlePathPrefix?.let { prefix -> "$prefix$encoded" } ?: "$indexUrl?title=$encoded"
        if (!useAppSkin) return base
        return withSkinParams(base, safeMode) ?: base
    }

    /**
     * Sets, or clears, this wiki's own useskin and safemode params on
     * an arbitrary url. Shared by [articleUrl], building this wiki's
     * own urls from a title, and by WikiArticleReader's
     * RequestInterceptor, rewriting a url this app is about to
     * navigate to that already turned out to belong to this wiki.
     * Returns null if [url] isn't a parseable url at all.
     */
    fun withSkinParams(url: String, safeMode: Boolean = true): String? = runCatching {
        val builder = URLBuilder(url)
        builder.parameters.apply {
            set("useskin", skin)
            if (safeMode) set("safemode", "1") else remove("safemode")
        }
        builder.buildString()
    }.getOrNull()

    fun cleanUrlPrefix(): String = articlePathPrefix ?: "$baseUrl/wiki/"

    companion object {
        const val DEFAULT_SKIN = "vector-2022"
    }
}

@Serializable
data class SkinOption(val code: String, val name: String)

/**
 * Whether safe mode should actually be off for this wiki right now:
 * either the app-wide "Disable safe mode" setting is on, or this wiki
 * has its own [WikiSite.disableSafeMode] override on. Either one is
 * enough; this is never a way to force safe mode back on for a wiki
 * when the app-wide setting is already disabling it everywhere.
 */
fun WikiSite.effectiveDisableSafeMode(globalDisableSafeMode: Boolean): Boolean =
    globalDisableSafeMode || disableSafeMode

/**
 * Skins this app has actually been tested against and is willing to
 * offer in the "Change skin" picker, see WikiPickerScreen. This is a
 * fixed, curated list rather than deriving the choices entirely from
 * siteinfo, since "which skin renders well in this app's narrow
 * WebView" isn't a fact any API can report. What siteinfo's
 * siprop=skins is used for is narrowing this list down further, per
 * wiki, to only the ones actually installed there, see
 * WikiMetadataResolver.deriveAvailableSkins and WikiSite.skinChoices.
 * The picker is always a subset of [options], never a superset.
 */
object WikiSkins {
    val options: List<String> = listOf(
        WikiSite.DEFAULT_SKIN,
        "chameleon",
        "citizen",
        "cosmos",
        "femiwiki",
        "foreground",
        "medik",
        "metrolook",
        "minerva",
        "monobook",
        "refreshed",
        "timeless",
    )
}

/**
 * A named group of wikis in the wiki picker, see WikiPickerScreen. A
 * folder is either one of the app's own [PresetFolders], shipped by
 * default and not removable, or one the person created themselves to
 * organize their own custom wikis, [isCustom] true. Which wikis actually
 * belong to a folder is not stored here. It lives on each [WikiSite] as
 * [WikiSite.folderId], the same way a preset's skin lives on the wiki
 * row rather than on PresetWikis.
 */
@Serializable
data class WikiFolder(
    val id: String,
    val name: String,
    val isCustom: Boolean = false,
)

/** We don't use these right now, but may eventually */
object PresetFolders {
    val MIRAHEZE = WikiFolder(id = "folder-miraheze", name = "Miraheze")
    val WIKIMEDIA = WikiFolder(id = "folder-wikimedia", name = "Wikimedia")

    val all: List<WikiFolder> = listOf(MIRAHEZE, WIKIMEDIA)
}

/**
 * Seed data for the wikis the app ships with a starting definition for.
 * This is not the live source of truth once the app has run once.
 * AppRepository reconciles this list into saved storage on startup. Any
 * id here without a stored row yet gets seeded from these defaults,
 * including [WikiSite.skin], which is otherwise never auto detected, see
 * WikiMetadataRefresher, and any previously stored preset row whose id
 * is no longer listed here, meaning it was removed in an app update,
 * gets pruned. From then on, a preset behaves exactly like a custom
 * wiki. Its articlePathPrefix, favicon, main page title, and
 * availableSkins get auto refreshed the same way, and its skin can be
 * changed from the UI. See AppRepository.presetWikis for the live,
 * saved list.
 *
 * Each entry also carries a [WikiSite.folderId] pointing at one of
 * [PresetFolders], so the picker groups these under a small number of
 * named folders instead of a single long list. Adding another Miraheze
 * or Wikimedia wiki later is then just a new entry here with the
 * matching folder id.
 */
object PresetWikis {
    val MIRAHEZE_META = WikiSite(
        id = "miraheze-meta",
        name = "Miraheze Meta",
        description = "Central coordination wiki for the Miraheze wiki farm",
        baseUrl = "https://meta.miraheze.org",
        skin = "citizen",
        // folderId = PresetFolders.MIRAHEZE.id,
    )
    val WIKIPEDIA_EN = WikiSite(
        id = "wikipedia-en",
        name = "Wikipedia",
        description = "The Free Encyclopedia",
        baseUrl = "https://en.wikipedia.org",
        // folderId = PresetFolders.WIKIMEDIA.id,
    )
    val WIKTIONARY_EN = WikiSite(
        id = "wiktionary-en",
        name = "Wiktionary",
        description = "The Free Dictionary",
        baseUrl = "https://en.wiktionary.org",
        // folderId = PresetFolders.WIKIMEDIA.id,
    )
    val WIKIBOOKS_EN = WikiSite(
        id = "wikibooks-en",
        name = "Wikibooks",
        description = "Open-content textbooks",
        baseUrl = "https://en.wikibooks.org",
        // folderId = PresetFolders.WIKIMEDIA.id,
    )

    val all: List<WikiSite> = listOf(
        MIRAHEZE_META, WIKIPEDIA_EN, WIKTIONARY_EN, WIKIBOOKS_EN,
    )
    val default: WikiSite = MIRAHEZE_META
}
