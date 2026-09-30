package com.aetherdeck.library.normalizer

import java.text.Normalizer
import java.util.Locale

data class ParsedRomTitle(
    val rawFileName: String,
    val displayTitle: String,
    val normalizedTitle: String,
    val sortTitle: String,
    val region: String,
    val languages: List<String>,
    val revision: String,
    val version: String,
    val editionType: String, // Full, Beta, Proto, Demo
    val discNumber: Int?,
    val discTotal: Int?,
    val dumpFlags: List<String>,
    val translationTag: String?,
    val extension: String
)

object TitleNormalizer {

    private val KNOWN_REGIONS = mapOf(
        "usa" to "USA",
        "u" to "USA",
        "us" to "USA",
        "ntsc-u" to "USA",
        "europe" to "Europe",
        "eur" to "Europe",
        "e" to "Europe",
        "pal" to "Europe",
        "uk" to "Europe",
        "japan" to "Japan",
        "jpn" to "Japan",
        "jp" to "Japan",
        "j" to "Japan",
        "ntsc-j" to "Japan",
        "world" to "World",
        "w" to "World",
        "spain" to "Spain",
        "esp" to "Spain",
        "france" to "France",
        "fra" to "France",
        "germany" to "Germany",
        "ger" to "Germany",
        "deu" to "Germany",
        "italy" to "Italy",
        "ita" to "Italy",
        "korea" to "Korea",
        "kor" to "Korea",
        "brazil" to "Brazil",
        "bra" to "Brazil",
        "australia" to "Australia",
        "aus" to "Australia",
        "asia" to "Asia"
    )

    private val KNOWN_LANG_CODES = setOf(
        "en", "es", "fr", "de", "it", "ja", "pt", "nl", "sv", "no", "da", "fi", "ko", "zh", "ru", "pl"
    )

    private val DISC_REGEX = Regex(
        pattern = "(?i)\\b(?:disc|disk|cd|gd|umd)\\s*0*(\\d+)(?:\\s*(?:of|/)\\s*0*(\\d+))?\\b"
    )

    private val REV_REGEX = Regex("(?i)^rev(?:ision)?\\s*([0-9a-z.]+)$")
    private val VER_REGEX = Regex("(?i)^v(?:er(?:sion)?)?\\s*(\\d+(?:\\.\\d+)*[a-z]?)$")
    private val EDITION_REGEX = Regex("(?i)^(beta|proto|prototype|demo|sample|kiosk|promo)(?:\\s*\\d*)?$")
    private val TRANSLATION_REGEX = Regex("(?i)^t[+-]?([a-z]{2,3})(?:[\\s_-].*)?$")
    private val DUMP_FLAG_REGEX = Regex("(?i)^([bahfopt!]\\d*|!)$")

