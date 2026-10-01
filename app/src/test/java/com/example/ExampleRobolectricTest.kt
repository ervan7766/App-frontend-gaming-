package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.aetherdeck.core.database.CanonicalGameEntity
import com.aetherdeck.core.database.GameSourceEntity
import com.aetherdeck.core.database.GameVariantEntity
import com.aetherdeck.core.models.ProviderType
import com.aetherdeck.emulators.config.EmulatorConfigManager
import com.aetherdeck.emulators.detection.EmulatorDetector
import com.aetherdeck.launchers.retroarch.RetroArchAdapter
import com.aetherdeck.launchers.router.Arley4dLauncher
import com.aetherdeck.launchers.router.GameLaunchRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app_name is AetherDeck and config assets load cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("AetherDeck", context.getString(R.string.app_name))

        val configManager = EmulatorConfigManager(context)
        val systems = configManager.loadSystemsConfig().systems
        val emulators = configManager.loadEmulatorsConfig().emulators

        assertTrue("Systems config must not be empty", systems.isNotEmpty())
        assertTrue("Emulators config must not be empty", emulators.isNotEmpty())
    }

    @Test
    fun `Arley4dLauncher builds verified ACTION_VIEW intent with ComponentName and URI permissions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configManager = EmulatorConfigManager(context)
        val detector = EmulatorDetector(context, configManager)
        val arley4dLauncher = Arley4dLauncher(context, detector)

        val uriStr = "content://com.android.externalstorage.documents/document/primary%3AFakeRoms%2FGodOfWar.arley"
        val intent = arley4dLauncher.buildBypassIntent(uriStr)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.arley4d.bypassarley4d", intent.component?.packageName)
        assertEquals("com.arley4d.bypassarley4d.MainActivity", intent.component?.className)
        assertEquals(uriStr, intent.dataString)
        assertNotNull(intent.clipData)
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
    }

    @Test
    fun `RetroArchAdapter builds RetroActivityFuture intent with ROM LIBRETRO and CONFIGFILE extras`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configManager = EmulatorConfigManager(context)
        val detector = EmulatorDetector(context, configManager)
        val retroArchAdapter = RetroArchAdapter(context, detector)

        val romUri = "content://com.android.externalstorage.documents/document/primary%3AROMs%2FSNES%2FChronoTrigger.sfc"
        val intent = retroArchAdapter.createLaunchIntent(
            packageName = "com.retroarch.aarch64",
            romUriOrPath = romUri,
            coreFileName = "snes9x_libretro_android.so"
        )

        assertEquals("com.retroarch.aarch64", intent.component?.packageName)
        assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", intent.component?.className)
        assertEquals("/storage/emulated/0/ROMs/SNES/ChronoTrigger.sfc", intent.getStringExtra("ROM"))
        assertEquals("/data/data/com.retroarch.aarch64/cores/snes9x_libretro_android.so", intent.getStringExtra("LIBRETRO"))
        assertEquals("/data/data/com.retroarch.aarch64", intent.getStringExtra("DATADIR"))
        assertEquals("/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg", intent.getStringExtra("CONFIGFILE"))
    }

    @Test
    fun `GameLaunchRouter blocks unverified catalog-only launch and routes Fake ROM to Arley4d`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configManager = EmulatorConfigManager(context)
        val detector = EmulatorDetector(context, configManager)
        val retroArchAdapter = RetroArchAdapter(context, detector)
        val arley4dLauncher = Arley4dLauncher(context, detector)
        val router = GameLaunchRouter(context, configManager, detector, retroArchAdapter, arley4dLauncher)

        val game = CanonicalGameEntity(
            id = "g1",
            displayTitle = "JoJo's Venture",
            normalizedTitle = "jojo s venture",
            sortTitle = "jojo s venture",
            systemId = "arcade"
        )
        val variant = GameVariantEntity(
            id = "v1",
            canonicalGameId = "g1",
            originalTitle = "jojoban",
            normalizedTitle = "jojoban",
            systemId = "arcade",
            sourceId = "s1"
        )
        val catalogSource = GameSourceEntity(
            id = "s1",
            librarySourceId = "cat",
            sourceUri = "https://example.org/jojoban.zip",
            sourceFileName = "jojoban.zip",
            relativePath = "cps3/jojoban.zip",
            systemId = "arcade",
            extension = "zip",
            provider = ProviderType.ARLEY4D_CATALOG
        )

        val check = router.performPreLaunchCheck(game, variant, catalogSource, null)
        assertEquals(false, check.canLaunch)
        assertNotNull(check.error)
    }
}
