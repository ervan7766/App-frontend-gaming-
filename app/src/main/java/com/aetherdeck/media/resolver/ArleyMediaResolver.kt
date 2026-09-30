package com.aetherdeck.media.resolver

import android.content.Context
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves Arley4d media references (such as `Z:/Digistorage/Arley4dCloudMedia/cps3/images/jojoban-image.jpg`)
 * against the `Arley4d/downloaded_media` repository structure where `.txt` pointer files contain the real HTTPS URL.
 * Also caches resolved URLs in memory and on disk so repeat lookups are instant.
 */
class ArleyMediaResolver(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val resolutionCacheDir: File by lazy {
        File(context.cacheDir, "arley_media_pointers").apply { mkdirs() }
    }

    companion object {
        private const val RAW_GITHUB_BASE = "https://raw.githubusercontent.com/Arley4d/downloaded_media/main"

        /**
         * Converts an Arley4d catalog media path into candidate downloaded_media `.txt` URLs.
         * Example input:
         * `Z:/Digistorage/Arley4dCloudMedia/cps3/images/jojoban-image.jpg`
         * Candidate outputs:
         * - `https://raw.githubusercontent.com/Arley4d/downloaded_media/main/cps3/covers/jojoban-image.txt`
         * - `https://raw.githubusercontent.com/Arley4d/downloaded_media/main/cps3/images/jojoban-image.txt`
         */
        fun buildCandidatePointerUrls(rawCatalogMediaPath: String, fallbackSystemId: String): List<String> {
            val normalized = rawCatalogMediaPath.trim().replace('\\', '/')
            if (normalized.startsWith("http://", ignoreCase = true) ||
                normalized.startsWith("https://", ignoreCase = true)
            ) {
                if (!normalized.endsWith(".txt", ignoreCase = true)) {
                    return listOf(normalized)
                }
            }

            val segments = normalized.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) return emptyList()

            val fileNameWithExt = segments.last()
            val fileStem = fileNameWithExt.substringBeforeLast('.')

            // Locate system & folder after Arley4dCloudMedia or downloaded_media if present
            val cloudIdx = segments.indexOfFirst {
                it.equals("Arley4dCloudMedia", ignoreCase = true) ||
                    it.equals("downloaded_media", ignoreCase = true)
            }

            val systemSegment = when {
                cloudIdx >= 0 && cloudIdx + 1 < segments.size - 1 -> segments[cloudIdx + 1].lowercase(Locale.ROOT)
                segments.size >= 3 -> segments[segments.size - 3].lowercase(Locale.ROOT)
                else -> fallbackSystemId.lowercase(Locale.ROOT)
            }

            val folderSegment = when {
                cloudIdx >= 0 && cloudIdx + 2 < segments.size - 1 -> segments[cloudIdx + 2].lowercase(Locale.ROOT)
                segments.size >= 2 -> segments[segments.size - 2].lowercase(Locale.ROOT)
                else -> "covers"
            }

            val candidateFolders = linkedSetOf<String>()
            if (folderSegment == "images" || folderSegment == "image") {
                candidateFolders.add("covers")
                candidateFolders.add("images")
                candidateFolders.add("screenshots")
            } else {
                candidateFolders.add(folderSegment)
                candidateFolders.add("covers")
                candidateFolders.add("images")
            }

            return candidateFolders.map { folder ->
                "$RAW_GITHUB_BASE/$systemSegment/$folder/$fileStem.txt"
            }
        }
    }

    suspend fun resolveMediaUrl(rawPathOrUrl: String, systemId: String): String? = withContext(Dispatchers.IO) {
        val trimmed = rawPathOrUrl.trim()
        if (trimmed.isEmpty()) return@withContext null

        // Direct non-.txt HTTP(S) URL
        if ((trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) &&
            !trimmed.endsWith(".txt", ignoreCase = true)
        ) {
            return@withContext trimmed
        }

        val cacheKey = hashKey("$systemId::$trimmed")
        memoryCache[cacheKey]?.let { return@withContext it.ifEmpty { null } }

        val diskFile = File(resolutionCacheDir, "$cacheKey.url")
        if (diskFile.exists()) {
            val cached = runCatching { diskFile.readText().trim() }.getOrNull()
            if (!cached.isNullOrEmpty()) {
                memoryCache[cacheKey] = cached
                return@withContext cached
            }
        }

        val candidates = buildCandidatePointerUrls(trimmed, systemId)
        for (pointerUrl in candidates) {
            try {
                val request = Request.Builder().url(pointerUrl).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyText = response.body?.string()?.trim().orEmpty()
                        val firstUrlLine = bodyText.lineSequence()
                            .map { it.trim() }
                            .firstOrNull { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

                        if (!firstUrlLine.isNullOrBlank()) {
                            memoryCache[cacheKey] = firstUrlLine
                            runCatching { diskFile.writeText(firstUrlLine) }
                            AetherLogger.info(LogCategory.MEDIA, "Resolved Arley4d media pointer: $pointerUrl -> $firstUrlLine")
                            return@withContext firstUrlLine
                        }
                    }
                }
            } catch (e: Exception) {
                AetherLogger.warn(LogCategory.MEDIA, "Failed to resolve media pointer $pointerUrl", e.message)
            }
        }

        memoryCache[cacheKey] = ""
        null
    }

    fun clearPointerCache() {
        memoryCache.clear()
        resolutionCacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun hashKey(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.take(12).joinToString("") { "%02x".format(it) }
    }
}
