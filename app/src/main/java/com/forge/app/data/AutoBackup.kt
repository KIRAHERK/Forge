package com.forge.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.forge.app.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Automatic backups, on top of Android keeping data across same-key updates:
 *  - files/backups/forge-backup-latest.json (always; survives updates, not uninstalls)
 *  - Downloads/Forge/forge-backup-YYYY-MM-DD.json on Android 10+ (survives uninstalls; import it from Settings)
 */
object AutoBackup {
    private const val LATEST = "forge-backup-latest.json"
    private const val KEEP = 10
    private val RELATIVE = Environment.DIRECTORY_DOWNLOADS + "/Forge/"

    fun latestFile(ctx: Context): File = File(File(ctx.filesDir, "backups").apply { mkdirs() }, LATEST)

    /** Returns true when at least the internal copy was written. */
    suspend fun run(ctx: Context, reason: String): Boolean = withContext(Dispatchers.IO) {
        Graph.init(ctx)
        val json = Graph.repo.exportJson()
        latestFile(ctx).writeText(json)
        if (Build.VERSION.SDK_INT >= 29) runCatching { writeDownloads(ctx, "forge-backup-${LocalDate.now()}.json", json) }
        Graph.settings.update { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupReason = reason) }
        true
    }

    suspend fun restoreLatest(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val f = latestFile(ctx)
        if (!f.exists()) return@withContext false
        Graph.repo.importJson(f.readText())
        true
    }

    @RequiresApi(29)
    private fun writeDownloads(ctx: Context, name: String, json: String) {
        val resolver = ctx.contentResolver
        val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val existing = resolver.query(
            collection, arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?",
            arrayOf(name, RELATIVE), null,
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE)
        }) ?: return
        resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }

        // Keep the newest few dated backups.
        resolver.query(
            collection, arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE 'forge-backup-%'",
            arrayOf(RELATIVE), "${MediaStore.Downloads.DISPLAY_NAME} DESC",
        )?.use { c ->
            var i = 0
            while (c.moveToNext()) {
                if (i++ >= KEEP) resolver.delete(ContentUris.withAppendedId(collection, c.getLong(0)), null, null)
            }
        }
    }
}
