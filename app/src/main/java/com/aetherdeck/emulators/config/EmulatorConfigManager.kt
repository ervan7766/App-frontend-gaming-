package com.aetherdeck.emulators.config

import android.content.Context
import com.aetherdeck.core.database.SystemEntity
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SystemConfigDto(
    val id: String,
    val name: String,
    val shortName: String,
    val manufacturer: String,
    val releaseYear: Int,
    val folderAliases: List<String> = emptyList(),
    val extensions: List<String> = emptyList(),
    val defaultEmulatorId: String = "",
    val alternativeEmulatorIds: List<String> = emptyList(),
    val defaultCore: String = "",
    val alternativeCores: List<String> = emptyList(),
    val catalogFile: String = "",
    val aspectRatio: Float = 0.75f
)

@Serializable
data class SystemsConfigFile(
    val version: Int = 1,
    val updatedAt: String = "",
    val systems: List<SystemConfigDto> = emptyList()
)

@Serializable
data class EmulatorConfigDto(
    val id: String,
    val name: String,
    val packages: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val supportedSystems: List<String> = emptyList(),
    val extensions: List<String> = emptyList(),
    val launchType: String = "STANDALONE_VIEW_DATA",
    val supportsContentUri: Boolean = true,
    val supportsFilePath: Boolean = true,
    val requiresCore: Boolean = false,
    val verified: Boolean = true,
    val config: Map<String, String> = emptyMap()
)

@Serializable
data class EmulatorsConfigFile(
    val version: Int = 1,
    val updatedAt: String = "",
    val emulators: List<EmulatorConfigDto> = emptyList()
)

@Serializable
data class LaunchProfileDto(
    val id: String,
    val emulatorId: String,
    val activityName: String,
    val action: String = "android.intent.action.VIEW",
    val category: String? = null,
    val romExtraKey: String? = null,
    val libretroExtraKey: String? = null,
    val configFileExtraKey: String? = null,
    val useDataUri: Boolean = false,
    val useClipData: Boolean = false,
    val grantReadUriPermission: Boolean = true,
    val newTaskFlag: Boolean = true,
    val verified: Boolean = true
)

@Serializable
data class LaunchProfilesConfigFile(
    val version: Int = 1,
    val updatedAt: String = "",
    val profiles: List<LaunchProfileDto> = emptyList()
)

class EmulatorConfigManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val overrideConfigDir: File by lazy {
        File(context.filesDir, "config_updates").apply { mkdirs() }
    }

    fun loadSystemsConfig(): SystemsConfigFile {
        return try {
            val overrideFile = File(overrideConfigDir, "systems.json")
            val assetText = context.assets.open("config/systems.json").bufferedReader().use { it.readText() }
            val bundled = json.decodeFromString<SystemsConfigFile>(assetText)
            if (overrideFile.exists()) {
                val updated = runCatching { json.decodeFromString<SystemsConfigFile>(overrideFile.readText()) }.getOrNull()
                if (updated != null && updated.version >= bundled.version && updated.systems.isNotEmpty()) {
                    return updated
                }
            }
            bundled
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.EMULATOR, "Failed to load systems.json", e)
            SystemsConfigFile()
        }
    }

    fun loadEmulatorsConfig(): EmulatorsConfigFile {
        return try {
            val overrideFile = File(overrideConfigDir, "emulators.json")
            val assetText = context.assets.open("config/emulators.json").bufferedReader().use { it.readText() }
            val bundled = json.decodeFromString<EmulatorsConfigFile>(assetText)
            if (overrideFile.exists()) {
                val updated = runCatching { json.decodeFromString<EmulatorsConfigFile>(overrideFile.readText()) }.getOrNull()
                if (updated != null && updated.version >= bundled.version && updated.emulators.isNotEmpty()) {
                    return updated
                }
            }
            bundled
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.EMULATOR, "Failed to load emulators.json", e)
            EmulatorsConfigFile()
        }
    }

    fun loadLaunchProfilesConfig(): LaunchProfilesConfigFile {
        return try {
            val assetText = context.assets.open("config/launch_profiles.json").bufferedReader().use { it.readText() }
            json.decodeFromString<LaunchProfilesConfigFile>(assetText)
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.EMULATOR, "Failed to load launch_profiles.json", e)
            LaunchProfilesConfigFile()
        }
    }

    fun toSystemEntities(existingSystems: List<SystemEntity>): List<SystemEntity> {
        val existingById = existingSystems.associateBy { it.id }
        return loadSystemsConfig().systems.map { dto ->
            val prev = existingById[dto.id]
            SystemEntity(
                id = dto.id,
                name = dto.name,
                shortName = dto.shortName,
                manufacturer = dto.manufacturer,
                releaseYear = dto.releaseYear,
                folderAliasesCsv = dto.folderAliases.joinToString(","),
                extensionsCsv = dto.extensions.joinToString(","),
                defaultEmulatorId = dto.defaultEmulatorId,
                alternativeEmulatorIdsCsv = dto.alternativeEmulatorIds.joinToString(","),
                defaultCore = dto.defaultCore,
                alternativeCoresCsv = dto.alternativeCores.joinToString(","),
                catalogFile = dto.catalogFile,
                aspectRatio = dto.aspectRatio,
                selectedEmulatorId = prev?.selectedEmulatorId ?: dto.defaultEmulatorId,
                selectedCore = prev?.selectedCore ?: dto.defaultCore,
                catalogSyncEnabled = prev?.catalogSyncEnabled ?: (dto.id in setOf("snes", "psx", "gba", "psp"))
            )
        }
    }

    /**
     * Validates and saves updated JSON definitions without executing any remote code.
     */
    fun applyValidatedDefinitionsUpdate(systemsJson: String?, emulatorsJson: String?): Boolean {
        return try {
            if (!systemsJson.isNullOrBlank()) {
                val parsed = json.decodeFromString<SystemsConfigFile>(systemsJson)
                require(parsed.systems.isNotEmpty()) { "Systems list cannot be empty" }
                File(overrideConfigDir, "systems.json").writeText(systemsJson)
            }
            if (!emulatorsJson.isNullOrBlank()) {
                val parsed = json.decodeFromString<EmulatorsConfigFile>(emulatorsJson)
                require(parsed.emulators.isNotEmpty()) { "Emulators list cannot be empty" }
                File(overrideConfigDir, "emulators.json").writeText(emulatorsJson)
            }
            AetherLogger.info(LogCategory.EMULATOR, "Updated versioned system/emulator definitions")
            true
        } catch (e: Exception) {
            AetherLogger.error(LogCategory.EMULATOR, "Rejected invalid definitions update", e)
            false
        }
    }
}
