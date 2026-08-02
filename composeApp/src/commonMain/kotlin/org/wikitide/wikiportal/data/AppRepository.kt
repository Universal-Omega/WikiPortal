package org.wikitide.wikiportal.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.wikitide.wikiportal.data.model.PresetWikis
import org.wikitide.wikiportal.data.model.SavedPage
import org.wikitide.wikiportal.data.model.ThemeMode
import org.wikitide.wikiportal.data.model.WikiFolder
import org.wikitide.wikiportal.data.model.WikiSite
import org.wikitide.wikiportal.data.store.SettingKeys
import org.wikitide.wikiportal.data.store.WikiPortalStore
import kotlin.random.Random

/**
 * A reactive layer over [WikiPortalStore]. The store itself is a plain
 * suspend API, so it can be backed by SQLDelight or browser storage. This
 * class loads it once at startup into [StateFlow]s so Compose screens
 * recompose right away when something changes, and it saves writes in the
 * background.
 */
class AppRepository(
    private val store: WikiPortalStore,
    private val appScope: CoroutineScope,
    private val metadataRefresher: WikiMetadataRefresher,
) {
    private val _activeWiki = MutableStateFlow(PresetWikis.default)
    val activeWiki: StateFlow<WikiSite> = _activeWiki

    /**
     * The live, saved preset list. It starts out seeded from
     * [PresetWikis]'s hardcoded defaults, but stops being equal to it once
     * the app has run once, see the reconciliation in [init]. This is
     * different from [customWikis] mainly in that presets are not freely
     * removable by the user, and get their initial fields from a shipped
     * default rather than a URL someone typed in. Otherwise a preset
     * behaves exactly like a custom wiki from here on, auto refreshed the
     * same way, with an editable skin, and so on. This starts out equal
     * to [PresetWikis.all] itself, not empty, so presets are available
     * right away before the startup reconciliation below finishes.
     * Screens that read this during initial composition should not see
     * an empty list just because the database read hasn't landed yet.
     */
    private val _presetWikis = MutableStateFlow(PresetWikis.all)
    val presetWikis: StateFlow<List<WikiSite>> = _presetWikis

    private val _customWikis = MutableStateFlow<List<WikiSite>>(emptyList())
    val customWikis: StateFlow<List<WikiSite>> = _customWikis

    /**
     * Folders the person created themselves, so their own custom wikis
     * can be grouped in WikiPickerScreen the same way [PresetWikis] are
     * grouped under [PresetFolders]. Ordered the way they were created
     * in. See [createFolder], [renameFolder], [deleteFolder], and
     * [moveWikiToFolder].
     */
    private val _customFolders = MutableStateFlow<List<WikiFolder>>(emptyList())
    val customFolders: StateFlow<List<WikiFolder>> = _customFolders

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor

    private val _textScale = MutableStateFlow(1.0f)
    val textScale: StateFlow<Float> = _textScale

    private val _showImages = MutableStateFlow(true)
    val showImages: StateFlow<Boolean> = _showImages

    private val _openLinksExternally = MutableStateFlow(false)
    val openLinksExternally: StateFlow<Boolean> = _openLinksExternally

    private val _confirmExternalNavigation = MutableStateFlow(true)
    val confirmExternalNavigation: StateFlow<Boolean> = _confirmExternalNavigation

    /** Off by default. When on, wiki pages load without MediaWiki's safemode=1 param. */
    private val _disableSafeMode = MutableStateFlow(false)
    val disableSafeMode: StateFlow<Boolean> = _disableSafeMode

    /** When on, links with target="_blank" open as a real new browser tab instead of inside the app's WebView. */
    private val _openBlankInNewTab = MutableStateFlow(false)
    val openBlankInNewTab: StateFlow<Boolean> = _openBlankInNewTab

    private val _savedPages = MutableStateFlow<List<SavedPage>>(emptyList())
    val savedPages: StateFlow<List<SavedPage>> = _savedPages

    private val _history = MutableStateFlow<List<SavedPage>>(emptyList())
    val history: StateFlow<List<SavedPage>> = _history

    /**
     * A set of "wikiId|title" keys that have offline content saved. This
     * is kept as a Set so the UI can do cheap contains checks, for
     * example the download icon's filled or outline state, without
     * scanning a list on every recomposition.
     */
    private val _offlineKeys = MutableStateFlow<Set<String>>(emptySet())
    val offlineKeys: StateFlow<Set<String>> = _offlineKeys

    /** The display list for the Saved screen's "Offline" tab. */
    private val _offlineArticles = MutableStateFlow<List<SavedPage>>(emptyList())
    val offlineArticles: StateFlow<List<SavedPage>> = _offlineArticles

    init {
        appScope.launch {
            val stored = store.allStoredWikis()
            val presetDefaultsById = PresetWikis.all.associateBy { it.id }
            val (storedPresetRows, otherRows) = stored.partition { it.id in presetDefaultsById }

            // otherRows means "not a current preset id". That covers both
            // genuine user-added custom wikis and stale rows for presets
            // that used to exist but were removed from the app in an
            // update. This splits on each row's own isCustom flag rather
            // than treating "not currently a preset id" as "therefore
            // custom". Otherwise a removed preset would show up under
            // "Your wikis" for the rest of this session even though it is
            // correctly deleted from the database below.
            val storedCustomRows = otherRows.filter { it.isCustom }
            val staleRows = otherRows.filterNot { it.isCustom }

            // Seeding step. Any current preset id with no stored row yet,
            // meaning a first run, or a preset added in an app update,
            // gets inserted from its shipped default, including the
            // skin, which is otherwise never auto detected. See
            // WikiMetadataRefresher.
            val missingPresets = presetDefaultsById.values.filter { it.id !in storedPresetRows.map { row -> row.id } }
            missingPresets.forEach { store.upsertWiki(it) }

            // Re-sync step. An existing preset row's skin only gets
            // updated to match a new shipped default, for example if an
            // app update changed Miraheze Meta's default from "citizen"
            // to something else, if the person never explicitly picked a
            // skin for it themselves. That is tracked by skinIsUserSet,
            // set by setWikiSkin. Otherwise this would silently overwrite
            // an intentional choice on every update. This does not touch
            // anything else auto refresh already owns, like
            // articlePathPrefix, favicon, or availableSkins. It only
            // touches skin, which WikiMetadataRefresher deliberately
            // never touches.
            val resyncedPresetRows = storedPresetRows.map { stored ->
                val shippedDefault = presetDefaultsById.getValue(stored.id)
                if (!stored.skinIsUserSet && stored.skin != shippedDefault.skin) {
                    stored.copy(skin = shippedDefault.skin)
                } else {
                    stored
                }
            }
            resyncedPresetRows.forEachIndexed { i, resynced ->
                if (resynced != storedPresetRows[i]) store.upsertWiki(resynced)
            }

            // Pruning step. Drop the stale local record rather than
            // keeping it around forever with no way to reach it from the
            // UI.
            staleRows.forEach { store.removeWiki(it.id) }

            // This is ordered by PresetWikis.all itself, not by however
            // storedPresetRows happened to come back. allStoredWikis() is
            // backed by "SELECT * FROM Wiki ORDER BY name ASC", which is a
            // reasonable default for a generic "list everything" query.
            // That is invisible on a first run, since nothing is stored
            // yet and _presetWikis.value below was built from
            // missingPresets and presetDefaultsById.values, which does
            // preserve PresetWikis.all's order. But it silently reshuffles
            // into alphabetical order on every run after that, once these
            // rows actually exist in the database and get read back
            // through that query.
            val resyncedById = resyncedPresetRows.associateBy { it.id }
            val missingById = missingPresets.associateBy { it.id }
            _presetWikis.value = PresetWikis.all.map { resyncedById[it.id] ?: missingById.getValue(it.id) }
            _customWikis.value = storedCustomRows
            _customFolders.value = store.allFolders()

            val activeId = store.getSetting(SettingKeys.ACTIVE_WIKI_ID)
            val restoredActiveWiki =
                (_presetWikis.value + _customWikis.value).firstOrNull { it.id == activeId } ?: PresetWikis.default
            _activeWiki.value = restoredActiveWiki
            refreshWikiMetadata(restoredActiveWiki)

            store.getSetting(SettingKeys.THEME_MODE)?.let { raw ->
                ThemeMode.entries.firstOrNull { it.name == raw }?.let { _themeMode.value = it }
            }
            store.getSetting(SettingKeys.DYNAMIC_COLOR)?.let { _dynamicColor.value = it.toBoolean() }
            store.getSetting(SettingKeys.TEXT_SCALE)?.let { it.toFloatOrNull()?.let { f -> _textScale.value = f } }
            store.getSetting(SettingKeys.SHOW_IMAGES)?.let { _showImages.value = it.toBoolean() }
            store.getSetting(SettingKeys.OPEN_LINKS_EXTERNALLY)?.let { _openLinksExternally.value = it.toBoolean() }
            store.getSetting(SettingKeys.CONFIRM_EXTERNAL_NAVIGATION)?.let { _confirmExternalNavigation.value = it.toBoolean() }
            store.getSetting(SettingKeys.DISABLE_SAFE_MODE)?.let { _disableSafeMode.value = it.toBoolean() }
            store.getSetting(SettingKeys.OPEN_BLANK_IN_NEW_TAB)?.let { _openBlankInNewTab.value = it.toBoolean() }

            _savedPages.value = store.savedPages()
            _history.value = store.history()
            _offlineKeys.value = store.offlineArticleKeys()
            _offlineArticles.value = store.offlineArticles()
        }
    }

    /**
     * Wiki ids already revalidated against live siteinfo this app
     * session. See [refreshWikiMetadata] and [refreshFaviconOnly], which
     * share this lock and this set, since checking or updating either
     * one's dedup state has to stay consistent with the other, as a full
     * refresh already covers favicon and should not be redundantly redone.
     * This is reachable both from the UI thread, for example
     * setActiveWiki and the wiki picker's row and dialog effects called
     * directly from Compose or ViewModel code, and from appScope's own
     * Dispatchers.Default coroutines, for example the startup
     * revalidation above. So the check and add step is guarded by a mutex
     * rather than relying on any single thread assumption.
     */
    private val revalidationMutex = Mutex()
    private val revalidatedThisSession = mutableSetOf<String>()
    private val faviconOnlyRevalidatedThisSession = mutableSetOf<String>()

    fun setActiveWiki(site: WikiSite) {
        _activeWiki.value = site
        appScope.launch {
            store.setSetting(SettingKeys.ACTIVE_WIKI_ID, site.id)
            if (site.isCustom && _customWikis.value.none { it.id == site.id }) {
                addCustomWiki(site)
            }
        }
        appScope.launch { refreshWikiMetadata(site) }
    }

    /**
     * A best effort, silent refresh of a wiki's site-derived metadata,
     * meaning script path, article path prefix, favicon, available and
     * default skin, and main page title, against its live siteinfo. See
     * [WikiMetadataRefresher] for why this is needed at all, and for why
     * the person's own skin choice is never overwritten by it. This runs
     * at most once per wiki per app session. It is a suspend function
     * rather than fire and forget, so callers that need to know when it
     * is actually done can wait for it, for example the skin picker in
     * WikiPickerScreen, which should not show a stale or fallback skin
     * list while this is in flight. Internal callers that don't care,
     * like setActiveWiki and the startup restoration in [init], just wrap
     * the call in their own appScope.launch. Any failure, for example
     * being offline or the site being down, just leaves the cached values
     * in place rather than surfacing an error, since this is a background
     * freshness check and not something the person explicitly asked for.
     */
    suspend fun refreshWikiMetadata(site: WikiSite) {
        val shouldRefresh = revalidationMutex.withLock { revalidatedThisSession.add(site.id) }
        if (!shouldRefresh) return
        val updated = metadataRefresher.refresh(site) ?: return
        if (updated == site) return

        if (updated.isCustom) {
            _customWikis.update { list -> list.map { if (it.id == updated.id) updated else it } }
        } else {
            _presetWikis.update { list -> list.map { if (it.id == updated.id) updated else it } }
        }
        store.upsertWiki(updated)
        // Only replace the active wiki if the person hasn't already
        // switched away while this was in flight.
        if (_activeWiki.value.id == updated.id) _activeWiki.value = updated
    }

    /**
     * A lightweight, favicon-only sibling of [refreshWikiMetadata]. See
     * WikiMetadataRefresher.refreshFavicon's comment for why this is
     * scoped down. It is meant for display contexts where the full
     * refresh would be overkill, specifically the wiki picker's row
     * favicon, triggered per row as it scrolls into view, see
     * WikiPickerScreen, rather than for the whole list up front, since
     * even the lighter favicon-only request would still mean hundreds of
     * concurrent requests for a long wiki list if fired all at once.
     * This is deduped separately from the full refresh, under the same
     * lock, but is skipped entirely if the full refresh has already run
     * for this wiki this session, since that already resolved the
     * favicon as part of its own work and this would just be redundant.
     */
    suspend fun refreshFaviconOnly(site: WikiSite) {
        val shouldRefresh = revalidationMutex.withLock {
            if (site.id in revalidatedThisSession) return@withLock false
            faviconOnlyRevalidatedThisSession.add(site.id)
        }
        if (!shouldRefresh) return
        val faviconUrl = metadataRefresher.refreshFavicon(site) ?: return
        if (faviconUrl == site.discoveredFaviconUrl) return

        val updated = site.copy(discoveredFaviconUrl = faviconUrl)
        if (updated.isCustom) {
            _customWikis.update { list -> list.map { if (it.id == updated.id) updated else it } }
        } else {
            _presetWikis.update { list -> list.map { if (it.id == updated.id) updated else it } }
        }
        store.upsertWiki(updated)
        if (_activeWiki.value.id == updated.id) _activeWiki.value = updated
    }

    /**
     * Updates just a wiki's cached main page title, without touching
     * anything else [refreshWikiMetadata] owns. Used by FeedViewModel's
     * own lighter, opportunistic fetch for when the full metadata refresh
     * hasn't resolved a main page title yet, for example after an earlier
     * refresh attempt failed while offline. This takes [wikiId] rather
     * than a WikiSite so it applies uniformly to presets and custom wikis
     * alike, the same as [setWikiSkin].
     */
    fun updateMainPageTitle(wikiId: String, title: String) {
        val updated = (_presetWikis.value + _customWikis.value).firstOrNull { it.id == wikiId }
            ?.copy(mainPageTitle = title)
            ?: return
        if (updated.isCustom) {
            _customWikis.update { list -> list.map { if (it.id == wikiId) updated else it } }
        } else {
            _presetWikis.update { list -> list.map { if (it.id == wikiId) updated else it } }
        }
        if (_activeWiki.value.id == wikiId) _activeWiki.value = updated
        appScope.launch { store.upsertWiki(updated) }
    }

    /**
     * Changes a wiki's rendering skin. This is the one site-derived
     * field that is deliberately not auto detected, see
     * WikiMetadataRefresher's class comment for why, so this is the only
     * way it changes short of re-adding a custom wiki. This works for
     * presets and custom wikis alike, and finds whichever list actually
     * holds [wikiId] rather than requiring the caller to know which. It
     * does nothing if [wikiId] isn't in either list.
     */
    fun setWikiSkin(wikiId: String, skin: String) {
        val updated = (_presetWikis.value + _customWikis.value).firstOrNull { it.id == wikiId }
            ?.copy(skin = skin, skinIsUserSet = true)
            ?: return
        if (updated.isCustom) {
            _customWikis.update { list -> list.map { if (it.id == wikiId) updated else it } }
        } else {
            _presetWikis.update { list -> list.map { if (it.id == wikiId) updated else it } }
        }
        if (_activeWiki.value.id == wikiId) _activeWiki.value = updated
        appScope.launch { store.upsertWiki(updated) }
    }

    /**
     * Overrides the app-wide "Disable safe mode" setting on for just
     * this one wiki. See WikiSite.disableSafeMode, and
     * ArticleHostScreen's effectiveDisableSafeMode for where the two
     * are combined.
     */
    fun setWikiDisableSafeMode(wikiId: String, disableSafeMode: Boolean) {
        val updated = (_presetWikis.value + _customWikis.value).firstOrNull { it.id == wikiId }
            ?.copy(disableSafeMode = disableSafeMode)
            ?: return
        if (updated.isCustom) {
            _customWikis.update { list -> list.map { if (it.id == wikiId) updated else it } }
        } else {
            _presetWikis.update { list -> list.map { if (it.id == wikiId) updated else it } }
        }
        if (_activeWiki.value.id == wikiId) _activeWiki.value = updated
        appScope.launch { store.upsertWiki(updated) }
    }

    fun addCustomWiki(site: WikiSite) {
        _customWikis.update { list -> list.filterNot { it.id == site.id } + site }
        appScope.launch { store.upsertWiki(site) }
    }

    fun removeCustomWiki(site: WikiSite) {
        _customWikis.update { list -> list.filterNot { it.id == site.id } }
        appScope.launch { store.removeWiki(site.id) }
        if (_activeWiki.value.id == site.id) {
            // This uses the live, revalidated preset row if we have one,
            // not the raw PresetWikis.default. Falling back to that would
            // silently roll the active wiki's favicon, articlePathPrefix,
            // and skin back to their shipped defaults even after they had
            // been auto updated or edited by the user.
            val fallback = _presetWikis.value.firstOrNull { it.id == PresetWikis.default.id } ?: PresetWikis.default
            setActiveWiki(fallback)
        }
    }

    /**
     * Creates a new folder for the person's own custom wikis and
     * returns it so the caller can immediately move a wiki into it, for
     * example right after the person types a name in WikiPickerScreen's
     * "New folder" dialog. The id is derived from the name plus a short
     * random suffix, only to keep it readable in logs and storage. It
     * is never shown in the UI and never needs to be typed back in, so
     * a collision would just mean two folders happen to share a
     * suffix, not a lookup failure.
     */
    fun createFolder(name: String): WikiFolder {
        val slug = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
        val folder = WikiFolder(id = "folder-custom-$slug-${Random.nextInt(100000, 999999)}", name = name, isCustom = true)
        _customFolders.update { it + folder }
        appScope.launch { store.upsertFolder(folder, _customFolders.value.size - 1) }
        return folder
    }

    fun renameFolder(folderId: String, newName: String) {
        val updated = _customFolders.value.firstOrNull { it.id == folderId }?.copy(name = newName) ?: return
        val sortOrder = _customFolders.value.indexOfFirst { it.id == folderId }
        _customFolders.update { list -> list.map { if (it.id == folderId) updated else it } }
        appScope.launch { store.upsertFolder(updated, sortOrder) }
    }

    /**
     * Deletes a custom folder. Wikis that were in it are not deleted
     * along with it, they just fall back to ungrouped, the same place a
     * newly added custom wiki starts out.
     */
    fun deleteFolder(folderId: String) {
        _customFolders.update { list -> list.filterNot { it.id == folderId } }
        val orphaned = _customWikis.value.filter { it.folderId == folderId }.map { it.copy(folderId = null) }
        _customWikis.update { list -> list.map { wiki -> orphaned.firstOrNull { it.id == wiki.id } ?: wiki } }
        appScope.launch {
            store.removeFolder(folderId)
            orphaned.forEach { store.upsertWiki(it) }
        }
    }

    /**
     * Persists a new order for the person's custom folders after a drag
     * reorder in WikiPickerScreen. [orderedIds] is expected to already
     * be the full set of custom folder ids in their new order. Any id
     * that no longer matches a real folder, for example one deleted in
     * another session, is just skipped rather than treated as an error.
     */
    fun reorderFolders(orderedIds: List<String>) {
        val byId = _customFolders.value.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] }
        _customFolders.value = reordered
        appScope.launch { reordered.forEachIndexed { index, folder -> store.upsertFolder(folder, index) } }
    }

    /**
     * Moves a custom wiki into [folderId], or back out to ungrouped
     * when [folderId] is null. Presets are not movable this way. Their
     * folder is fixed at [PresetFolders] and set once, in
     * [PresetWikis], since they are not the person's own wikis to
     * reorganize.
     */
    fun moveWikiToFolder(wikiId: String, folderId: String?) {
        val updated = _customWikis.value.firstOrNull { it.id == wikiId }?.copy(folderId = folderId) ?: return
        _customWikis.update { list -> list.map { if (it.id == wikiId) updated else it } }
        if (_activeWiki.value.id == wikiId) _activeWiki.value = updated
        appScope.launch { store.upsertWiki(updated) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        appScope.launch { store.setSetting(SettingKeys.THEME_MODE, mode.name) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        appScope.launch { store.setSetting(SettingKeys.DYNAMIC_COLOR, enabled.toString()) }
    }

    fun setTextScale(scale: Float) {
        _textScale.value = scale
        appScope.launch { store.setSetting(SettingKeys.TEXT_SCALE, scale.toString()) }
    }

    fun setShowImages(enabled: Boolean) {
        _showImages.value = enabled
        appScope.launch { store.setSetting(SettingKeys.SHOW_IMAGES, enabled.toString()) }
    }

    fun setOpenLinksExternally(enabled: Boolean) {
        _openLinksExternally.value = enabled
        appScope.launch { store.setSetting(SettingKeys.OPEN_LINKS_EXTERNALLY, enabled.toString()) }
    }

    fun setConfirmExternalNavigation(enabled: Boolean) {
        _confirmExternalNavigation.value = enabled
        appScope.launch { store.setSetting(SettingKeys.CONFIRM_EXTERNAL_NAVIGATION, enabled.toString()) }
    }

    fun setDisableSafeMode(enabled: Boolean) {
        _disableSafeMode.value = enabled
        appScope.launch { store.setSetting(SettingKeys.DISABLE_SAFE_MODE, enabled.toString()) }
    }

    fun setOpenBlankInNewTab(enabled: Boolean) {
        _openBlankInNewTab.value = enabled
        appScope.launch { store.setSetting(SettingKeys.OPEN_BLANK_IN_NEW_TAB, enabled.toString()) }
    }

    fun allWikisNow(): List<WikiSite> = _presetWikis.value + _customWikis.value

    fun isSaved(wikiId: String, title: String): Boolean =
        _savedPages.value.any { it.wikiId == wikiId && it.title == title }

    fun toggleSaved(page: SavedPage) {
        val exists = _savedPages.value.any { it.wikiId == page.wikiId && it.title == page.title }
        _savedPages.update { list ->
            if (exists) list.filterNot { it.wikiId == page.wikiId && it.title == page.title } else listOf(page) + list
        }
        appScope.launch { store.toggleSaved(page) }
    }

    fun recordVisit(page: SavedPage) {
        _history.update { list ->
            (listOf(page) + list.filterNot { it.wikiId == page.wikiId && it.title == page.title }).take(200)
        }
        appScope.launch { store.recordVisit(page) }
    }

    fun clearHistory() {
        _history.value = emptyList()
        appScope.launch { store.clearHistory() }
    }

    fun isOfflineSaved(wikiId: String, title: String): Boolean = "$wikiId|$title" in _offlineKeys.value

    /**
     * Stores HTML for offline reading that has already been fetched and
     * wrapped, see buildOfflineHtmlDocument. Fetching the HTML itself,
     * through action=parse, is a network concern handled by the caller,
     * ArticleScreen, through MediaWikiApi. This repository only owns the
     * persistence side of it.
     */
    fun saveOfflineArticle(page: SavedPage, html: String) {
        _offlineKeys.update { it + "${page.wikiId}|${page.title}" }
        _offlineArticles.update { list -> listOf(page) + list.filterNot { it.wikiId == page.wikiId && it.title == page.title } }
        appScope.launch { store.saveOfflineArticle(page, html) }
    }

    fun removeOfflineArticle(wikiId: String, title: String) {
        _offlineKeys.update { it - "$wikiId|$title" }
        _offlineArticles.update { list -> list.filterNot { it.wikiId == wikiId && it.title == title } }
        appScope.launch { store.removeOfflineArticle(wikiId, title) }
    }

    suspend fun getOfflineArticleHtml(wikiId: String, title: String): String? =
        store.getOfflineArticleHtml(wikiId, title)
}
