package com.aetherdeck.library.grouping

import com.aetherdeck.core.database.CanonicalGameEntity
import com.aetherdeck.core.database.GameSourceEntity
import com.aetherdeck.core.database.GameVariantEntity
import com.aetherdeck.core.database.MatchReviewEntity
import com.aetherdeck.library.normalizer.ParsedRomTitle
import com.aetherdeck.library.normalizer.TitleNormalizer
import java.security.MessageDigest
import java.util.Locale

data class CandidateRomImport(
    val source: GameSourceEntity,
    val parsed: ParsedRomTitle,
    val md5: String? = null,
    val externalCatalogId: String? = null,
    val catalogDisplayTitle: String? = null,
    val catalogDescription: String? = null,
    val catalogDeveloper: String? = null,
    val catalogPublisher: String? = null,
    val catalogReleaseYear: Int? = null,
    val catalogGenre: String? = null,
    val catalogPlayers: String? = null,
    val catalogRating: Float? = null,
    val catalogRegion: String? = null,
    val catalogLanguages: String? = null
)

data class GroupingResult(
    val canonicalGames: List<CanonicalGameEntity>,
    val variants: List<GameVariantEntity>,
    val matchReviews: List<MatchReviewEntity>
)

object GameGroupingEngine {

    /**
     * Groups candidate ROM imports into CanonicalGames + GameVariants while preserving existing user data
     * (favorites, manual metadata, playCount, lastPlayed, emulator overrides, preferredVariantId).
     *
     * Rules enforced:
     * 1. Never merge games across different systems.
     * 2. Group regional variants (USA, Europe, Japan) of the same normalized title on the same system into ONE CanonicalGame.
     * 3. Group multi-disc entries (Disc 1, Disc 2, CD1, CD2) into ONE CanonicalGame.
     * 4. Group cross-regional titles (e.g., Resident Evil 2 and Biohazard 2) ONLY when a verified externalCatalogId matches;
     *    otherwise, create a MatchReviewEntity instead of over-merging.
     */
    fun groupGames(
        candidates: List<CandidateRomImport>,
        existingCanonicalGames: List<CanonicalGameEntity>,
        existingVariants: List<GameVariantEntity>,
        regionPriority: List<String>,
        languagePriority: List<String>
    ): GroupingResult {
        val existingById = existingCanonicalGames.associateBy { it.id }.toMutableMap()
        val existingBySystemAndNormalized = existingCanonicalGames
            .groupBy { "${it.systemId}::${it.normalizedTitle}" }
            .mapValues { it.value.first() }
            .toMutableMap()

        val existingVariantBySourceId = existingVariants.associateBy { it.sourceId }

        // Group candidates by system first (never mix systems)
        val bySystem = candidates.groupBy { it.source.systemId }

        val resultCanonical = mutableMapOf<String, CanonicalGameEntity>()
        // Keep existing canonical games so metadata/favorites/play history are never lost on rescan
        existingCanonicalGames.forEach { resultCanonical[it.id] = it }

        val resultVariants = mutableListOf<GameVariantEntity>()
        val matchReviews = mutableListOf<MatchReviewEntity>()

        for ((systemId, systemCandidates) in bySystem) {
            // Map from groupKey -> canonicalGameId
            // If candidate has verified externalCatalogId, use that to link variants; otherwise use normalizedTitle
            val externalIdToCanonicalId = mutableMapOf<String, String>()

            for (candidate in systemCandidates) {
                val effectiveNormalizedTitle = candidate.catalogDisplayTitle
                    ?.let { TitleNormalizer.normalizeForMatching(it) }
                    ?.takeIf { it.isNotEmpty() }
                    ?: candidate.parsed.normalizedTitle

                val existingVariant = existingVariantBySourceId[candidate.source.id]
                val existingCanonical = existingVariant?.let { existingById[it.canonicalGameId] }
                    ?: candidate.externalCatalogId?.let { extId ->
                        externalIdToCanonicalId[extId]?.let { resultCanonical[it] }
                    }
                    ?: existingBySystemAndNormalized["$systemId::$effectiveNormalizedTitle"]
                    ?: resultCanonical.values.firstOrNull {
                        it.systemId == systemId && it.normalizedTitle == effectiveNormalizedTitle
                    }

                val canonicalId = existingCanonical?.id
                    ?: stableId("cg_${systemId}_$effectiveNormalizedTitle")

                candidate.externalCatalogId?.let { extId ->
                    externalIdToCanonicalId[extId] = canonicalId
                }

                val displayTitle = when {
                    existingCanonical?.userEditedMetadata == true -> existingCanonical.displayTitle
                    !candidate.catalogDisplayTitle.isNullOrBlank() -> TitleNormalizer.parse(candidate.catalogDisplayTitle).displayTitle
                    else -> existingCanonical?.displayTitle ?: candidate.parsed.displayTitle
                }

                val multiDiscGroupId = if (candidate.parsed.discNumber != null) {
                    "md_${canonicalId}"
                } else {
                    existingCanonical?.multiDiscGroupId
                }

                val canonicalEntity = if (existingCanonical != null) {
                    if (existingCanonical.userEditedMetadata) {
                        existingCanonical.copy(
                            multiDiscGroupId = multiDiscGroupId ?: existingCanonical.multiDiscGroupId
                        )
                    } else {
                        existingCanonical.copy(
                            displayTitle = displayTitle,
                            description = candidate.catalogDescription?.takeIf { it.isNotBlank() } ?: existingCanonical.description,
                            developer = candidate.catalogDeveloper?.takeIf { it.isNotBlank() } ?: existingCanonical.developer,
                            publisher = candidate.catalogPublisher?.takeIf { it.isNotBlank() } ?: existingCanonical.publisher,
                            releaseYear = candidate.catalogReleaseYear ?: existingCanonical.releaseYear,
                            genre = candidate.catalogGenre?.takeIf { it.isNotBlank() } ?: existingCanonical.genre,
                            players = candidate.catalogPlayers?.takeIf { it.isNotBlank() } ?: existingCanonical.players,
                            rating = candidate.catalogRating ?: existingCanonical.rating,
                            multiDiscGroupId = multiDiscGroupId ?: existingCanonical.multiDiscGroupId
                        )
                    }
                } else {
                    CanonicalGameEntity(
                        id = canonicalId,
                        displayTitle = displayTitle,
                        normalizedTitle = effectiveNormalizedTitle,
                        sortTitle = TitleNormalizer.createSortTitle(displayTitle),
                        systemId = systemId,
                        description = candidate.catalogDescription ?: "",
                        developer = candidate.catalogDeveloper ?: "",
                        publisher = candidate.catalogPublisher ?: "",
                        releaseYear = candidate.catalogReleaseYear,
                        genre = candidate.catalogGenre ?: "",
                        players = candidate.catalogPlayers ?: "1",
                        rating = candidate.catalogRating,
                        multiDiscGroupId = multiDiscGroupId
                    )
                }

                resultCanonical[canonicalId] = canonicalEntity
                existingBySystemAndNormalized["$systemId::$effectiveNormalizedTitle"] = canonicalEntity

                val variantId = existingVariant?.id ?: stableId("gv_${candidate.source.id}")
                val region = candidate.catalogRegion?.takeIf { it.isNotBlank() } ?: candidate.parsed.region
                val languages = candidate.catalogLanguages?.takeIf { it.isNotBlank() }
                    ?: candidate.parsed.languages.joinToString(",")

                val variantEntity = GameVariantEntity(
                    id = variantId,
                    canonicalGameId = canonicalId,
                    originalTitle = candidate.source.sourceFileName.substringBeforeLast('.'),
                    normalizedTitle = candidate.parsed.normalizedTitle,
                    systemId = systemId,
                    region = region,
                    languages = languages,
                    revision = candidate.parsed.revision,
                    version = candidate.parsed.version,
                    discNumber = candidate.parsed.discNumber,
                    discTotal = candidate.parsed.discTotal,
                    fileExtension = candidate.source.extension,
                    md5 = candidate.md5 ?: existingVariant?.md5,
                    sha1 = existingVariant?.sha1,
                    crc32 = existingVariant?.crc32,
                    sourceId = candidate.source.id,
                    preferred = existingVariant?.preferred ?: false,
                    available = candidate.source.available,
                    emulatorOverrideId = existingVariant?.emulatorOverrideId,
                    coreOverride = existingVariant?.coreOverride
                )
                resultVariants.add(variantEntity)
            }

            // Detect potential ambiguous matches (e.g., Biohazard vs Resident Evil without externalCatalogId)
            val systemGames = resultCanonical.values.filter { it.systemId == systemId }
            detectPotentialMatchReviews(systemId, systemGames, matchReviews)
        }

        // Select preferred variant per canonical game based on region/language priority if not manually pinned
        val variantsByGame = resultVariants.groupBy { it.canonicalGameId }
        val finalVariants = mutableListOf<GameVariantEntity>()

        for ((gameId, gameVariants) in variantsByGame) {
            val canonical = resultCanonical[gameId] ?: continue
            val bestVariant = selectPreferredVariant(
                variants = gameVariants,
                currentPreferredId = canonical.preferredVariantId,
                regionPriority = regionPriority,
                languagePriority = languagePriority
            )
            if (bestVariant != null) {
                resultCanonical[gameId] = canonical.copy(preferredVariantId = bestVariant.id)
                gameVariants.forEach { v ->
                    finalVariants.add(v.copy(preferred = (v.id == bestVariant.id)))
                }
            } else {
                finalVariants.addAll(gameVariants)
            }
        }

        return GroupingResult(
            canonicalGames = resultCanonical.values.toList(),
            variants = finalVariants,
            matchReviews = matchReviews
        )
    }

