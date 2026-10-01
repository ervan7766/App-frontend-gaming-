package com.aetherdeck.launchers.router

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aetherdeck.core.database.CanonicalGameEntity
import com.aetherdeck.core.database.GameSourceEntity
import com.aetherdeck.core.database.GameVariantEntity
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import com.aetherdeck.core.models.ContextualError
import com.aetherdeck.core.models.PreLaunchCheckResult
import com.aetherdeck.core.models.ProviderType
import com.aetherdeck.emulators.config.EmulatorConfigManager
import com.aetherdeck.emulators.detection.CoreVerificationStatus
import com.aetherdeck.emulators.detection.EmulatorDetector
import com.aetherdeck.launchers.retroarch.RetroArchAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Future-ready contract if Arley4d exposes an official progress broadcast/provider.
 * Currently Arley4d Bypass does NOT expose a 0–100% progress API to external apps,
 * so AetherDeck never fabricates percentages and only reports verified handoff phases.
 */
interface ArleyProgressProvider {
    fun observeExternalProgress(sourceUri: String): Flow<Int?> = emptyFlow()
}

class Arley4dLauncher(
    private val context: Context,
    private val detector: EmulatorDetector
) : ArleyProgressProvider {
    companion object {
        const val PACKAGE_NAME = "com.arley4d.bypassarley4d"
        const val ACTIVITY_NAME = "com.arley4d.bypassarley4d.MainActivity"
    }

    private var lastHandoffSummary: String = "Ninguno"

    fun getLastHandoffSummary(): String = lastHandoffSummary

    fun buildBypassIntent(sourceUriString: String): Intent {
        val uri = Uri.parse(sourceUriString)
        return Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(PACKAGE_NAME, ACTIVITY_NAME)
            data = uri
            clipData = ClipData.newRawUri("Arley4dFakeRom", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun preCheckFakeRom(source: GameSourceEntity, romReadable: Boolean): PreLaunchCheckResult {
        val pm = context.packageManager
        val installed = detector.isPackageInstalled(pm, PACKAGE_NAME)
        val activityValid = installed && detector.isActivityResolvable(pm, PACKAGE_NAME, ACTIVITY_NAME)

        val error = when {
            !source.available -> ContextualError(
                title = "NO SE PUEDE INICIAR",
                whatHappened = "El archivo Fake ROM '${source.sourceFileName}' no está disponible.",
                whyItHappened = "El archivo fue movido, eliminado o el almacenamiento está desconectado.",
                howToFix = "Reconecta el almacenamiento o vuelve a escanear tu carpeta de Fake ROMs.",
                settingsRoute = "providers"
            )
            !romReadable -> ContextualError(
                title = "PERMISO DE CARPETA PERDIDO",
                whatHappened = "AetherDeck no puede leer '${source.sourceFileName}'.",
                whyItHappened = "El permiso persistente de Android SAF caducó o fue revocado.",
                howToFix = "Vuelve a seleccionar tu carpeta de Arley4d Fake ROMs en Fuentes para restaurar el permiso.",
                settingsRoute = "providers"
            )
            !installed -> ContextualError(
                title = "ARLEY4D BYPASS NO INSTALADO",
                whatHappened = "No se puede iniciar '${source.sourceFileName}' porque Arley4d Bypass no está instalado.",
                whyItHappened = "Las Fake ROMs requieren el paquete '$PACKAGE_NAME' para procesar el contenido.",
                howToFix = "Instala el APK de Arley4d Bypass ($PACKAGE_NAME) en tu dispositivo y vuelve a intentarlo.",
                settingsRoute = "help"
            )
            !activityValid -> ContextualError(
                title = "ACTIVITY DE ARLEY4D NO VÁLIDA",
                whatHappened = "El paquete '$PACKAGE_NAME' está instalado, pero no se encontró '$ACTIVITY_NAME'.",
                whyItHappened = "Tu versión instalada de Arley4d Bypass podría tener una configuración distinta.",
                howToFix = "Verifica la versión de Arley4d Bypass en Configuración → Emuladores.",
                settingsRoute = "emulators"
            )
            else -> null
        }

        return PreLaunchCheckResult(
            gameAvailable = source.available,
            sourceReadable = romReadable,
            emulatorInstalled = installed,
            activityValid = activityValid,
            coreConfigured = true,
            coreVerified = true,
            permissionAvailable = romReadable,
            resolvedPackage = PACKAGE_NAME,
            resolvedActivity = ACTIVITY_NAME,
            resolvedCore = null,
            resolvedUri = source.sourceUri,
            error = error
        )
    }

    fun launchFakeRom(source: GameSourceEntity): Result<Unit> {
        return try {
            val intent = buildBypassIntent(source.sourceUri)
            context.startActivity(intent)
            lastHandoffSummary = "${source.sourceFileName} -> $PACKAGE_NAME ($ACTIVITY_NAME)"
            AetherLogger.recordLaunch("Arley4d Handoff ($PACKAGE_NAME/$ACTIVITY_NAME) -> uri=${source.sourceUri}")
            AetherLogger.info(LogCategory.ARLEY4D, "ARLEY_HANDOFF: ${source.sourceFileName}")
            Result.success(Unit)
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.ARLEY4D, "Arley4d Fake ROM handoff failed", e)
            Result.failure(e)
        }
    }
}

class GameLaunchRouter(
    private val context: Context,
    private val configManager: EmulatorConfigManager,
    private val detector: EmulatorDetector,
    private val retroArchAdapter: RetroArchAdapter,
    private val arley4dLauncher: Arley4dLauncher
) {

    /**
     * Resolves human-friendly emulator & core label for Game Details (e.g. "RetroArch · Snes9x" or "PPSSPP").
     */
    fun describeTargetEmulatorForUi(
        game: CanonicalGameEntity,
        variant: GameVariantEntity?,
        source: GameSourceEntity?,
        system: SystemEntity?
    ): String {
        if (source?.provider == ProviderType.ARLEY4D_LOCAL_FAKE_ROM) {
            return "Arley4d Bypass"
        }
        val effectiveEmulatorId = variant?.emulatorOverrideId?.takeIf { it.isNotBlank() }
            ?: game.emulatorOverrideId?.takeIf { it.isNotBlank() }
            ?: system?.selectedEmulatorId?.takeIf { it.isNotBlank() }
            ?: system?.defaultEmulatorId
            ?: "retroarch_aarch64"

        val effectiveCore = variant?.coreOverride?.takeIf { it.isNotBlank() }
            ?: game.coreOverride?.takeIf { it.isNotBlank() }
            ?: system?.selectedCore?.takeIf { it.isNotBlank() }
            ?: system?.defaultCore
            ?: ""

        val emulators = configManager.loadEmulatorsConfig().emulators
        val emuCfg = emulators.firstOrNull { it.id == effectiveEmulatorId }
        val isRetroArch = emuCfg?.launchType == "RETROARCH" || effectiveEmulatorId.startsWith("retroarch")

        return if (isRetroArch) {
            "RetroArch · ${RetroArchAdapter.friendlyCoreName(effectiveCore)}"
        } else {
            emuCfg?.name ?: effectiveEmulatorId
        }
    }

    fun performPreLaunchCheck(
        game: CanonicalGameEntity,
        variant: GameVariantEntity,
        source: GameSourceEntity?,
        system: SystemEntity?,
        preferredRetroArchPackage: String? = null
    ): PreLaunchCheckResult {
        if (source == null) {
            return PreLaunchCheckResult(
                gameAvailable = false,
                sourceReadable = false,
                emulatorInstalled = false,
                activityValid = false,
                coreConfigured = false,
                coreVerified = false,
                permissionAvailable = false,
                resolvedPackage = null,
                resolvedActivity = null,
                resolvedCore = null,
                resolvedUri = null,
                error = ContextualError(
                    title = "NO SE PUEDE INICIAR",
                    whatHappened = "No se encontró el archivo origen para '${game.displayTitle}'.",
                    whyItHappened = "La entrada proviene de una referencia sin archivo local o fue eliminada.",
                    howToFix = "Reescanea tu carpeta de juegos o selecciona una versión disponible.",
                    settingsRoute = "providers"
                )
            )
        }

        // Route 1: ARLEY4D_LOCAL_FAKE_ROM -> Arley4dLauncher (NEVER directly to RetroArch)
        if (source.provider == ProviderType.ARLEY4D_LOCAL_FAKE_ROM) {
            val readable = retroArchAdapter.isRomReadable(source.sourceUri)
            return arley4dLauncher.preCheckFakeRom(source, readable)
        }

        // Route 2: ARLEY4D_CATALOG without local ROM
        if (source.provider == ProviderType.ARLEY4D_CATALOG) {
            return PreLaunchCheckResult(
                gameAvailable = false,
                sourceReadable = false,
                emulatorInstalled = detector.isArley4dInstalled(),
                activityValid = detector.isArley4dInstalled(),
                coreConfigured = true,
                coreVerified = false,
                permissionAvailable = true,
                resolvedPackage = Arley4dLauncher.PACKAGE_NAME,
                resolvedActivity = Arley4dLauncher.ACTIVITY_NAME,
                resolvedCore = null,
                resolvedUri = source.sourceUri,
                error = ContextualError(
                    title = "NO SE PUEDE INICIAR",
                    whatHappened = "'${game.displayTitle}' es solo una referencia del catálogo sin ROM o Fake ROM local.",
                    whyItHappened = "AetherDeck no es un descargador de ROMs y solo inicia archivos locales verificados.",
                    howToFix = "Añade tu carpeta local de ROMs o Fake ROMs de Arley4d y vuelve a escanear.",
                    settingsRoute = "providers"
                )
            )
        }

        // Route 3: LOCAL_ROM or EMULATION_STATION
        val primaryEmulatorId = variant.emulatorOverrideId?.takeIf { it.isNotBlank() }
            ?: game.emulatorOverrideId?.takeIf { it.isNotBlank() }
            ?: system?.selectedEmulatorId?.takeIf { it.isNotBlank() }
            ?: system?.defaultEmulatorId
            ?: "retroarch_aarch64"

        val effectiveCore = variant.coreOverride?.takeIf { it.isNotBlank() }
            ?: game.coreOverride?.takeIf { it.isNotBlank() }
            ?: system?.selectedCore?.takeIf { it.isNotBlank() }
            ?: system?.defaultCore?.takeIf { it.isNotBlank() }
            ?: RetroArchAdapter.inferDefaultCoreForExtension(source.extension)
            ?: RetroArchAdapter.inferDefaultCoreForExtension(source.sourceFileName)
            ?: "snes9x_libretro_android.so"

        val pm = context.packageManager
        val emulators = configManager.loadEmulatorsConfig().emulators
        val primaryConfig = emulators.firstOrNull { it.id == primaryEmulatorId }

        // Check if primary emulator is installed; if not, automatically fallback to an installed alternative (e.g. RetroArch)
        val primaryInstalled = if (primaryConfig?.launchType == "RETROARCH") {
            retroArchAdapter.resolveInstalledRetroArchPackage(
                preferredRetroArchPackage?.takeIf { it.isNotBlank() } ?: primaryConfig.packages.firstOrNull()
            ) != null
        } else {
            primaryConfig?.packages?.any { detector.isPackageInstalled(pm, it) } == true
        }

        val emulatorConfig = if (primaryInstalled && primaryConfig != null && primaryConfig.verified) {
            primaryConfig
        } else {
            val altIds = system?.alternativeEmulatorIdsCsv
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val installedAlt = altIds.mapNotNull { id -> emulators.firstOrNull { it.id == id && it.verified } }
                .firstOrNull { cfg ->
                    if (cfg.launchType == "RETROARCH") {
                        retroArchAdapter.resolveInstalledRetroArchPackage() != null
                    } else {
                        cfg.packages.any { detector.isPackageInstalled(pm, it) }
                    }
                }
            installedAlt
                ?: emulators.firstOrNull { it.launchType == "RETROARCH" && retroArchAdapter.resolveInstalledRetroArchPackage() != null }
                ?: primaryConfig
                ?: emulators.firstOrNull { it.launchType == "RETROARCH" }
        }

        val readable = retroArchAdapter.isRomReadable(source.sourceUri)
        if (!source.available || !readable) {
            return PreLaunchCheckResult(
                gameAvailable = source.available,
                sourceReadable = readable,
                emulatorInstalled = false,
                activityValid = false,
                coreConfigured = effectiveCore.isNotBlank(),
                coreVerified = false,
                permissionAvailable = readable,
                resolvedPackage = null,
                resolvedActivity = null,
                resolvedCore = effectiveCore,
                resolvedUri = source.sourceUri,
                error = ContextualError(
                    title = "NO SE PUEDE INICIAR",
                    whatHappened = "No se puede leer el archivo '${source.sourceFileName}'.",
                    whyItHappened = "El archivo fue movido o el permiso SAF de la carpeta caducó.",
                    howToFix = "Vuelve a conceder permiso a la carpeta en Fuentes o ejecuta un Reescaneo.",
                    settingsRoute = "providers"
                )
            )
        }

        if (emulatorConfig == null) {
            return PreLaunchCheckResult(
                gameAvailable = true,
                sourceReadable = true,
                emulatorInstalled = false,
                activityValid = false,
                coreConfigured = false,
                coreVerified = false,
                permissionAvailable = true,
                resolvedPackage = null,
                resolvedActivity = null,
                resolvedCore = null,
                resolvedUri = source.sourceUri,
                error = ContextualError(
                    title = "NO SE PUEDE INICIAR",
                    whatHappened = "No hay un emulador válido configurado para '$primaryEmulatorId'.",
                    whyItHappened = "El sistema '${game.systemId}' necesita un emulador asignado.",
                    howToFix = "Selecciona un emulador para ${system?.name ?: game.systemId} en Emuladores.",
                    settingsRoute = "emulators"
                )
            )
        }

        val isRetroArch = emulatorConfig.launchType == "RETROARCH"

        // For RetroArch, dynamically resolve any installed RetroArch package (selected preferred -> configured -> any installed variant)
        val installedPkg = if (isRetroArch) {
            retroArchAdapter.resolveInstalledRetroArchPackage(
                preferredRetroArchPackage?.takeIf { it.isNotBlank() } ?: emulatorConfig.packages.firstOrNull()
            )
        } else {
            emulatorConfig.packages.firstOrNull { detector.isPackageInstalled(pm, it) }
        }

        val resolvedAct = if (isRetroArch) {
            RetroArchAdapter.DEFAULT_RETROARCH_ACTIVITY
        } else if (installedPkg != null) {
            emulatorConfig.activities.firstOrNull { detector.isActivityResolvable(pm, installedPkg, it) }
                ?: pm.getLaunchIntentForPackage(installedPkg)?.component?.className
                ?: emulatorConfig.activities.firstOrNull()
        } else {
            emulatorConfig.activities.firstOrNull()
        }

        val coreConfigured = !isRetroArch || effectiveCore.isNotBlank()
        val coreStatus = if (isRetroArch) {
            detector.getRetroArchCoreStatus(installedPkg, effectiveCore)
        } else {
            CoreVerificationStatus.CORE_VERIFIED
        }

        val friendlyCore = RetroArchAdapter.friendlyCoreName(effectiveCore)

        val error = when {
            !emulatorConfig.verified -> ContextualError(
                title = "NO SE PUEDE INICIAR",
                whatHappened = "La integración con ${emulatorConfig.name} está marcada como NO VERIFICADA.",
                whyItHappened = "AetherDeck bloquea perfiles no verificados para evitar pantallas negras.",
                howToFix = "Elige un emulador verificado (como RetroArch, PPSSPP, DuckStation, Dolphin o melonDS).",
                settingsRoute = "emulators",
                gameIdForCoreChange = game.id
            )
            installedPkg == null -> ContextualError(
                title = "NO SE PUEDE INICIAR",
                whatHappened = if (isRetroArch) "RetroArch no está instalado en este dispositivo." else "${emulatorConfig.name} no está instalado.",
                whyItHappened = "'${game.displayTitle}' está configurado para ejecutarse con ${if (isRetroArch) "RetroArch ($friendlyCore)" else emulatorConfig.name}.",
                howToFix = "Instala ${if (isRetroArch) "RetroArch" else emulatorConfig.name} o cambia el emulador predeterminado.",
                settingsRoute = "emulators",
                isMissingRetroArchCore = isRetroArch,
                gameIdForCoreChange = game.id
            )
            resolvedAct == null -> ContextualError(
                title = "NO SE PUEDE INICIAR",
                whatHappened = "No se encontró una Activity válida dentro de '$installedPkg'.",
                whyItHappened = "La versión instalada de ${emulatorConfig.name} no expone la Activity esperada.",
                howToFix = "Abre Configuración → Emuladores para probar el emulador.",
                settingsRoute = "emulators",
                gameIdForCoreChange = game.id
            )
            isRetroArch && (!coreConfigured || coreStatus == CoreVerificationStatus.CORE_MISSING) -> ContextualError(
                title = "NO SE PUEDE INICIAR",
                whatHappened = if (!coreConfigured) "No se encontró el core configurado para este sistema." else "$friendlyCore core no está instalado.",
                whyItHappened = "RetroArch requiere el core '$effectiveCore' descargado para iniciar '${game.displayTitle}' sin pantalla negra.",
                howToFix = "Cambia de core en Detalles del Juego o abre RetroArch → Actualizador en línea → Descargador de núcleos.",
                settingsRoute = "emulators",
                isMissingRetroArchCore = true,
                gameIdForCoreChange = game.id
            )
            else -> null
        }

        return PreLaunchCheckResult(
            gameAvailable = true,
            sourceReadable = true,
            emulatorInstalled = installedPkg != null,
            activityValid = resolvedAct != null && emulatorConfig.verified,
            coreConfigured = coreConfigured,
            coreVerified = coreStatus == CoreVerificationStatus.CORE_VERIFIED,
            permissionAvailable = true,
            resolvedPackage = installedPkg,
            resolvedActivity = resolvedAct,
            resolvedCore = if (isRetroArch) effectiveCore else null,
            resolvedUri = source.sourceUri,
            error = error
        )
    }

    fun executeLaunch(
        checkResult: PreLaunchCheckResult,
        source: GameSourceEntity
    ): Result<Unit> {
        if (!checkResult.canLaunch) {
            val msg = checkResult.error?.whatHappened ?: "Pre-launch check failed"
            return Result.failure(IllegalStateException(msg))
        }

        if (source.provider == ProviderType.ARLEY4D_LOCAL_FAKE_ROM) {
            return arley4dLauncher.launchFakeRom(source)
        }

        val pkg = checkResult.resolvedPackage!!
        val act = checkResult.resolvedActivity!!
        val core = checkResult.resolvedCore

        return try {
            if (core != null) {
                retroArchAdapter.launchRetroArchActivityWithFallback(
                    packageName = pkg,
                    primaryActivity = act,
                    romUriOrPath = source.sourceUri,
                    coreFileName = core
                )
            } else {
                val intent = buildStandaloneIntent(pkg, act, source.sourceUri)
                context.startActivity(intent)
            }
            AetherLogger.recordLaunch("Launched $pkg/$act -> uri=${source.sourceUri} core=${core ?: "standalone"}")
            Result.success(Unit)
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.EMULATOR, "Failed launching $pkg/$act", e)
            Result.failure(e)
        }
    }

    private fun buildStandaloneIntent(
        packageName: String,
        activityName: String,
        sourceUriString: String
    ): Intent {
        val uri = Uri.parse(sourceUriString)
        val emuCfg = configManager.loadEmulatorsConfig().emulators.firstOrNull { it.packages.contains(packageName) }
        val actionStr = emuCfg?.config?.get("action") ?: Intent.ACTION_VIEW
        val extraKey = emuCfg?.config?.get("extraKey")

        return Intent(actionStr).apply {
            component = ComponentName(packageName, activityName)
            data = uri
            clipData = ClipData.newRawUri("ROM", uri)
            if (!extraKey.isNullOrBlank()) {
                putExtra(extraKey, retroArchAdapter.resolveBestRomPathOrUri(sourceUriString))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Keep AetherDeck alive in background without clearing AetherDeck's task
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
