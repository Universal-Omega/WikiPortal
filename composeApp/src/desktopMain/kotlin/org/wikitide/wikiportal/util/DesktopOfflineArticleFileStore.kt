package org.wikitide.wikiportal.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wikitide.wikiportal.data.store.OfflineArticleFileStore
import java.io.File

/** Same ~/.wikiportal directory the desktop SQLite database file already in, see PlatformModule.desktop.kt. */
class DesktopOfflineArticleFileStore : OfflineArticleFileStore {
    private val directory: File by lazy {
        File(File(System.getProperty("user.home"), ".wikiportal"), "offline-articles").apply { mkdirs() }
    }

    override suspend fun write(fileName: String, content: String) = withContext(Dispatchers.IO) {
        runCatching { File(directory, fileName).writeText(content) }
        Unit
    }

    override suspend fun read(fileName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(directory, fileName)
            if (file.exists()) file.readText() else null
        }.getOrNull()
    }

    override suspend fun delete(fileName: String) = withContext(Dispatchers.IO) {
        runCatching { File(directory, fileName).delete() }
        Unit
    }
}