    fun selectPreferredVariant(
        variants: List<GameVariantEntity>,
        currentPreferredId: String?,
        regionPriority: List<String>,
        languagePriority: List<String>
    ): GameVariantEntity? {
        if (variants.isEmpty()) return null
        // Respect existing preferred variant if still available
        if (currentPreferredId != null) {
            val pinned = variants.firstOrNull { it.id == currentPreferredId && it.available }
            if (pinned != null) return pinned
        }

        return variants.sortedWith(
            compareByDescending<GameVariantEntity> { it.available }
                .thenBy { variant ->
                    // Prefer Disc 1 for multi-disc sets
                    variant.discNumber ?: 1
                }
                .thenBy { variant ->
                    val idx = regionPriority.indexOfFirst { it.equals(variant.region, ignoreCase = true) }
                    if (idx >= 0) idx else Int.MAX_VALUE - 1
                }
                .thenBy { variant ->
                    val variantLangs = variant.languages.split(",").map { it.trim().lowercase(Locale.ROOT) }
                    val idx = languagePriority.indexOfFirst { pref ->
                        variantLangs.any { it.startsWith(pref.lowercase(Locale.ROOT).take(2)) }
                    }
                    if (idx >= 0) idx else Int.MAX_VALUE - 1
                }
                .thenByDescending { it.revision }
                .thenByDescending { it.version }
        ).firstOrNull()
    }

