package org.wikitide.wikiportal.util

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

/**
 * The system "Save As" picker (Storage Access Framework) can only be
 * launched through an ActivityResultLauncher, which has to be
 * registered ahead of time by an actual Activity, before it reaches
 * the started state, see MainActivity's launcher property. That
 * doesn't fit AndroidLogExporter's shape at all, a plain suspend
 * function with no Activity of its own, injected as a Koin singleton
 * that outlives any one Activity instance. This bridges the two:
 * MainActivity registers its launcher here once, and
 * launchSaveDialog suspends until MainActivity's own callback,
 * onDocumentCreated, reports back which Uri the person picked, or
 * null if they backed out of the dialog.
 *
 * Only one export can be in flight at a time, which is really just
 * the same constraint the picker itself already imposes by being a
 * modal dialog.
 */
object AndroidLogExportBridge {
    var launcher: ((suggestedName: String) -> Unit)? = null
    private var pendingResult: CompletableDeferred<Uri?>? = null

    suspend fun launchSaveDialog(suggestedName: String): Uri? {
        val launch = launcher ?: return null
        val deferred = CompletableDeferred<Uri?>()
        pendingResult = deferred
        launch(suggestedName)
        return deferred.await()
    }

    fun onDocumentCreated(uri: Uri?) {
        pendingResult?.complete(uri)
        pendingResult = null
    }
}
