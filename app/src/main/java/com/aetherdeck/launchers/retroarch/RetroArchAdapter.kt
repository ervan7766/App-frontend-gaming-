package com.aetherdeck.launchers.retroarch

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import com.aetherdeck.emulators.detection.CoreVerificationStatus
import com.aetherdeck.emulators.detection.EmulatorDetector

data class RetroArchTestDiagnostic(
    val packageCheck: Boolean,
    val activityCheck: Boolean,
    val romCheck: Boolean,
    val coreCheck: Boolean,
    val configCheck: Boolean,
    val launchSucceeded: Boolean,
    val packageName: String,
    val versionName: String,
    val activityName: String,
    val coreName: String,
    val configPath: String,
    val coreStatus: CoreVerificationStatus,
    val detectedCores: List<String>,
    val message: String
)

/**
 * Dedicated RetroArch adapter.
 * Detects com.retroarch, com.retroarch.aarch64, com.retroarch.ra32 and respects user selection.
 * Uses com.retroarch.browser.retroactivity.RetroActivityFuture (configurable).
 * Passes ROM, LIBRETRO (core name e.g. snes9x_libretro_android.so), and CONFIGFILE extras.
 * Never destroys AetherDeck's own task stack when launching RetroArch.
 */
class RetroArchAdapter(
    private val context: Context,
    private val detector: EmulatorDetector
) {
    companion object {
        const val DEFAULT_RETROARCH_ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"

        private val CORE_FRIENDLY_NAMES = mapOf(
            "snes9x_libretro_android.so" to "Snes9x",
            "snes9x2010_libretro_android.so" to "Snes9x 2010",
            "bsnes_libretro_android.so" to "bsnes",
            "mgba_libretro_android.so" to "mGBA",
            "gambatte_libretro_android.so" to "Gambatte",
            "sameboy_libretro_android.so" to "SameBoy",
            "vbam_libretro_android.so" to "VBA-M",
            "gpsp_libretro_android.so" to "gpSP",
            "pcsx_rearmed_libretro_android.so" to "PCSX-ReARMed",
            "swanstation_libretro_android.so" to "SwanStation",
            "mednafen_psx_hw_libretro_android.so" to "Beetle PSX HW",
            "ppsspp_libretro_android.so" to "PPSSPP Core",
            "fceumm_libretro_android.so" to "FCEUmm",
            "nestopia_libretro_android.so" to "Nestopia",
            "mupen64plus_next_gles3_libretro_android.so" to "Mupen64Plus-Next",
            "melonds_libretro_android.so" to "melonDS Core",
            "genesis_plus_gx_libretro_android.so" to "Genesis Plus GX",
            "picodrive_libretro_android.so" to "PicoDrive",
            "flycast_libretro_android.so" to "Flycast Core",
            "fbneo_libretro_android.so" to "FinalBurn Neo",
            "dolphin_libretro_android.so" to "Dolphin Core"
        )

        fun friendlyCoreName(coreFileName: String?): String {
            if (coreFileName.isNullOrBlank()) return "Sin core"
            val clean = coreFileName.substringAfterLast('/')
            return CORE_FRIENDLY_NAMES[clean] ?: clean.removeSuffix("_libretro_android.so").uppercase()
        }
    }

    fun resolveInstalledRetroArchPackage(preferredPackage: String? = null): String? {
        val installed = detector.detectRetroArchPackages()
        if (!preferredPackage.isNullOrBlank() && installed.contains(preferredPackage)) {
            return preferredPackage
        }
        return installed.firstOrNull()
    }

    fun buildConfigFilePath(packageName: String, customConfigPath: String? = null): String {
        if (!customConfigPath.isNullOrBlank()) return customConfigPath
        return "/storage/emulated/0/Android/data/$packageName/files/retroarch.cfg"
    }

    /**
     * Opens RetroArch standalone app without a ROM ("ABRIR RETROARCH").
     * Preserves AetherDeck's backstack in background.
     */
    fun testLaunchRetroArchApp(preferredPackage: String? = null): Result<String> {
        val pkg = resolveInstalledRetroArchPackage(preferredPackage)
            ?: return Result.failure(
                IllegalStateException("Ninguna variante de RetroArch (com.retroarch / aarch64 / ra32) está instalada en el dispositivo.")
            )

        return try {
            detector.queryInstalledRetroArchCores(pkg)
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(pkg, DEFAULT_RETROARCH_ACTIVITY)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            AetherLogger.info(LogCategory.RETROARCH, "Opened RetroArch standalone ($pkg)")
            Result.success("RetroArch ($pkg) abierto correctamente")
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.RETROARCH, "Failed opening RetroArch ($pkg)", e)
            Result.failure(e)
        }
    }

    /**
     * Builds the verified RetroArch launch Intent with ROM, LIBRETRO, and CONFIGFILE extras.
     * Does NOT use FLAG_ACTIVITY_CLEAR_TASK or finish(), keeping AetherDeck alive in background.
     */
    fun createLaunchIntent(
        packageName: String,
        activityName: String = DEFAULT_RETROARCH_ACTIVITY,
        romUriOrPath: String,
        coreFileName: String,
        configFilePath: String? = null
    ): Intent {
        val cleanCoreName = coreFileName.substringAfterLast('/')
        val cfgPath = buildConfigFilePath(packageName, configFilePath)
        val parsedUri = runCatching { Uri.parse(romUriOrPath) }.getOrNull()
        val romParam = if (romUriOrPath.startsWith("content://")) {
            resolveBestRomPathOrUri(romUriOrPath)
        } else {
            romUriOrPath
        }

        return Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(packageName, activityName)
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("ROM", romParam)
            putExtra("LIBRETRO", cleanCoreName)
            putExtra("CONFIGFILE", cfgPath)
            putExtra("IME", "com.android.inputmethod.latin/.LatinIME")
            if (parsedUri != null && romUriOrPath.startsWith("content://")) {
                data = parsedUri
                clipData = ClipData.newRawUri("ROM", parsedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Runs a full diagnostic test launch for a chosen ROM ("PROBAR JUEGO").
     */
    fun runTestGameLaunch(
        preferredPackage: String?,
        activityName: String = DEFAULT_RETROARCH_ACTIVITY,
        romUriString: String,
        coreFileName: String,
        actuallyStartActivity: Boolean = true
    ): RetroArchTestDiagnostic {
        val pm = context.packageManager
        val pkg = resolveInstalledRetroArchPackage(preferredPackage)
        val packageCheck = pkg != null
        val resolvedPkg = pkg ?: (preferredPackage?.takeIf { it.isNotBlank() } ?: "com.retroarch.aarch64")
        val verName = if (packageCheck) detector.getPackageVersion(pm, resolvedPkg) ?: "1.x" else "No instalado"
        val detectedCores = detector.queryInstalledRetroArchCores(resolvedPkg).toList().sorted()

        val activityCheck = packageCheck && detector.isActivityResolvable(pm, resolvedPkg, activityName)
        val romCheck = isRomReadable(romUriString)
        val coreStatus = detector.getRetroArchCoreStatus(resolvedPkg, coreFileName)
        val coreCheck = coreFileName.isNotBlank() &&
            coreFileName.endsWith(".so") &&
            coreStatus != CoreVerificationStatus.CORE_MISSING
        val cfgPath = buildConfigFilePath(resolvedPkg)
        val configCheck = cfgPath.isNotBlank()

        if (!packageCheck || !activityCheck || !romCheck || !coreCheck) {
            val reason = when {
                !packageCheck -> "RetroArch no está instalado en este dispositivo."
                !activityCheck -> "La Activity $activityName no se encontró en $resolvedPkg."
                !romCheck -> "No se puede leer la ROM seleccionada o se perdió el permiso SAF."
                coreStatus == CoreVerificationStatus.CORE_MISSING -> "${friendlyCoreName(coreFileName)} ($coreFileName) no está instalado en RetroArch."
                else -> "No se ha seleccionado un core Libretro (.so) válido."
            }
            AetherLogger.warn(LogCategory.RETROARCH, "PROBAR JUEGO pre-check failed: $reason")
            return RetroArchTestDiagnostic(
                packageCheck = packageCheck,
                activityCheck = activityCheck,
                romCheck = romCheck,
                coreCheck = coreCheck,
                configCheck = configCheck,
                launchSucceeded = false,
                packageName = resolvedPkg,
                versionName = verName,
                activityName = activityName,
                coreName = coreFileName,
                configPath = cfgPath,
                coreStatus = coreStatus,
                detectedCores = detectedCores,
                message = reason
            )
        }

        var launched = false
        var message = "Todas las comprobaciones previas superadas."
        if (actuallyStartActivity) {
            try {
                val intent = createLaunchIntent(
                    packageName = resolvedPkg,
                    activityName = activityName,
                    romUriOrPath = romUriString,
                    coreFileName = coreFileName,
                    configFilePath = cfgPath
                )
                context.startActivity(intent)
                launched = true
                message = "RetroArch iniciado con ${friendlyCoreName(coreFileName)} ($coreFileName)."
                AetherLogger.recordLaunch("RetroArch ($resolvedPkg) -> core=$coreFileName rom=$romUriString")
            } catch (e: Exception) {
                launched = false
                message = "Error al iniciar RetroArch: ${e.message}"
                AetherLogger.error(LogCategory.RETROARCH, "Failed starting RetroArch activity", e)
            }
        } else {
            launched = true
        }

        return RetroArchTestDiagnostic(
            packageCheck = true,
            activityCheck = true,
            romCheck = true,
            coreCheck = true,
            configCheck = configCheck,
            launchSucceeded = launched,
            packageName = resolvedPkg,
            versionName = verName,
            activityName = activityName,
            coreName = coreFileName,
            configPath = cfgPath,
            coreStatus = coreStatus,
            detectedCores = detectedCores,
            message = message
        )
    }

    fun isRomReadable(uriOrPath: String): Boolean {
        if (uriOrPath.isBlank()) return false
        return try {
            if (uriOrPath.startsWith("content://")) {
                val uri = Uri.parse(uriOrPath)
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc != null && doc.exists() && doc.canRead()) return true
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            } else {
                val file = java.io.File(uriOrPath)
                file.exists() && file.canRead()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun resolveBestRomPathOrUri(uriString: String): String {
        if (!uriString.startsWith("content://")) return uriString
        return try {
            val decoded = Uri.decode(uriString)
            val primaryIdx = decoded.lastIndexOf("primary:")
            if (primaryIdx >= 0) {
                val rel = decoded.substring(primaryIdx + "primary:".length)
                val candidateFile = java.io.File("/storage/emulated/0/$rel")
                if (candidateFile.exists()) {
                    return candidateFile.absolutePath
                }
            }
            uriString
        } catch (_: Exception) {
            uriString
        }
    }
}