    private val KNOWN_REGIONAL_ALIAS_PAIRS = listOf(
        "resident evil" to "biohazard",
        "mega man" to "rockman",
        "street fighter alpha" to "street fighter zero",
        "contra" to "probotector",
        "star fox" to "starwing",
        "bully" to "canis canem edit",
        "dark chronicle" to "dark cloud 2",
        "dragon warrior" to "dragon quest"
    )

    private fun detectPotentialMatchReviews(
        systemId: String,
        games: List<CanonicalGameEntity>,
        outReviews: MutableList<MatchReviewEntity>
    ) {
        if (games.size < 2) return
        for (i in games.indices) {
            for (j in i + 1 until games.size) {
                val a = games[i]
                val b = games[j]
                val normA = a.normalizedTitle
                val normB = b.normalizedTitle
                for ((alias1, alias2) in KNOWN_REGIONAL_ALIAS_PAIRS) {
                    val swappedA = normA.replace(alias1, alias2)
                    val swappedB = normB.replace(alias1, alias2)
                    if ((normA.contains(alias1) && swappedA == normB) ||
                        (normB.contains(alias1) && swappedB == normA)
                    ) {
                        val reviewId = stableId("mr_${systemId}_${minOf(a.id, b.id)}_${maxOf(a.id, b.id)}")
                        outReviews.add(
                            MatchReviewEntity(
                                id = reviewId,
                                systemId = systemId,
                                candidateGameIdA = a.id,
                                candidateTitleA = a.displayTitle,
                                candidateGameIdB = b.id,
                                candidateTitleB = b.displayTitle,
                                similarityScore = 0.88f,
                                reason = "Possible regional title alias ($alias1 / $alias2) without verified shared external ID."
                            )
                        )
                    }
                }
            }
        }
    }

    fun stableId(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
