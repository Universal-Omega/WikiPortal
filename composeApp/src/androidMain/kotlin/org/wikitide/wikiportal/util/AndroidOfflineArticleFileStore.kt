package org.wikitide.wikiportal.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wikitide.wikiportal.data.store.OfflineArticleFileStore
import java.io.File

class AndroidOfflineArticleFileStore(private val context: Context) : OfflineArticleFileStore {
    private val directory: File by lazy { File(context.filesDir, "offline-articles").apply { mkdirs() } }

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
