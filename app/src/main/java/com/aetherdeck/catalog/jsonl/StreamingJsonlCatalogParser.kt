package com.aetherdeck.catalog.jsonl

import com.aetherdeck.core.database.CatalogEntryEntity
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import com.aetherdeck.library.grouping.GameGroupingEngine
import com.aetherdeck.library.normalizer.TitleNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

object StreamingJsonlCatalogParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Streams a JSONL input stream line-by-line without loading the entire file into memory.
     * Emits batches of CatalogEntryEntity of size [batchSize] for efficient Room insertion.
     */
    suspend fun parseStreamInBatches(
        inputStream: InputStream,
        defaultSystemId: String,
        batchSize: Int = 250,
        onBatch: suspend (List<CatalogEntryEntity>, Int) -> Unit
    ): Int {
        var totalProcessed = 0
        val buffer = ArrayList<CatalogEntryEntity>(batchSize)

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 32 * 1024).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && trimmed.startsWith("{")) {
                    parseLine(trimmed, defaultSystemId)?.let { entry ->
                        buffer.add(entry)
                        totalProcessed++
                        if (buffer.size >= batchSize) {
                            onBatch(buffer.toList(), totalProcessed)
                            buffer.clear()
                        }
                    }
                }
                line = reader.readLine()
            }
        }

        if (buffer.isNotEmpty()) {
            onBatch(buffer.toList(), totalProcessed)
            buffer.clear()
        }

        AetherLogger.info(
            LogCategory.CATALOG,
            "Finished streaming JSONL catalog for system=$defaultSystemId ($totalProcessed entries)"
        )
        return totalProcessed
    }

    fun parseLine(rawJsonLine: String, defaultSystemId: String): CatalogEntryEntity? {
        return try {
            val root = json.parseToJsonElement(rawJsonLine).jsonObject
            val type = root["type"]?.jsonPrimitive?.contentOrNull ?: "file"
            if (type.equals("dir", ignoreCase = true) || type.equals("directory", ignoreCase = true)) {
                return null
            }

            val p = root["p"]?.jsonPrimitive?.contentOrNull ?: ""
            val n = root["n"]?.jsonPrimitive?.contentOrNull
                ?: p.substringAfterLast('/').substringBeforeLast('.')
            if (n.isBlank() && p.isBlank()) return null

            val ext = (root["ext"]?.jsonPrimitive?.contentOrNull
                ?: p.substringAfterLast('.', ""))
                .removePrefix(".")
                .lowercase(Locale.ROOT)

            val parent = root["parent"]?.jsonPrimitive?.contentOrNull ?: ""
            val systemId = defaultSystemId.ifBlank {
                parent.substringAfterLast('/').lowercase(Locale.ROOT).ifBlank { "unknown" }
            }

            // Parse urls[].u as external reference only (not an auto-downloader)
            var firstExternalUrl: String? = null
            val urlsElement = root["urls"]
            if (urlsElement is JsonArray && urlsElement.isNotEmpty()) {
                val firstUrlObj = urlsElement.first()
                if (firstUrlObj is JsonObject) {
                    firstExternalUrl = firstUrlObj["u"]?.jsonPrimitive?.contentOrNull
                } else {
                    firstExternalUrl = firstUrlObj.jsonPrimitive.contentOrNull
                }
            }

            val meta = root["meta"] as? JsonObject
            val metaName = meta?.get("name")?.jsonPrimitive?.contentOrNull
            val metaDesc = meta?.get("desc")?.jsonPrimitive?.contentOrNull
            val metaImage = meta?.get("image")?.jsonPrimitive?.contentOrNull
            val metaThumbnail = meta?.get("thumbnail")?.jsonPrimitive?.contentOrNull
            val metaMarquee = meta?.get("marquee")?.jsonPrimitive?.contentOrNull
            val metaVideo = meta?.get("video")?.jsonPrimitive?.contentOrNull
            val metaFanart = meta?.get("fanart")?.jsonPrimitive?.contentOrNull
            val metaBoxback = meta?.get("boxback")?.jsonPrimitive?.contentOrNull
            val metaManual = meta?.get("manual")?.jsonPrimitive?.contentOrNull
            val metaRating = meta?.get("rating")?.jsonPrimitive?.floatOrNull
            val rawReleaseDate = meta?.get("releasedate")?.jsonPrimitive?.contentOrNull
            val releaseYear = parseReleaseYear(rawReleaseDate)
            val metaDeveloper = meta?.get("developer")?.jsonPrimitive?.contentOrNull
            val metaPublisher = meta?.get("publisher")?.jsonPrimitive?.contentOrNull
            val metaGenre = meta?.get("genre")?.jsonPrimitive?.contentOrNull
            val metaPlayers = meta?.get("players")?.jsonPrimitive?.contentOrNull
            val metaRegion = meta?.get("region")?.jsonPrimitive?.contentOrNull
            val metaMd5 = meta?.get("md5")?.jsonPrimitive?.contentOrNull?.lowercase(Locale.ROOT)
            val metaLang = meta?.get("lang")?.jsonPrimitive?.contentOrNull
            val metaFamily = meta?.get("family")?.jsonPrimitive?.contentOrNull

            val bestTitle = metaName?.takeIf { it.isNotBlank() } ?: n
            val parsedTitle = TitleNormalizer.parse(bestTitle)
            val catalogIdentity = GameGroupingEngine.stableId("cat_${systemId}_${p.ifBlank { n }}_${metaMd5 ?: ""}")

            CatalogEntryEntity(
                catalogIdentity = catalogIdentity,
                systemId = systemId,
                filePath = p,
                sourceTitle = n,
                normalizedTitle = parsedTitle.normalizedTitle,
                extension = ext,
                externalReferenceUrl = firstExternalUrl,
                metaName = metaName,
                metaDesc = metaDesc,
                metaImage = metaImage,
                metaThumbnail = metaThumbnail,
                metaMarquee = metaMarquee,
                metaVideo = metaVideo,
                metaFanart = metaFanart,
                metaBoxback = metaBoxback,
                metaManual = metaManual,
                metaRating = metaRating,
                metaReleaseYear = releaseYear,
                metaDeveloper = metaDeveloper,
                metaPublisher = metaPublisher,
                metaGenre = metaGenre,
                metaPlayers = metaPlayers,
                metaRegion = metaRegion ?: parsedTitle.region.takeIf { it != "Unknown" },
                metaMd5 = metaMd5,
                metaLang = metaLang ?: parsedTitle.languages.joinToString(","),
                metaFamily = metaFamily,
                md5 = metaMd5
            )
        } catch (e: Exception) {
            AetherLogger.warn(LogCategory.CATALOG, "Skipped malformed JSONL line", e.message)
            null
        }
    }

    private fun parseReleaseYear(rawDate: String?): Int? {
        if (rawDate.isNullOrBlank()) return null
        val match = Regex("(\\d{4})").find(rawDate) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        return if (year in 1970..2035) year else null
    }
}
