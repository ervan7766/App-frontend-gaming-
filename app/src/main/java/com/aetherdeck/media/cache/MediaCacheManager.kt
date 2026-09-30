package com.aetherdeck.media.cache

import android.content.Context
import coil.ImageLoader
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageBreakdown(
    val databaseBytes: Long,
    val mediaCacheBytes: Long,
    val backupsBytes: Long,
    val catalogCacheBytes: Long,
    val userMediaBytes: Long
) {
    val totalBytes: Long
        get() = databaseBytes + mediaCacheBytes + backupsBytes + catalogCacheBytes + userMediaBytes
}

class MediaCacheManager(private val context: Context) {

    val mediaCacheDir: File
        get() = File(context.cacheDir, "image_cache").apply { mkdirs() }

    val arleyPointersDir: File
        get() = File(context.cacheDir, "arley_media_pointers").apply { mkdirs() }

    val userMediaDir: File
        get() = File(context.filesDir, "custom_artwork").apply { mkdirs() }

    val backupsDir: File
        get() = File(context.filesDir, "backups").apply { mkdirs() }

    val catalogCacheDir: File
        get() = File(context.filesDir, "catalog_cache").apply { mkdirs() }

    suspend fun getStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val dbFile = context.getDatabasePath("aetherdeck.db")
        val dbWal = context.getDatabasePath("aetherdeck.db-wal")
        val dbShm = context.getDatabasePath("aetherdeck.db-shm")
        val dbSize = (if (dbFile.exists()) dbFile.length() else 0L) +
            (if (dbWal.exists()) dbWal.length() else 0L) +
            (if (dbShm.exists()) dbShm.length() else 0L)

        val coilCache = directorySize(mediaCacheDir) +
            directorySize(File(context.cacheDir, "image_manager_disk_cache")) +
            directorySize(arleyPointersDir)

        StorageBreakdown(
            databaseBytes = dbSize,
            mediaCacheBytes = coilCache,
            backupsBytes = directorySize(backupsDir),
            catalogCacheBytes = directorySize(catalogCacheDir),
            userMediaBytes = directorySize(userMediaDir)
        )
    }

    suspend fun clearMediaCache(imageLoader: ImageLoader? = null) = withContext(Dispatchers.IO) {
        try {
            imageLoader?.memoryCache?.clear()
            imageLoader?.diskCache?.clear()
            mediaCacheDir.deleteRecursively()
            mediaCacheDir.mkdirs()
            arleyPointersDir.deleteRecursively()
            arleyPointersDir.mkdirs()
            AetherLogger.info(LogCategory.MEDIA, "Cleared local media and pointer cache")
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.MEDIA, "Failed to clear media cache", e)
        }
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var result = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                result += file.length()
            }
        }
        return result
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format( java.util.Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(java.util.Locale.US, "%.2f MB", mb)
            val gb = mb / 1024.0
            return String.format(java.util.Locale.US, "%.2f GB", gb)
        }
    }
}
