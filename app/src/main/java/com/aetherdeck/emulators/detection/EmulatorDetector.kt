package com.aetherdeck.emulators.detection

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import com.aetherdeck.emulators.config.EmulatorConfigDto
import com.aetherdeck.emulators.config.EmulatorConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class CoreVerificationStatus(val labelEs: String, val labelEn: String) {
    RETROARCH_NOT_INSTALLED("RetroArch no instalado", "RetroArch Not Installed"),
    RETROARCH_INSTALLED_NO_CORE("Sin core seleccionado", "RetroArch Installed (No Core Set)"),
    CORE_CONFIGURED("Core configurado (No verificado en disco)", "Core Configured"),
    CORE_VERIFIED("Core verificado ✓", "Core Verified ✓"),
    CORE_MISSING("Core no instalado en RetroArch", "Core Missing");

    val label: String get() = labelEs
}

data class DetectedEmulatorStatus(
    val config: EmulatorConfigDto,
    val isInstalled: Boolean,
    val installedPackage: String?,
    val installedVersion: String?,
    val resolvedActivity: String?,
    val activityVerified: Boolean,
    val verificationNote: String
)

data class InstalledAndroidApp(
    val packageName: String,
    val label: String,
    val isFavorite: Boolean = false,
    val isOnHome: Boolean = false,
    val isHidden: Boolean = false,
    val iconDrawable: Drawable? = null
)

