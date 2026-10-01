package com.aetherdeck.launchers.retroarch

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import com.aetherdeck.emulators.detection.CoreVerificationStatus
import com.aetherdeck.emulators.detection.EmulatorDetector
import java.io.File
import java.util.Locale

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
 * Uses com.retroarch.browser.retroactivity.RetroActivityFuture (with automatic fallback).
 * Passes ROM (resolved POSIX path), LIBRETRO (/data/data/<pkg>/cores/<core>.so), CONFIGFILE,
 * IME, DATADIR, SDCARD, EXTERNAL, and APK extras as required by RetroArch's platform_unix.c JNI entry.
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
            "mame2003_plus_libretro_android.so" to "MAME 2003-Plus",
            "dolphin_libretro_android.so" to "Dolphin Core"
        )

        fun friendlyCoreName(coreFileName: String?): String {
            if (coreFileName.isNullOrBlank()) return "Sin core"
            val clean = coreFileName.substringAfterLast('/')
            return CORE_FRIENDLY_NAMES[clean] ?: clean.removeSuffix("_libretro_android.so").uppercase()
        }

        fun inferDefaultCoreForExtension(extOrPath: String): String? {
            val ext = extOrPath.substringAfterLast('.', extOrPath).lowercase(Locale.ROOT).trim()
            return when (ext) {
                "sfc", "smc", "fig", "swc" -> "snes9x_libretro_android.so"
                "gba", "agb" -> "mgba_libretro_android.so"
                "gb", "gbc", "dmg", "cgb", "sgb" -> "gambatte_libretro_android.so"
                "nes", "fds", "unf" -> "fceumm_libretro_android.so"
                "md", "gen", "smd", "sgd" -> "genesis_plus_gx_libretro_android.so"
                "z64", "n64", "v64" -> "mupen64plus_next_gles3_libretro_android.so"
                "nds", "dsi", "ids" -> "melonds_libretro_android.so"
                "pbp", "cue", "bin", "chd", "img", "mdf", "m3u" -> "pcsx_rearmed_libretro_android.so"
                "cso", "iso", "elf" -> "ppsspp_libretro_android.so"
                "gdi", "cdi" -> "flycast_libretro_android.so"
                "rvz", "gcm", "gcz", "wbfs" -> "dolphin_libretro_android.so"
                "zip", "7z" -> "fbneo_libretro_android.so"
                else -> null
            }
        }
    }

    fun resolveInstalledRetroArchPackage(preferredPackage: String? = null): String? {
        val installed = detector.detectRetroArchPackages()
        if (!preferredPackage.isNullOrBlank() && installed.contains(preferredPackage)) {
            return preferredPackage
        }
        return installed.firstOrNull()
    }

    fun resolveExternalStorageRoot(): String {
        val envPath = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrNull()
        return if (!envPath.isNullOrBlank() && envPath.startsWith("/storage/")) {
            envPath
        } else {
            "/storage/emulated/0"
        }
    }

    fun buildConfigFilePath(packageName: String, customConfigPath: String? = null): String {
        if (!customConfigPath.isNullOrBlank()) return customConfigPath
        val sdcard = resolveExternalStorageRoot()
        return "$sdcard/Android/data/$packageName/files/retroarch.cfg"
    }

    /**
     * Resolves the full path to the Libretro core (.so) as required by RetroArch's native JNI entry
     * (platform_unix.c expects "/data/data/<packageName>/cores/<core_name>_libretro_android.so").
     */
    fun buildFullCorePath(packageName: String, coreFileName: String): String {
        val trimmed = coreFileName.trim()
        if (trimmed.startsWith("/data/data/") || trimmed.startsWith("/data/user/")) {
            return trimmed
        }
        val cleanCoreName = trimmed.substringAfterLast('/')
        val externalCandidates = listOf(
            File("/storage/emulated/0/RetroArch/cores/$cleanCoreName"),
            File("/storage/emulated/0/Android/data/$packageName/files/cores/$cleanCoreName")
        )
        for (candidate in externalCandidates) {
            if (runCatching { candidate.exists() && candidate.canRead() }.getOrDefault(false)) {
                return candidate.absolutePath
            }
        }
        return "/data/data/$packageName/cores/$cleanCoreName"
    }

    /**
     * Opens RetroArch standalone app without a ROM ("TEST APP / ABRIR RETROARCH").
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
                    component = ComponentName(pkg, "com.retroarch.browser.mainmenu.MainMenuActivity")
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            AetherLogger.info(LogCategory.RETROARCH, "Opened RetroArch standalone ($pkg)")
            Result.success("RetroArch ($pkg) abierto correctamente")
        } catch (e: Exception) {
            // Secondary fallback to RetroActivityFuture if MainMenuActivity wasn't resolved
            try {
                val fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(pkg, DEFAULT_RETROARCH_ACTIVITY)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                Result.success("RetroArch ($pkg) abierto correctamente")
            } catch (e2: Exception) {
                AetherLogger.error(LogCategory.RETROARCH, "Failed opening RetroArch ($pkg)", e2)
                Result.failure(e2)
            }
        }
    }

    /**
     * Builds the verified RetroArch launch Intent with all JNI extras required by RetroActivityFuture:
     * ROM, LIBRETRO (/data/data/<pkg>/cores/<core>.so), CONFIGFILE, IME, DATADIR, SDCARD, EXTERNAL, and APK.
     */
    fun createLaunchIntent(
        packageName: String,
        activityName: String = DEFAULT_RETROARCH_ACTIVITY,
        romUriOrPath: String,
        coreFileName: String,
        configFilePath: String? = null
    ): Intent {
        val fullCorePath = buildFullCorePath(packageName, coreFileName)
        val cfgPath = buildConfigFilePath(packageName, configFilePath)
        val parsedUri = runCatching { Uri.parse(romUriOrPath) }.getOrNull()
        val romParam = if (romUriOrPath.startsWith("content://")) {
            resolveBestRomPathOrUri(romUriOrPath)
        } else {
            romUriOrPath
        }

        val sdcardPath = resolveExternalStorageRoot()
        val dataDirPath = "/data/data/$packageName"
        val externalFilesPath = "$sdcardPath/Android/data/$packageName/files"
        val currentIme = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "com.android.inputmethod.latin/.LatinIME"
        val apkSourceDir = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).sourceDir
        }.getOrNull()

        return Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(packageName, activityName)
            putExtra("ROM", romParam)
            putExtra("LIBRETRO", fullCorePath)
            putExtra("CONFIGFILE", cfgPath)
            putExtra("IME", currentIme)
            putExtra("DATADIR", dataDirPath)
            putExtra("SDCARD", sdcardPath)
            putExtra("EXTERNAL", externalFilesPath)
            if (!apkSourceDir.isNullOrBlank()) {
                putExtra("APK", apkSourceDir)
            }
            if (parsedUri != null && romUriOrPath.startsWith("content://")) {
                // Only set intent.data if romParam could not be resolved to a POSIX file path;
                // always attach ClipData + URI read permission so RetroArch has both path and SAF access.
                if (romParam.startsWith("content://")) {
                    data = parsedUri
                }
                clipData = ClipData.newRawUri("ROM", parsedUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Restart RetroArch's own task cleanly so a backgrounded RetroArch menu doesn't swallow the new ROM extras
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    /**
     * Starts RetroArch with automatic fallback across known RetroArch activity names if the primary activity fails.
     */
    fun launchRetroArchActivityWithFallback(
        packageName: String,
        primaryActivity: String = DEFAULT_RETROARCH_ACTIVITY,
        romUriOrPath: String,
        coreFileName: String,
        configFilePath: String? = null
    ) {
        val candidateActivities = listOf(
            primaryActivity,
            DEFAULT_RETROARCH_ACTIVITY,
            "com.retroarch.browser.retroactivity.RetroActivityPast",
            "com.retroarch.browser.mainmenu.MainMenuActivity"
        ).distinct()

        var lastError: Exception? = null
        for (act in candidateActivities) {
            try {
                val intent = createLaunchIntent(
                    packageName = packageName,
                    activityName = act,
                    romUriOrPath = romUriOrPath,
                    coreFileName = coreFileName,
                    configFilePath = configFilePath
                )
                AetherLogger.info(
                    LogCategory.RETROARCH,
                    "Launching RetroArch: pkg=$packageName act=$act ROM=${intent.getStringExtra("ROM")} LIBRETRO=${intent.getStringExtra("LIBRETRO")}"
                )
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                lastError = e
                AetherLogger.warn(LogCategory.RETROARCH, "Attempt with activity $act failed: ${e.message}")
            }
        }
        throw (lastError ?: IllegalStateException("No se pudo iniciar ninguna Activity de RetroArch en $packageName"))
    }

    /**
     * Runs a full diagnostic test launch for a chosen ROM ("TEST GAME / PROBAR JUEGO").
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

        val resolvedRomPath = resolveBestRomPathOrUri(romUriString)
        // If user left the default snes9x core but selected a ROM for another system (e.g. .gba, .bin, .nds), auto-infer matching core
        val effectiveCoreFile = if (coreFileName.isBlank() || coreFileName == "snes9x_libretro_android.so") {
            inferDefaultCoreForExtension(resolvedRomPath) ?: coreFileName.ifBlank { "snes9x_libretro_android.so" }
        } else {
            coreFileName
        }

        val activityCheck = packageCheck && detector.isActivityResolvable(pm, resolvedPkg, activityName)
        val romCheck = isRomReadable(romUriString)
        val coreStatus = detector.getRetroArchCoreStatus(resolvedPkg, effectiveCoreFile)
        val coreCheck = effectiveCoreFile.isNotBlank() &&
            effectiveCoreFile.endsWith(".so") &&
            coreStatus != CoreVerificationStatus.CORE_MISSING
        val cfgPath = buildConfigFilePath(resolvedPkg)
        val configCheck = cfgPath.isNotBlank()

        if (!packageCheck || !activityCheck || !romCheck || !coreCheck) {
            val reason = when {
                !packageCheck -> "RetroArch no está instalado en este dispositivo."
                !activityCheck -> "La Activity $activityName no se encontró en $resolvedPkg."
                !romCheck -> "No se puede leer la ROM seleccionada o se perdió el permiso SAF."
                coreStatus == CoreVerificationStatus.CORE_MISSING -> "${friendlyCoreName(effectiveCoreFile)} ($effectiveCoreFile) no está instalado en RetroArch."
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
                coreName = buildFullCorePath(resolvedPkg, effectiveCoreFile),
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
                launchRetroArchActivityWithFallback(
                    packageName = resolvedPkg,
                    primaryActivity = activityName,
                    romUriOrPath = romUriString,
                    coreFileName = effectiveCoreFile,
                    configFilePath = cfgPath
                )
                launched = true
                message = "RetroArch iniciado: ROM=$resolvedRomPath | Core=${buildFullCorePath(resolvedPkg, effectiveCoreFile)}"
                AetherLogger.recordLaunch("RetroArch ($resolvedPkg) -> core=$effectiveCoreFile rom=$resolvedRomPath")
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
            coreName = buildFullCorePath(resolvedPkg, effectiveCoreFile),
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
                val file = File(uriOrPath)
                file.exists() && file.canRead()
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Converts a SAF content:// URI (tree document or single document) into a real POSIX filesystem path
     * (/storage/emulated/0/... or /storage/<UUID>/...) required by RetroArch's native C fopen().
     * Does NOT gate on File.exists() because Scoped Storage blocks File.exists() on ROM extensions inside
     * the frontend's sandboxed process even when the path exists and RetroArch can read it.
     */
    fun resolveBestRomPathOrUri(uriString: String): String {
        if (!uriString.startsWith("content://")) return uriString
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return uriString

        // 1. Try DocumentsContract documentId extraction (handles both single Document URIs and Tree Document URIs)
        val docId: String? = runCatching {
            DocumentsContract.getDocumentId(uri)
        }.getOrNull() ?: runCatching {
            val decoded = Uri.decode(uriString)
            when {
                decoded.contains("/document/") -> decoded.substringAfterLast("/document/")
                else -> decoded
            }
        }.getOrNull()

        if (!docId.isNullOrBlank()) {
            when {
                docId.startsWith("raw:") -> {
                    return docId.removePrefix("raw:")
                }
                docId.contains("primary:", ignoreCase = true) -> {
                    val idx = docId.lastIndexOf("primary:", ignoreCase = true)
                    val rel = docId.substring(idx + "primary:".length).trimStart('/')
                    return "/storage/emulated/0/$rel"
                }
                docId.startsWith("home:", ignoreCase = true) -> {
                    val rel = docId.substringAfter(":").trimStart('/')
                    return "/storage/emulated/0/Documents/$rel"
                }
                docId.contains(":") -> {
                    val parts = docId.split(":", limit = 2)
                    val volumeId = parts[0].substringAfterLast('/')
                    val rel = parts[1].trimStart('/')
                    if (volumeId.equals("primary", ignoreCase = true)) {
                        return "/storage/emulated/0/$rel"
                    } else if (volumeId.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))) {
                        return "/storage/$volumeId/$rel"
                    }
                }
            }
        }

        // 2. Try resolving real kernel path via /proc/self/fd/<fd> symlink
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val link = Os.readlink("/proc/self/fd/${pfd.fd}")
                if (!link.isNullOrBlank() && link.startsWith("/")) {
                    val normalized = link
                        .replace(Regex("^/mnt/user/\\d+/emulated/0/"), "/storage/emulated/0/")
                        .replace(Regex("^/mnt/pass_through/\\d+/emulated/0/"), "/storage/emulated/0/")
                        .replace(Regex("^/mnt/runtime/[^/]+/emulated/0/"), "/storage/emulated/0/")
                        .replace(Regex("^/data/media/0/"), "/storage/emulated/0/")
                    if (normalized.startsWith("/storage/")) {
                        return normalized
                    }
                }
            }
        }

        // 3. Fallback: decode URI string for primary: or SD volume pattern
        return try {
            val decoded = Uri.decode(uriString)
            val primaryIdx = decoded.lastIndexOf("primary:", ignoreCase = true)
            if (primaryIdx >= 0) {
                val rel = decoded.substring(primaryIdx + "primary:".length).trimStart('/')
                return "/storage/emulated/0/$rel"
            }
            uriString
        } catch (_: Exception) {
            uriString
        }
    }
}
