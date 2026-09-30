package com.example

import com.aetherdeck.catalog.jsonl.StreamingJsonlCatalogParser
import com.aetherdeck.core.database.CanonicalGameEntity
import com.aetherdeck.core.database.GameSourceEntity
import com.aetherdeck.core.models.ProviderType
import com.aetherdeck.library.grouping.CandidateRomImport
import com.aetherdeck.library.grouping.GameGroupingEngine
import com.aetherdeck.library.matcher.CatalogMatchConfidence
import com.aetherdeck.library.matcher.CatalogMatcher
import com.aetherdeck.library.normalizer.TitleNormalizer
import com.aetherdeck.media.resolver.ArleyMediaResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ExampleUnitTest {

    @Test
    fun `title normalizer preserves sequel numbers and roman numerals while extracting tags`() {
        val re2 = TitleNormalizer.parse("Resident Evil 2 (USA) (Disc 1) (Rev 1) [T-Es].bin")
        assertEquals("Resident Evil 2", re2.displayTitle)
        assertEquals("resident evil 2", re2.normalizedTitle)
        assertEquals("USA", re2.region)
        assertEquals(Integer.valueOf(1), re2.discNumber)
        assertEquals("Rev 1", re2.revision)
        assertEquals("T-Es", re2.translationTag)
        assertTrue(re2.languages.contains("Es"))

        val ff7 = TitleNormalizer.parse("Final Fantasy VII (Europe) (En,Fr,De,Es,It) (CD2).chd")
        assertEquals("Final Fantasy VII", ff7.displayTitle)
        assertEquals("final fantasy vii", ff7.normalizedTitle)
        assertEquals("Europe", ff7.region)
        assertEquals(Integer.valueOf(2), ff7.discNumber)
        assertTrue(ff7.languages.containsAll(listOf("En", "Fr", "De", "Es", "It")))
    }

    @Test
    fun `grouping engine groups Tekken 3 USA Europe Japan into one canonical game with 3 variants`() {
        val files = listOf(
            "Tekken 3 (USA).bin",
            "Tekken 3 (Europe) (En,Fr,De,Es,It).bin",
            "Tekken 3 (Japan).bin"
        )
        val candidates = files.mapIndexed { idx, name ->
            val src = GameSourceEntity(
                id = "src_$idx",
                librarySourceId = "root1",
                sourceUri = "content://roms/psx/$name",
                sourceFileName = name,
                relativePath = "PS1/$name",
                systemId = "psx",
                extension = "bin",
                provider = ProviderType.LOCAL_ROM
            )
            CandidateRomImport(source = src, parsed = TitleNormalizer.parse(name))
        }

        val result = GameGroupingEngine.groupGames(
            candidates = candidates,
            existingCanonicalGames = emptyList(),
            existingVariants = emptyList(),
            regionPriority = listOf("Europe", "USA", "Japan"),
            languagePriority = listOf("Es", "En", "Ja")
        )

        assertEquals(1, result.canonicalGames.size)
        assertEquals("Tekken 3", result.canonicalGames.first().displayTitle)
        assertEquals(3, result.variants.size)

        // Verify Europe is automatically chosen as preferred variant according to regionPriority
        val preferred = result.variants.firstOrNull { it.preferred }
        assertNotNull(preferred)
        assertEquals("Europe", preferred?.region)
    }

    @Test
    fun `no over-merge rule flags Resident Evil 2 and Biohazard 2 for match review without external ID`() {
        val re2Src = GameSourceEntity(
            id = "src_re2",
            librarySourceId = "root1",
            sourceUri = "content://roms/psx/Resident Evil 2 (USA).bin",
            sourceFileName = "Resident Evil 2 (USA).bin",
            relativePath = "PS1/Resident Evil 2 (USA).bin",
            systemId = "psx",
            extension = "bin",
            provider = ProviderType.LOCAL_ROM
        )
        val bio2Src = GameSourceEntity(
            id = "src_bio2",
            librarySourceId = "root1",
            sourceUri = "content://roms/psx/Biohazard 2 (Japan).bin",
            sourceFileName = "Biohazard 2 (Japan).bin",
            relativePath = "PS1/Biohazard 2 (Japan).bin",
            systemId = "psx",
            extension = "bin",
            provider = ProviderType.LOCAL_ROM
        )

        val resultWithoutExternalId = GameGroupingEngine.groupGames(
            candidates = listOf(
                CandidateRomImport(re2Src, TitleNormalizer.parse(re2Src.sourceFileName)),
                CandidateRomImport(bio2Src, TitleNormalizer.parse(bio2Src.sourceFileName))
            ),
            existingCanonicalGames = emptyList(),
            existingVariants = emptyList(),
            regionPriority = listOf("USA", "Japan"),
            languagePriority = listOf("En", "Ja")
        )

        // Must NOT over-merge without shared external ID; instead must create a MatchReview
        assertEquals(2, resultWithoutExternalId.canonicalGames.size)
        assertFalse(resultWithoutExternalId.matchReviews.isEmpty())
    }

    @Test
    fun `rescan preserves favorites, playCount, and userEditedMetadata without duplicating games`() {
        val src = GameSourceEntity(
            id = "src_mgs_1",
            librarySourceId = "root1",
            sourceUri = "content://roms/psx/Metal Gear Solid (USA) (Disc 1).chd",
            sourceFileName = "Metal Gear Solid (USA) (Disc 1).chd",
            relativePath = "PS1/Metal Gear Solid (USA) (Disc 1).chd",
            systemId = "psx",
            extension = "chd",
            provider = ProviderType.LOCAL_ROM
        )
        val parsed = TitleNormalizer.parse(src.sourceFileName)
        val initialGroup = GameGroupingEngine.groupGames(
            candidates = listOf(CandidateRomImport(src, parsed)),
            existingCanonicalGames = emptyList(),
            existingVariants = emptyList(),
            regionPriority = listOf("USA"),
            languagePriority = listOf("En")
        )

        val userCustomizedGame: CanonicalGameEntity = initialGroup.canonicalGames.first().copy(
            displayTitle = "Metal Gear Solid: Tactical Espionage",
            favorite = true,
            playCount = 12,
            userEditedMetadata = true
        )

        // Perform a rescan with catalog metadata trying to overwrite displayTitle
        val rescanGroup = GameGroupingEngine.groupGames(
            candidates = listOf(
                CandidateRomImport(
                    source = src,
                    parsed = parsed,
                    catalogDisplayTitle = "Metal Gear Solid Catalog Override"
                )
            ),
            existingCanonicalGames = listOf(userCustomizedGame),
            existingVariants = initialGroup.variants,
            regionPriority = listOf("USA"),
            languagePriority = listOf("En")
        )

        assertEquals(1, rescanGroup.canonicalGames.size)
        val preserved = rescanGroup.canonicalGames.first()
        assertTrue(preserved.favorite)
        assertEquals(12, preserved.playCount)
        assertTrue(preserved.userEditedMetadata)
        assertEquals("Metal Gear Solid: Tactical Espionage", preserved.displayTitle)
    }

    @Test
    fun `streaming JSONL catalog parser and catalog matcher prioritize MD5 and filename`() = runTest {
        val jsonlSample = """
            {"type":"file","p":"psx/Tekken 3 (USA).bin","n":"Tekken 3 (USA).bin","ext":".bin","parent":"psx","urls":[{"u":"https://example.org/ref"}],"meta":{"name":"Tekken 3","desc":"3D Fighting classic","image":"Z:/Digistorage/Arley4dCloudMedia/psx/images/tekken3-image.jpg","developer":"Namco","publisher":"Namco","releasedate":"19980326","genre":"Fighting","players":"2","region":"USA","md5":"a1b2c3d4e5f6"}}
        """.trimIndent()

        val batches = mutableListOf<com.aetherdeck.core.database.CatalogEntryEntity>()
        val count = StreamingJsonlCatalogParser.parseStreamInBatches(
            inputStream = ByteArrayInputStream(jsonlSample.toByteArray(Charsets.UTF_8)),
            defaultSystemId = "psx",
            batchSize = 10
        ) { batch, _ ->
            batches.addAll(batch)
        }

        assertEquals(1, count)
        val entry = batches.first()
        assertEquals("Tekken 3", entry.metaName)
        assertEquals(Integer.valueOf(1998), entry.metaReleaseYear)
        assertEquals("a1b2c3d4e5f6", entry.md5)

        // Match by exact MD5
        val md5Match = CatalogMatcher.matchLocalFile("renamed_file.bin", "a1b2c3d4e5f6", batches)
        assertEquals(CatalogMatchConfidence.EXACT_MD5, md5Match.confidence)

        // Match by exact filename
        val fileMatch = CatalogMatcher.matchLocalFile("Tekken 3 (USA).bin", null, batches)
        assertEquals(CatalogMatchConfidence.EXACT_FILENAME, fileMatch.confidence)

        // Verify ArleyMediaResolver pointer URL generation
        val pointerUrls = ArleyMediaResolver.buildCandidatePointerUrls(
            rawCatalogMediaPath = "Z:/Digistorage/Arley4dCloudMedia/cps3/images/jojoban-image.jpg",
            fallbackSystemId = "cps3"
        )
        assertTrue(pointerUrls.first().endsWith("/cps3/covers/jojoban-image.txt"))
    }
}
