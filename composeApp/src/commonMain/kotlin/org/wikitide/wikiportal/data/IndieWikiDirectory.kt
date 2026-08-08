package org.wikitide.wikiportal.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.wikitide.wikiportal.data.model.IndieWikiSite
import org.wikitide.wikiportal.data.model.findIndieWikiRedirect
import org.wikitide.wikiportal.data.model.toDomainOrNull
import org.wikitide.wikiportal.data.store.SettingKeys
import org.wikitide.wikiportal.data.store.WikiPortalStore
import org.wikitide.wikiportal.network.IndieWikiBuddyApi
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.nowEpochMillis

private const val TAG = "IndieWikiDirectory"

/** Refetched at most this often. Indie Wiki Buddy's own data doesn't change fast enough to justify anything shorter. */
private const val CACHE_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000

private val json = Json { ignoreUnknownKeys = true }

/**
 * Loads Indie Wiki Buddy's directory of wikis, see
 * https://github.com/KevinPayravi/indie-wiki-buddy, and keeps it
 * around for two things: suggesting an independent wiki when someone
 * tries to add one of its known origins, see AddWikiViewModel, and
 * letting them browse the directory directly, see BrowseWikisScreen.
 * The full decoded list is cached as a single setting, see
 * SettingKeys.INDIE_WIKI_CACHE, rather than its own table, since it's
 * read as one flat list either way and there's no query this app needs
 * against it that a table and SQL would actually help with.
 */
class IndieWikiDirectory(
    private val api: IndieWikiBuddyApi,
    private val store: WikiPortalStore,
    private val appScope: CoroutineScope,
) {
    private val _sites = MutableStateFlow<List<IndieWikiSite>>(emptyList())
    val sites: StateFlow<List<IndieWikiSite>> = _sites

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val loadMutex = Mutex()
    private var loadedFromCache = false

    /**
     * Reads whatever's already cached off disk on first call, a plain
     * local read so this returns quickly even offline, then kicks off
     * a background refresh if that cache is missing or older than
     * [CACHE_MAX_AGE_MILLIS], without waiting on it. Safe, and cheap,
     * to call from anywhere that's about to read [sites], for example a
     * screen's own init or right before a redirect match, since the
     * disk read only ever happens once per app session and every call
     * after that is a no-op beyond the staleness check.
     */
    suspend fun ensureLoaded() {
        loadMutex.withLock {
            if (loadedFromCache) return@withLock
            loadedFromCache = true
            val cached = store.getSetting(SettingKeys.INDIE_WIKI_CACHE)
                ?.let { raw -> runCatching { json.decodeFromString<List<IndieWikiSite>>(raw) }.getOrNull() }
            if (cached != null) _sites.value = cached
        }
        val updatedAt = store.getSetting(SettingKeys.INDIE_WIKI_CACHE_UPDATED_AT)?.toLongOrNull() ?: 0L
        if (_sites.value.isEmpty() || nowEpochMillis() - updatedAt > CACHE_MAX_AGE_MILLIS) {
            appScope.launch { refresh() }
        }
    }

    /** Forces a network refresh regardless of cache age, for example a manual "Refresh" tap on BrowseWikisScreen. */
    suspend fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        try {
            val fetched = api.fetchAllSites().mapNotNull { (lang, dto) -> dto.toDomainOrNull(lang) }
            if (fetched.isNotEmpty()) {
                _sites.value = fetched
                store.setSetting(SettingKeys.INDIE_WIKI_CACHE, json.encodeToString(fetched))
                store.setSetting(SettingKeys.INDIE_WIKI_CACHE_UPDATED_AT, nowEpochMillis().toString())
            } else if (_sites.value.isEmpty()) {
                AppLog.w(TAG, "Refresh returned nothing and there's no cache to fall back on")
            }
        } finally {
            _isRefreshing.value = false
        }
    }

    /**
     * Looks for a redirect match against whatever's currently loaded.
     * Used by AddWikiViewModel before it goes ahead and adds whatever
     * was typed in. Doesn't wait on a network refresh, so a fresh
     * install with nothing cached yet, or being offline, just means no
     * suggestion this time rather than adding a wiki hanging on it. A
     * match is a nice-to-have here, not something worth blocking or
     * erroring over.
     */
    suspend fun findRedirectSuggestion(rawUrl: String): IndieWikiSite? {
        ensureLoaded()
        return findIndieWikiRedirect(rawUrl, _sites.value)
    }
}
