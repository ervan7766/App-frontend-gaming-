package com.aetherdeck.library.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.aetherdeck.catalog.jsonl.StreamingJsonlCatalogParser
import com.aetherdeck.core.database.AetherDao
import com.aetherdeck.core.database.CatalogEntryEntity
import com.aetherdeck.core.database.ExternalIdentityEntity
import com.aetherdeck.core.database.GameMediaEntity
import com.aetherdeck.core.database.GameSourceEntity
import com.aetherdeck.core.database.LibrarySourceEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import com.aetherdeck.core.models.MediaType
import com.aetherdeck.core.models.ProviderType
import com.aetherdeck.library.grouping.CandidateRomImport
import com.aetherdeck.library.grouping.GameGroupingEngine
import com.aetherdeck.library.matcher.CatalogMatchConfidence
import com.aetherdeck.library.matcher.CatalogMatcher
import com.aetherdeck.library.normalizer.TitleNormalizer
import com.aetherdeck.media.resolver.ArleyMediaResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

data class EsDeGameMetadata(
    val path: String,
    val name: String?,
    val desc: String?,
    val developer: String?,
    val publisher: String?,
    val releaseYear: Int?,
    val genre: String?,
    val players: String?,
    val rating: Float?
)

data class ScanSummary(
    val filesScanned: Int,
    val canonicalGamesCount: Int,
    val variantsCount: Int,
    val newGamesAdded: Int,
    val unavailableMarked: Int
)