class EmulatorDetector(
    private val context: Context,
    private val configManager: EmulatorConfigManager
) {
    companion object {
        val RETROARCH_PACKAGES = listOf(
            "com.retroarch.aarch64",
            "com.retroarch",
            "com.retroarch.ra32"
        )
        const val ARLEY4D_PACKAGE = "com.arley4d.bypassarley4d"
        const val ARLEY4D_ACTIVITY = "com.arley4d.bypassarley4d.MainActivity"

        const val ACTION_QUERY_INSTALLED_CORES = "com.retroarch.QUERY_INSTALLED_CORES"
        const val ACTION_INSTALLED_CORES_RESULT = "com.retroarch.INSTALLED_CORES_RESULT"
        const val EXTRA_CORES = "CORES"
    }

    private val _queriedRetroArchCores = MutableStateFlow<Set<String>>(emptySet())
    val queriedRetroArchCores: StateFlow<Set<String>> = _queriedRetroArchCores.asStateFlow()

    private var coreResultReceiverRegistered = false

    private val coreResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == ACTION_INSTALLED_CORES_RESULT) {
                val coresArray = intent.getStringArrayExtra(EXTRA_CORES)
                    ?: intent.getStringArrayListExtra(EXTRA_CORES)?.toTypedArray()
                val rawString = intent.getStringExtra(EXTRA_CORES)
                val parsed = mutableSetOf<String>()
                coresArray?.forEach { c ->
                    val fileName = c.substringAfterLast('/').trim()
                    if (fileName.isNotEmpty()) parsed.add(fileName)
                }
                rawString?.split(",", ";", "\n")?.forEach { c ->
                    val fileName = c.substringAfterLast('/').trim()
                    if (fileName.isNotEmpty()) parsed.add(fileName)
                }
                if (parsed.isNotEmpty()) {
                    _queriedRetroArchCores.value = _queriedRetroArchCores.value + parsed
                    AetherLogger.info(
                        LogCategory.RETROARCH,
                        "Received INSTALLED_CORES_RESULT from RetroArch: ${parsed.joinToString(", ")}"
                    )
                }
            }
        }
    }

    fun registerCoreQueryReceiverIfNeeded() {
        if (coreResultReceiverRegistered) return
        try {
            val filter = IntentFilter(ACTION_INSTALLED_CORES_RESULT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    coreResultReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.applicationContext.registerReceiver(coreResultReceiver, filter)
            }
            coreResultReceiverRegistered = true
        } catch (e: Exception) {
            AetherLogger.warn(LogCategory.RETROARCH, "Could not register INSTALLED_CORES_RESULT receiver", e.message)
        }
    }

    /**
     * Queries RetroArch for installed cores via com.retroarch.QUERY_INSTALLED_CORES
     * and also scans accessible storage directories.
     */
    fun queryInstalledRetroArchCores(preferredPackage: String? = null): Set<String> {
        registerCoreQueryReceiverIfNeeded()
        val installedPkgs = detectRetroArchPackages()
        val targetPkg = preferredPackage?.takeIf { installedPkgs.contains(it) } ?: installedPkgs.firstOrNull()

        val discovered = mutableSetOf<String>()
        discovered.addAll(_queriedRetroArchCores.value)

        if (targetPkg != null) {
            try {
                val queryIntent = Intent(ACTION_QUERY_INSTALLED_CORES).apply {
                    setPackage(targetPkg)
                    putExtra("PACKAGE", context.packageName)
                }
                context.sendBroadcast(queryIntent)
                AetherLogger.info(LogCategory.RETROARCH, "Sent QUERY_INSTALLED_CORES to $targetPkg")
            } catch (e: Exception) {
                AetherLogger.warn(LogCategory.RETROARCH, "Broadcast QUERY_INSTALLED_CORES failed", e.message)
            }

            // Also inspect accessible RetroArch directories if present
            val candidateDirs = listOf(
                File("/storage/emulated/0/RetroArch/cores"),
                File("/storage/emulated/0/Android/data/$targetPkg/files/cores")
            )
            for (dir in candidateDirs) {
                runCatching {
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles()?.forEach { f ->
                            if (f.name.endsWith(".so")) {
                                discovered.add(f.name)
                            }
                        }
                    }
                }
            }
        }

        if (discovered.isNotEmpty()) {
            _queriedRetroArchCores.value = discovered
        }
        return _queriedRetroArchCores.value
    }

    fun detectAllEmulators(): List<DetectedEmulatorStatus> {
        val pm = context.packageManager
        val configs = configManager.loadEmulatorsConfig().emulators
        return configs.map { cfg ->
            var foundPkg: String? = null
            var foundVer: String? = null
            var foundAct: String? = null
            var actVerified = false

            for (pkg in cfg.packages) {
                if (isPackageInstalled(pm, pkg)) {
                    foundPkg = pkg
                    foundVer = getPackageVersion(pm, pkg)
                    for (act in cfg.activities) {
                        if (isActivityResolvable(pm, pkg, act)) {
                            foundAct = act
                            actVerified = true
                            break
                        }
                    }
                    if (foundAct == null) {
                        val launchIntent = pm.getLaunchIntentForPackage(pkg)
                        foundAct = launchIntent?.component?.className ?: cfg.activities.firstOrNull()
                        actVerified = launchIntent != null
                    }
                    break
                }
            }

            val note = when {
                foundPkg == null -> "No instalado"
                !cfg.verified -> "Instalado (NO VERIFICADO)"
                actVerified -> "Instalado y verificado ($foundPkg${foundVer?.let { " v$it" }.orEmpty()})"
                else -> "Instalado ($foundPkg)"
            }

            DetectedEmulatorStatus(
                config = cfg,
                isInstalled = foundPkg != null,
                installedPackage = foundPkg,
                installedVersion = foundVer,
                resolvedActivity = foundAct,
                activityVerified = actVerified && cfg.verified,
                verificationNote = note
            )
        }
    }

    fun detectRetroArchPackages(): List<String> {
        val pm = context.packageManager
        return RETROARCH_PACKAGES.filter { isPackageInstalled(pm, it) }
    }

    fun getPackageVersion(pm: PackageManager, packageName: String): String? {
        return try {
            pm.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
    }

    fun isArley4dInstalled(): Boolean {
        return isPackageInstalled(context.packageManager, ARLEY4D_PACKAGE)
    }

    fun getRetroArchCoreStatus(retroArchPackage: String?, coreFileName: String?): CoreVerificationStatus {
        val installedPkgs = detectRetroArchPackages()
        val pkg = retroArchPackage?.takeIf { installedPkgs.contains(it) } ?: installedPkgs.firstOrNull()
            ?: return CoreVerificationStatus.RETROARCH_NOT_INSTALLED

        if (coreFileName.isNullOrBlank()) {
            return CoreVerificationStatus.RETROARCH_INSTALLED_NO_CORE
        }

        val cleanCore = coreFileName.substringAfterLast('/')
        val queried = queryInstalledRetroArchCores(pkg)
        if (queried.contains(cleanCore)) {
            return CoreVerificationStatus.CORE_VERIFIED
        }

        // If RetroArch reported its full list of cores via broadcast and this core isn't in it, mark CORE_MISSING
        if (_queriedRetroArchCores.value.isNotEmpty() && !_queriedRetroArchCores.value.contains(cleanCore)) {
            return CoreVerificationStatus.CORE_MISSING
        }

        val accessibleCorePaths = listOf(
            "/storage/emulated/0/RetroArch/cores/$cleanCore",
            "/storage/emulated/0/Android/data/$pkg/files/cores/$cleanCore"
        )
        val canVerifyOnDisk = accessibleCorePaths.any { path ->
            runCatching { File(path).exists() }.getOrDefault(false)
        }

        return if (canVerifyOnDisk) {
            CoreVerificationStatus.CORE_VERIFIED
        } else {
            CoreVerificationStatus.CORE_CONFIGURED
        }
    }

    fun queryLauncherApps(
        favoritePackages: Set<String>,
        homePackages: Set<String> = emptySet(),
        hiddenPackages: Set<String> = emptySet()
    ): List<InstalledAndroidApp> {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val activities = pm.queryIntentActivities(intent, 0)
            activities
                .mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    val label = resolveInfo.loadLabel(pm)?.toString() ?: pkg
                    val icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
                    InstalledAndroidApp(
                        packageName = pkg,
                        label = label,
                        isFavorite = favoritePackages.contains(pkg),
                        isOnHome = homePackages.contains(pkg),
                        isHidden = hiddenPackages.contains(pkg),
                        iconDrawable = icon
                    )
                }
                .distinctBy { it.packageName }
                .sortedWith(compareByDescending<InstalledAndroidApp> { it.isFavorite }.thenBy { it.label.lowercase() })
        } catch (e: Exception) {
            AetherLogger.warn(LogCategory.EMULATOR, "Could not query launcher apps", e.message)
            emptyList()
        }
    }

    fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isActivityResolvable(pm: PackageManager, packageName: String, activityClassName: String): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(packageName, activityClassName)
            }
            val list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (list.isNotEmpty()) return true
            pm.getActivityInfo(ComponentName(packageName, activityClassName), 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
