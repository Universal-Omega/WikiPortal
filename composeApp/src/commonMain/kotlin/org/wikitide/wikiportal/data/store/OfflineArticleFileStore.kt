package org.wikitide.wikiportal.data.store

interface OfflineArticleFileStore {
    suspend fun write(fileName: String, content: String)
    suspend fun read(fileName: String): String?
    suspend fun delete(fileName: String)
}

/**
 * A deterministic, filesystem-safe name for wikiId and title's saved
 * file. FNV-1a, not a cryptographic hash: small, dependency-free, and
 * plenty collision-resistant for a number of saved articles any real
 * person is ever going to have.
 */
fun offlineArticleFileName(wikiId: String, title: String): String {
    var hash = -3750763034362895579L // FNV-1a 64-bit offset basis
    for (byte in "$wikiId|$title".encodeToByteArray()) {
        hash = hash xor byte.toLong()
        hash *= 1099511628211L // FNV-1a 64-bit prime
    }
    return hash.toULong().toString(16) + ".html"
}
