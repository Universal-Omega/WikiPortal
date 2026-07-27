package org.wikitide.wikiportal.data.model

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
     * ExploreViewModel and ArticleHostScreen's "go to main page" button
     * for where this is used, and AppRepository.updateMainPageTitle for
     * the one place it's updated outside of a full metadata refresh.
     */
    val mainPageTitle: String? = null,
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
) {
    val apiUrl: String get() = "$baseUrl$scriptPath/api.php"
    val indexUrl: String get() = "$baseUrl$scriptPath/index.php"
    val restUrl: String get() = "$baseUrl$scriptPath/rest.php"

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

    fun articleUrl(title: String, useAppSkin: Boolean = true): String {
        val encoded = title.replace(" ", "_")
        return buildString {
            append(indexUrl)
            append("?title=")
            append(encoded)
            if (useAppSkin) {
                append("&useskin=")
                append(skin)
                append("&safemode=1")
            }
        }
    }

    fun cleanUrlPrefix(): String = articlePathPrefix ?: "$baseUrl/wiki/"

    companion object {
        const val DEFAULT_SKIN = "vector-2022"
    }
}

@Serializable
data class SkinOption(val code: String, val name: String)

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
