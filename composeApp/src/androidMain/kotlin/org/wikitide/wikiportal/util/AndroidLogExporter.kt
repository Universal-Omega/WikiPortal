package org.wikitide.wikiportal.util

import android.content.Context

/**
 * Lets the person choose exactly where to save the export, through
 * Android's own system "Save As" picker, rather than writing it
 * somewhere inside this app's own private storage. That means the
 * file lands wherever they choose, Downloads, a cloud-backed folder,
 * anywhere the picker offers.
 */
class AndroidLogExporter(private val context: Context) : LogExporter {
    override suspend fun export(fileName: String, content: String): Result<String> = runCatching {
        val uri = AndroidLogExportBridge.launchSaveDialog(fileName) ?: error("Save cancelled")
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray())
        } ?: error("Couldn't open $fileName for writing")
        "Saved"
    }
}
