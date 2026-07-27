package org.wikitide.wikiportal.util

/** Provided per platform, see each platformModule(). */
interface LogExporter {
    /**
     * Best-effort save of [content] to [fileName], wherever this
     * platform's users would expect a downloaded text file to land.
     * Returns a short, human-readable description of where it went,
     * for example "Saved to Downloads", to show back to the person.
     */
    suspend fun export(fileName: String, content: String): Result<String>
}