    fun parse(fileNameOrTitle: String): ParsedRomTitle {
        val trimmed = fileNameOrTitle.trim()
        val lastDot = trimmed.lastIndexOf('.')
        val hasKnownExt = lastDot > 0 && lastDot >= trimmed.length - 7 &&
            !trimmed.substring(lastDot + 1).contains(' ') &&
            !trimmed.substring(lastDot + 1).contains(')')

        val extension = if (hasKnownExt) trimmed.substring(lastDot + 1).lowercase(Locale.ROOT) else ""
        var working = if (hasKnownExt) trimmed.substring(0, lastDot) else trimmed

        val detectedRegions = mutableListOf<String>()
        val detectedLanguages = mutableSetOf<String>()
        var revision = ""
        var version = ""
        var editionType = "Release"
        var discNumber: Int? = null
        var discTotal: Int? = null
        val dumpFlags = mutableListOf<String>()
        var translationTag: String? = null

        // Extract bracketed tags [...]
        val bracketRegex = Regex("\\[([^\\]]+)]")
        bracketRegex.findAll(working).forEach { match ->
            val content = match.groupValues[1].trim()
            val transMatch = TRANSLATION_REGEX.matchEntire(content)
            if (transMatch != null) {
                val langCode = transMatch.groupValues[1].replaceFirstChar { it.uppercase() }
                translationTag = "T-$langCode"
                detectedLanguages.add(langCode)
            } else if (DUMP_FLAG_REGEX.matches(content)) {
                dumpFlags.add("[$content]")
            } else {
                // Check if region inside brackets
                val lower = content.lowercase(Locale.ROOT)
                KNOWN_REGIONS[lower]?.let { reg ->
                    if (!detectedRegions.contains(reg)) detectedRegions.add(reg)
                }
            }
        }
        working = working.replace(bracketRegex, " ")

        // Extract parenthesized tags (...)
        val parenRegex = Regex("\\(([^)]+)\\)")
        parenRegex.findAll(working).forEach { match ->
            val content = match.groupValues[1].trim()
            // Check disc first
            val discMatch = DISC_REGEX.find(content)
            if (discMatch != null) {
                discNumber = discMatch.groupValues[1].toIntOrNull()
                discTotal = discMatch.groupValues.getOrNull(2)?.toIntOrNull()
                return@forEach
            }

            // Split comma-separated tokens inside parens, e.g. (USA, Europe) or (En,Fr,De,Es,It)
            val parts = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val allLangs = parts.isNotEmpty() && parts.all { KNOWN_LANG_CODES.contains(it.lowercase(Locale.ROOT)) }
            if (allLangs) {
                parts.forEach { lang ->
                    detectedLanguages.add(lang.lowercase(Locale.ROOT).replaceFirstChar { c -> c.uppercase() })
                }
                return@forEach
            }

            var matchedAny = false
            for (part in parts) {
                val lower = part.lowercase(Locale.ROOT)
                val regionHit = KNOWN_REGIONS[lower]
                if (regionHit != null) {
                    if (!detectedRegions.contains(regionHit)) detectedRegions.add(regionHit)
                    matchedAny = true
                    continue
                }
                if (KNOWN_LANG_CODES.contains(lower)) {
                    detectedLanguages.add(lower.replaceFirstChar { c -> c.uppercase() })
                    matchedAny = true
                    continue
                }
                val revHit = REV_REGEX.matchEntire(part)
                if (revHit != null) {
                    revision = "Rev ${revHit.groupValues[1].uppercase(Locale.ROOT)}"
                    matchedAny = true
                    continue
                }
                val verHit = VER_REGEX.matchEntire(part)
                if (verHit != null) {
                    version = "v${verHit.groupValues[1]}"
                    matchedAny = true
                    continue
                }
                val edHit = EDITION_REGEX.matchEntire(part)
                if (edHit != null) {
                    editionType = edHit.groupValues[1].replaceFirstChar { c -> c.uppercase() }
                    matchedAny = true
                    continue
                }
            }
        }
        working = working.replace(parenRegex, " ")

        // Check if standalone Disc marker or v1.x / Rev X remains at the end of the title
        val trailingDiscMatch = DISC_REGEX.find(working)
        if (trailingDiscMatch != null) {
            if (discNumber == null) {
                discNumber = trailingDiscMatch.groupValues[1].toIntOrNull()
                discTotal = trailingDiscMatch.groupValues.getOrNull(2)?.toIntOrNull()
            }
            working = working.removeRange(trailingDiscMatch.range)
        }

        val trailingVerRegex = Regex("(?i)\\s+v(\\d+\\.\\d+[a-z]?)\\s*$")
        trailingVerRegex.find(working)?.let { m ->
            if (version.isEmpty()) version = "v${m.groupValues[1]}"
            working = working.removeRange(m.range)
        }

        val trailingRevRegex = Regex("(?i)\\s+rev\\s*([0-9a-z]+)\\s*$")
        trailingRevRegex.find(working)?.let { m ->
            if (revision.isEmpty()) revision = "Rev ${m.groupValues[1].uppercase(Locale.ROOT)}"
            working = working.removeRange(m.range)
        }

        // Clean underscores and collapse whitespace while keeping important numbers (2, 3, VII, etc.)
        var displayTitle = working
            .replace('_', ' ')
            .replace(Regex("\\s+-\\s*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Handle ", The" at the end -> "The ..."
        if (displayTitle.endsWith(", The", ignoreCase = true)) {
            displayTitle = "The " + displayTitle.substring(0, displayTitle.length - 5).trim()
        }

        if (displayTitle.isEmpty()) {
            displayTitle = fileNameOrTitle.substringBeforeLast('.').trim()
        }

        val normalizedTitle = normalizeForMatching(displayTitle)
        val sortTitle = createSortTitle(displayTitle)

        val resolvedRegion = when {
            detectedRegions.isEmpty() -> "Unknown"
            detectedRegions.size == 1 -> detectedRegions.first()
            detectedRegions.contains("USA") && detectedRegions.contains("Europe") -> "World"
            else -> detectedRegions.first()
        }

        if (detectedLanguages.isEmpty()) {
            when (resolvedRegion) {
                "Spain" -> detectedLanguages.add("Es")
                "Japan" -> detectedLanguages.add("Ja")
                "France" -> detectedLanguages.add("Fr")
                "Germany" -> detectedLanguages.add("De")
                "Italy" -> detectedLanguages.add("It")
                else -> detectedLanguages.add("En")
            }
        }

        return ParsedRomTitle(
            rawFileName = fileNameOrTitle,
            displayTitle = displayTitle,
            normalizedTitle = normalizedTitle,
            sortTitle = sortTitle,
            region = resolvedRegion,
            languages = detectedLanguages.toList(),
            revision = revision,
            version = version,
            editionType = editionType,
            discNumber = discNumber,
            discTotal = discTotal,
            dumpFlags = dumpFlags,
            translationTag = translationTag,
            extension = extension
        )
    }

    /**
     * Normalizes a title for deterministic matching and search without stripping numbers or Roman numerals.
     * Example:
     * "Resident Evil 2" -> "resident evil 2"
     * "Final Fantasy VII" -> "final fantasy vii"
     * "Pokémon: Edición Roja" -> "pokemon edicion roja"
     */
    fun normalizeForMatching(title: String): String {
        val decomposed = Normalizer.normalize(title, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutAccents
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun createSortTitle(displayTitle: String): String {
        val cleaned = displayTitle
            .replace(Regex("^(?i)(the|a|an)\\s+"), "")
            .trim()
        return cleaned.ifEmpty { displayTitle }.lowercase(Locale.ROOT)
    }
}
