package org.wikitide.wikiportal.util

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import org.wikitide.wikiportal.data.store.OfflineArticleFileStore
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * Real files under the app's Documents directory, the usual
 * place for content the user explicitly created and expects to
 * persist, as opposed to Caches, which the OS is free to purge under
 * storage pressure.
 */
@OptIn(ExperimentalForeignApi::class)
class IosOfflineArticleFileStore : OfflineArticleFileStore {
    private val directory: String by lazy {
        val documentsDir = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String
            ?: NSTemporaryDirectory()
        val dir = "$documentsDir/offline-articles"
        NSFileManager.defaultManager.createDirectoryAtPath(
            dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        dir
    }

    private fun path(fileName: String) = "$directory/$fileName"

    @OptIn(BetaInteropApi::class)
    override suspend fun write(fileName: String, content: String) {
        NSString.create(
            string = content
        ).writeToFile(path(fileName), atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    override suspend fun read(fileName: String): String? =
        NSString.stringWithContentsOfFile(path(fileName), encoding = NSUTF8StringEncoding, error = null)

    override suspend fun delete(fileName: String) {
        NSFileManager.defaultManager.removeItemAtPath(path(fileName), error = null)
    }
}