class SafLibraryScanner(
    private val context: Context,
    private val dao: AetherDao,
    private val arleyMediaResolver: ArleyMediaResolver
) {

    private val ignoredExtensions = setOf(
        "xml", "png", "jpg", "jpeg", "webp", "gif", "mp4", "mkv", "pdf", "db", "ini", "cfg", "sav", "srm", "state", "bak"
    )

    suspend fun scanLibrarySource(
        librarySource: LibrarySourceEntity,
        regionPriority: List<String>,
        languagePriority: List<String>,
        onProgress: suspend (String, Int, Int) -> Unit = { _, _, _ -> }
    ): Result<ScanSummary> = withContext(Dispatchers.IO) {
        try {
            AetherLogger.info(LogCategory.SCAN, "Starting scan for '${librarySource.name}' (${librarySource.providerType})")
            val systems = dao.getSystemsList()
            val aliasMap = buildSystemFolderAliasMap(systems)
            val extMap = buildSystemExtensionMap(systems)

            val treeUri = Uri.parse(librarySource.uriString)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: DocumentFile.fromSingleUri(context, treeUri)

            if (rootDoc == null || !rootDoc.exists() || !rootDoc.canRead()) {
                val msg = "Folder permission lost or unreachable for ${librarySource.name}"
                AetherLogger.error(LogCategory.SCAN, msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            // If this source is a JSONL catalog file or directory containing .jsonl files
            if (librarySource.providerType == ProviderType.GENERIC_JSONL ||
                librarySource.providerType == ProviderType.ARLEY4D_CATALOG
            ) {
                val count = importJsonlFromDocument(rootDoc, systems, onProgress)
                val updatedRoot = librarySource.copy(
                    lastScannedAt = System.currentTimeMillis(),
                    gameCount = count
                )
                dao. updateLibrarySource(updatedRoot)
                return@withContext Result.success(
                    ScanSummary(
                        filesScanned = count,
                        canonicalGamesCount = dao.getAllCanonicalGamesList().size,
                        variantsCount = dao.getAllVariantsList().size,
                        newGamesAdded = count,
                        unavailableMarked = 0
                    )
                )
            }

            // Collect all files and any ES-DE gamelist.xml files recursively
            val discoveredFiles = mutableListOf<DiscoveredRomFile>()
            val esDeMetadataBySystem = mutableMapOf<String, Map<String, EsDeGameMetadata>>()

            val rootSystemHint = inferSystemFromFolderName(rootDoc.name.orEmpty(), aliasMap)
            walkDocumentTree(
                doc = rootDoc,
                relativePathPrefix = "",
                currentSystemHint = rootSystemHint,
                aliasMap = aliasMap,
                extMap = extMap,
                providerType = librarySource.providerType,
                outFiles = discoveredFiles,
                outEsDeMetadata = esDeMetadataBySystem,
                onProgress = onProgress
            )

            // Compare with existing sources for this LibrarySource to mark removed files unavailable
            val existingRootSources = dao.getGameSourcesByRoot(librarySource.id)
            val discoveredSourceIds = discoveredFiles.map { it.sourceEntity.id }.toSet()
            val missingSourceIds = existingRootSources
                .filter { !discoveredSourceIds.contains(it.id) }
                .map { it.id }

            if (missingSourceIds.isNotEmpty()) {
                dao.markSourcesAvailability(missingSourceIds, false)
                dao.markVariantsAvailabilityBySource(missingSourceIds, false)
            }

            if (discoveredFiles.isNotEmpty()) {
                dao.insertGameSources(discoveredFiles.map { it.sourceEntity })
            }

            // Build CandidateRomImports for ALL active sources across libraries so grouping is holistic
            val allSources = dao.getAllGameSources().filter { it.available }
            val catalogBySystem = mutableMapOf<String, List<CatalogEntryEntity>>()

            val candidates = mutableListOf<CandidateRomImport>()
            val newMediaToInsert = mutableListOf<GameMediaEntity>()
            val externalIdentitiesToInsert = mutableListOf<ExternalIdentityEntity>()

            for ((index, src) in allSources.withIndex()) {
                if (index % 15 == 0) {
                    onProgress(src.sourceFileName, index + 1, allSources.size)
                }
                val parsed = TitleNormalizer.parse(src.sourceFileName)
                val sysCatalog = catalogBySystem.getOrPut(src.systemId) {
                    dao.getCatalogEntriesForSystem(src.systemId)
                }
                val matchResult = CatalogMatcher.matchLocalFile(
                    fileName = src.sourceFileName,
                    md5 = null,
                    systemCatalog = sysCatalog
                )
                val matchedCatalog = if (
                    matchResult.confidence != CatalogMatchConfidence.NO_MATCH &&
                    matchResult.confidence != CatalogMatchConfidence.AMBIGUOUS_REVIEW_REQUIRED
                ) {
                    matchResult.entry
                } else {
                    null
                }

                // Also check ES-DE gamelist.xml metadata if present
                val esDeMap = esDeMetadataBySystem[src.systemId]
                val cleanFileKey = src.sourceFileName.lowercase(Locale.ROOT)
                val esDeHit = esDeMap?.get(cleanFileKey)

                candidates.add(
                    CandidateRomImport(
                        source = src,
                        parsed = parsed,
                        md5 = matchedCatalog?.md5,
                        externalCatalogId = matchedCatalog?.catalogIdentity,
                        catalogDisplayTitle = matchedCatalog?.metaName ?: esDeHit?.name,
                        catalogDescription = matchedCatalog?.metaDesc ?: esDeHit?.desc,
                        catalogDeveloper = matchedCatalog?.metaDeveloper ?: esDeHit?.developer,
                        catalogPublisher = matchedCatalog?.metaPublisher ?: esDeHit?.publisher,
                        catalogReleaseYear = matchedCatalog?.metaReleaseYear ?: esDeHit?.releaseYear,
                        catalogGenre = matchedCatalog?.metaGenre ?: esDeHit?.genre,
                        catalogPlayers = matchedCatalog?.metaPlayers ?: esDeHit?.players,
                        catalogRating = matchedCatalog?.metaRating ?: esDeHit?.rating,
                        catalogRegion = matchedCatalog?.metaRegion,
                        catalogLanguages = matchedCatalog?.metaLang
                    )
                )
            }

            val existingCanonical = dao.getAllCanonicalGamesList()
            val existingVariants = dao.getAllVariantsList()
            val beforeCount = existingCanonical.size

            val groupingResult = GameGroupingEngine.groupGames(
                candidates = candidates,
                existingCanonicalGames = existingCanonical,
                existingVariants = existingVariants,
                regionPriority = regionPriority,
                languagePriority = languagePriority
            )

            dao.insertCanonicalGames(groupingResult.canonicalGames)
            dao.insertGameVariants(groupingResult.variants)
            if (groupingResult.matchReviews.isNotEmpty()) {
                dao.insertMatchReviews(groupingResult.matchReviews)
            }

            // Link catalog media pointers & external identities for matched games
            for (game in groupingResult.canonicalGames) {
                val sysCatalog = catalogBySystem[game.systemId].orEmpty()
                val catalogHit = sysCatalog.firstOrNull { it.normalizedTitle == game.normalizedTitle }
                if (catalogHit != null) {
                    externalIdentitiesToInsert.add(
                        ExternalIdentityEntity(
                            id = GameGroupingEngine.stableId("ext_${game.id}_${catalogHit.catalogIdentity}"),
                            canonicalGameId = game.id,
                            providerName = "Arley4dCatalog",
                            catalogIdentity = catalogHit.catalogIdentity,
                            externalUrl = catalogHit.externalReferenceUrl,
                            md5 = catalogHit.md5
                        )
                    )
                    val rawImage = catalogHit.metaImage ?: catalogHit.metaThumbnail
                    if (!rawImage.isNullOrBlank()) {
                        val resolvedUrl = arleyMediaResolver.resolveMediaUrl(rawImage, game.systemId) ?: rawImage
                        newMediaToInsert.add(
                            GameMediaEntity(
                                id = GameGroupingEngine.stableId("med_${game.id}_COVER"),
                                canonicalGameId = game.id,
                                mediaType = MediaType.COVER,
                                localOrRemoteUri = resolvedUrl,
                                providerName = "Arley4dMedia",
                                isCustomUserArt = false
                            )
                        )
                    }
                    if (!catalogHit.metaFanart.isNullOrBlank()) {
                        val resolvedBg = arleyMediaResolver.resolveMediaUrl(catalogHit.metaFanart, game.systemId)
                            ?: catalogHit.metaFanart
                        newMediaToInsert.add(
                            GameMediaEntity(
                                id = GameGroupingEngine.stableId("med_${game.id}_BACKGROUND"),
                                canonicalGameId = game.id,
                                mediaType = MediaType.BACKGROUND,
                                localOrRemoteUri = resolvedBg,
                                providerName = "Arley4dMedia",
                                isCustomUserArt = false
                            )
                        )
                    }
                    if (!catalogHit.metaMarquee.isNullOrBlank()) {
                        val resolvedLogo = arleyMediaResolver.resolveMediaUrl(catalogHit.metaMarquee, game.systemId)
                            ?: catalogHit.metaMarquee
                        newMediaToInsert.add(
                            GameMediaEntity(
                                id = GameGroupingEngine.stableId("med_${game.id}_LOGO"),
                                canonicalGameId = game.id,
                                mediaType = MediaType.LOGO,
                                localOrRemoteUri = resolvedLogo,
                                providerName = "Arley4dMedia",
                                isCustomUserArt = false
                            )
                        )
                    }
                }
            }

            if (externalIdentitiesToInsert.isNotEmpty()) {
                dao.insertExternalIdentities(externalIdentitiesToInsert)
            }
            if (newMediaToInsert.isNotEmpty()) {
                // Only insert media for games that don't have custom user art for that mediaType
                val existingMedia = dao.getAllMediaList()
                val customKeys = existingMedia.filter { it.isCustomUserArt }
                    .map { "${it.canonicalGameId}::${it.mediaType}" }
                    .toSet()
                val filteredMedia = newMediaToInsert.filterNot {
                    customKeys.contains("${it.canonicalGameId}::${it.mediaType}")
                }
                dao.insertGameMedia(filteredMedia)
            }

            val updatedRoot = librarySource.copy(
                lastScannedAt = System.currentTimeMillis(),
                gameCount = discoveredFiles.size
            )
            dao.updateLibrarySource(updatedRoot)

            val summary = ScanSummary(
                filesScanned = discoveredFiles.size,
                canonicalGamesCount = groupingResult.canonicalGames.size,
                variantsCount = groupingResult.variants.size,
                newGamesAdded = (groupingResult.canonicalGames.size - beforeCount).coerceAtLeast(0),
                unavailableMarked = missingSourceIds.size
            )
            AetherLogger.info(
                LogCategory.SCAN,
                "Scan completed for '${librarySource.name}': ${summary.filesScanned} files, ${summary.canonicalGamesCount} canonical games"
            )
            Result.success(summary)
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.SCAN, "Scan failed for '${librarySource.name}'", e)
            Result.failure(e)
        }
    }

    private data class DiscoveredRomFile(
        val sourceEntity: GameSourceEntity
    )

    private suspend fun walkDocumentTree(
        doc: DocumentFile,
        relativePathPrefix: String,
        currentSystemHint: String?,
        aliasMap: Map<String, String>,
        extMap: Map<String, List<String>>,
        providerType: ProviderType,
        outFiles: MutableList<DiscoveredRomFile>,
        outEsDeMetadata: MutableMap<String, Map<String, EsDeGameMetadata>>,
        onProgress: suspend (String, Int, Int) -> Unit
    ) {
        val children = doc.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            if (name.startsWith(".")) continue

            val relPath = if (relativePathPrefix.isEmpty()) name else "$relativePathPrefix/$name"

            if (child.isDirectory) {
                val folderSys = inferSystemFromFolderName(name, aliasMap) ?: currentSystemHint
                walkDocumentTree(
                    doc = child,
                    relativePathPrefix = relPath,
                    currentSystemHint = folderSys,
                    aliasMap = aliasMap,
                    extMap = extMap,
                    providerType = providerType,
                    outFiles = outFiles,
                    outEsDeMetadata = outEsDeMetadata,
                    onProgress = onProgress
                )
            } else if (child.isFile) {
                if (name.equals("gamelist.xml", ignoreCase = true) && currentSystemHint != null) {
                    runCatching {
                        context.contentResolver.openInputStream(child.uri)?.use { stream ->
                            val parsedXml = parseEsDeGamelistXml(stream)
                            outEsDeMetadata[currentSystemHint] = parsedXml
                        }
                    }
                    continue
                }

                val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (ext.isEmpty() || ignoredExtensions.contains(ext)) continue

                val resolvedSystem = currentSystemHint
                    ?: inferSystemFromExtension(ext, extMap)
                    ?: "unknown"

                // Validate extension if not a Fake ROM folder
                val isFakeRom = providerType == ProviderType.ARLEY4D_LOCAL_FAKE_ROM ||
                    ext in setOf("arley", "fakerom", "txt")

                val validForSystem = isFakeRom || extMap.containsKey(ext)
                if (!validForSystem) continue

                val uriStr = child.uri.toString()
                val sourceId = GameGroupingEngine.stableId("src_$uriStr")
                val sourceEntity = GameSourceEntity(
                    id = sourceId,
                    librarySourceId = "", // Filled by caller or tied to root
                    sourceUri = uriStr,
                    sourceFileName = name,
                    relativePath = relPath,
                    systemId = resolvedSystem,
                    extension = ext,
                    provider = providerType,
                    fileSize = child.length(),
                    lastModified = child.lastModified(),
                    available = true
                )
                outFiles.add(DiscoveredRomFile(sourceEntity))
                if (outFiles.size % 10 == 0) {
                    onProgress(name, outFiles.size, outFiles.size)
                }
            }
        }
    }

    private suspend fun importJsonlFromDocument(
        rootDoc: DocumentFile,
        systems: List<SystemEntity>,
        onProgress: suspend (String, Int, Int) -> Unit
    ): Int {
        var total = 0
        val files = if (rootDoc.isDirectory) {
            rootDoc.listFiles().filter { it.isFile && (it.name?.endsWith(".jsonl", true) == true || it.name?.endsWith(".json", true) == true) }
        } else {
            listOf(rootDoc)
        }
        val aliasMap = buildSystemFolderAliasMap(systems)
        for (file in files) {
            val fileName = file.name ?: "catalog.jsonl"
            val stem = fileName.substringBeforeLast('.').lowercase(Locale.ROOT)
            val sysId = inferSystemFromFolderName(stem, aliasMap) ?: stem
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                total += StreamingJsonlCatalogParser.parseStreamInBatches(
                    inputStream = stream,
                    defaultSystemId = sysId,
                    batchSize = 250
                ) { batch, processed ->
                    dao.insertCatalogEntriesBatch(batch)
                    onProgress(fileName, processed, processed)
                }
            }
        }
        return total
    }

    private fun buildSystemFolderAliasMap(systems: List<SystemEntity>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (sys in systems) {
            map[sys.id.lowercase(Locale.ROOT)] = sys.id
            map[sys.shortName.lowercase(Locale.ROOT)] = sys.id
            map[sys.name.lowercase(Locale.ROOT)] = sys.id
            sys.folderAliasesCsv.split(",").map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
                .forEach { alias -> map[alias] = sys.id }
        }
        return map
    }

    private fun buildSystemExtensionMap(systems: List<SystemEntity>): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        for (sys in systems) {
            sys.extensionsCsv.split(",").map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
                .forEach { ext ->
                    map.getOrPut(ext) { mutableListOf() }.add(sys.id)
                }
        }
        return map
    }

    private fun inferSystemFromFolderName(folderName: String, aliasMap: Map<String, String>): String? {
        val clean = folderName.trim().lowercase(Locale.ROOT)
        aliasMap[clean]?.let { return it }
        val stripped = clean.replace(Regex("[^a-z0-9]"), "")
        return aliasMap[stripped]
    }

    private fun inferSystemFromExtension(ext: String, extMap: Map<String, List<String>>): String? {
        val candidates = extMap[ext] ?: return null
        return candidates.firstOrNull()
    }

    fun parseEsDeGamelistXml(inputStream: InputStream): Map<String, EsDeGameMetadata> {
        val result = mutableMapOf<String, EsDeGameMetadata>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var currentTag = ""
            var path: String? = null
            var name: String? = null
            var desc: String? = null
            var dev: String? = null
            var pub: String? = null
            var relDate: String? = null
            var genre: String? = null
            var players: String? = null
            var rating: Float? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name.lowercase(Locale.ROOT)
                        if (currentTag == "game") {
                            path = null; name = null; desc = null; dev = null; pub = null
                            relDate = null; genre = null; players = null; rating = null
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "path" -> path = text
                                "name" -> name = text
                                "desc" -> desc = text
                                "developer" -> dev = text
                                "publisher" -> pub = text
                                "releasedate" -> relDate = text
                                "genre" -> genre = text
                                "players" -> players = text
                                "rating" -> rating = text.toFloatOrNull()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name.equals("game", ignoreCase = true) && !path.isNullOrBlank()) {
                            val fileNameKey = path!!.substringAfterLast('/').lowercase(Locale.ROOT)
                            val year = relDate?.take(4)?.toIntOrNull()
                            result[fileNameKey] = EsDeGameMetadata(
                                path = path!!,
                                name = name,
                                desc = desc,
                                developer = dev,
                                publisher = pub,
                                releaseYear = year,
                                genre = genre,
                                players = players,
                                rating = rating
                            )
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            AetherLogger.warn(LogCategory.SCAN, "Failed parsing gamelist.xml", e.message)
        }
        return result
    }
}
