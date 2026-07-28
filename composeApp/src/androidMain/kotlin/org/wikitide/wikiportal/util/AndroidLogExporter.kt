package org.wikitide.wikiportal.util

import android.content.Context

class AndroidLogExporter(private val context: Context) : LogExporter {
    override suspend fun export(fileName: String, content: String): Result<String> = runCatching {
        val uri = AndroidLogExportBridge.launchSaveDialog(fileName) ?: error("Save cancelled")
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray())
        } ?: error("Couldn't open $fileName for writing")
        "Saved"
    }
}
