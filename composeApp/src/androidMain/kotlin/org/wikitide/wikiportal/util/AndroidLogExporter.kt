package org.wikitide.wikiportal.util

import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Writes the export to this app's own external files directory, no
 * storage permission needed since that location is always private to
 * this app, and also opens the system share sheet with the same text,
 * so saving a copy to Drive, emailing it, or handing it to a bug
 * report is just a tap away rather than needing a file manager that
 * exposes Android/data at all.
 */
class AndroidLogExporter(private val context: Context) : LogExporter {
    override suspend fun export(fileName: String, content: String): Result<String> = runCatching {
        val dir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        val chooser = Intent.createChooser(shareIntent, "Export logs").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)

        "Saved to ${file.absolutePath}"
    }
}
