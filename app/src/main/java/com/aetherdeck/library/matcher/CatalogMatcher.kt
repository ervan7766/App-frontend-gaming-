package com.aetherdeck.library.matcher

import com.aetherdeck.core.database.CatalogEntryEntity
import com.aetherdeck.library.normalizer.TitleNormalizer
import java.util.Locale

enum class CatalogMatchConfidence {
    EXACT_MD5,
    EXACT_FILENAME,
    NORMALIZED_FILENAME,
    METADATA_TITLE,
    HIGH_CONFIDENCE_FUZZY,
    AMBIGUOUS_REVIEW_REQUIRED,
    NO_MATCH
}

data class CatalogMatchResult(
    val entry: CatalogEntryEntity?,
    val confidence: CatalogMatchConfidence,
    val score: Float
)

object CatalogMatcher {

    /**
     * Matches a local ROM file against catalog entries for the same system.
     * Priority:
     * 1. Exact MD5
     * 2. Exact filename
     * 3. Normalized filename
     * 4. Metadata title
     * 5. High-confidence fuzzy match (dubious fuzzy matches return AMBIGUOUS_REVIEW_REQUIRED and are NOT auto-applied).
     */
    fun matchLocalFile(
        fileName: String,
        md5: String?,
        systemCatalog: List<CatalogEntryEntity>
    ): CatalogMatchResult {
        if (systemCatalog.isEmpty()) {
            return CatalogMatchResult(null, CatalogMatchConfidence.NO_MATCH, 0f)
        }

        // 1. Exact MD5
        if (!md5.isNullOrBlank()) {
            val cleanMd5 = md5.lowercase(Locale.ROOT)
            val md5Hit = systemCatalog.firstOrNull {
                it.md5?.lowercase(Locale.ROOT) == cleanMd5 || it.metaMd5?.lowercase(Locale.ROOT) == cleanMd5
            }
            if (md5Hit != null) {
                return CatalogMatchResult(md5Hit, CatalogMatchConfidence.EXACT_MD5, 1.0f)
            }
        }

        val cleanFileName = fileName.substringAfterLast('/')
        val fileStem = cleanFileName.substringBeforeLast('.')

        // 2. Exact filename or sourceTitle
        val exactFileHit = systemCatalog.firstOrNull { entry ->
            val entryFile = entry.filePath.substringAfterLast('/')
            entryFile.equals(cleanFileName, ignoreCase = true) ||
                entry.sourceTitle.equals(cleanFileName, ignoreCase = true) ||
                entry.sourceTitle.equals(fileStem, ignoreCase = true)
        }
        if (exactFileHit != null) {
            return CatalogMatchResult(exactFileHit, CatalogMatchConfidence.EXACT_FILENAME, 0.99f)
        }

        // 3. Normalized filename
        val parsedLocal = TitleNormalizer.parse(cleanFileName)
        val normLocal = parsedLocal.normalizedTitle
        val normFileHit = systemCatalog.firstOrNull { entry ->
            TitleNormalizer.parse(entry.sourceTitle).normalizedTitle == normLocal
        }
        if (normFileHit != null) {
            return CatalogMatchResult(normFileHit, CatalogMatchConfidence.NORMALIZED_FILENAME, 0.96f)
        }

        // 4. Metadata title
        val metaHit = systemCatalog.firstOrNull { entry ->
            entry.normalizedTitle == normLocal ||
                (entry.metaName != null && TitleNormalizer.normalizeForMatching(entry.metaName) == normLocal)
        }
        if (metaHit != null) {
            return CatalogMatchResult(metaHit, CatalogMatchConfidence.METADATA_TITLE, 0.94f)
        }

        // 5. Token similarity check (avoid applying dubious fuzzy matches automatically)
        var bestCandidate: CatalogEntryEntity? = null
        var bestScore = 0f
        for (entry in systemCatalog) {
            val score = tokenDiceSimilarity(normLocal, entry.normalizedTitle)
            if (score > bestScore) {
                bestScore = score
                bestCandidate = entry
            }
        }

        return when {
            bestCandidate != null && bestScore >= 0.92f -> {
                CatalogMatchResult(bestCandidate, CatalogMatchConfidence.HIGH_CONFIDENCE_FUZZY, bestScore)
            }
            bestCandidate != null && bestScore >= 0.72f -> {
                // Dubious fuzzy match: do NOT apply automatically
                CatalogMatchResult(bestCandidate, CatalogMatchConfidence.AMBIGUOUS_REVIEW_REQUIRED, bestScore)
            }
            else -> CatalogMatchResult(null, CatalogMatchConfidence.NO_MATCH, bestScore)
        }
    }

    private fun tokenDiceSimilarity(a: String, b: String): Float {
        if (a == b) return 1.0f
        val tokensA = a.split(" ").filter { it.isNotBlank() }.toSet()
        val tokensB = b.split(" ").filter { it.isNotBlank() }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f
        // Ensure sequel numbers match if present! E.g. "resident evil 2" vs "resident evil 3" must NOT fuzzy match
        val numbersA = tokensA.filter { it.all(Char::isDigit) || isRomanNumeral(it) }
        val numbersB = tokensB.filter { it.all(Char::isDigit) || isRomanNumeral(it) }
        if (numbersA != numbersB) return 0.2f

        val intersection = tokensA.intersect(tokensB).size
        return (2.0f * intersection) / (tokensA.size + tokensB.size)
    }

    private fun isRomanNumeral(token: String): Boolean {
        return token in setOf("ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii", "xiii")
    }
}
