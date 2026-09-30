package com.aetherdeck.collections.smart

import com.aetherdeck.core.database.CanonicalGameWithDetails
import com.aetherdeck.core.database.GameSourceEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
enum class SmartRuleField(val label: String) {
    SYSTEM("System"),
    GENRE("Genre"),
    YEAR("Year"),
    DEVELOPER("Developer"),
    PUBLISHER("Publisher"),
    PLAYERS("Players"),
    RATING("Rating"),
    REGION("Region"),
    LANGUAGE("Language"),
    PROVIDER("Provider"),
    FAVORITE("Favorite"),
    PLAY_COUNT("Play Count")
}

@Serializable
enum class SmartRuleOperator(val label: String) {
    EQUALS("equals"),
    CONTAINS("contains"),
    GREATER("greater than"),
    LESS("less than"),
    IN("in"),
    NOT_IN("not in")
}

@Serializable
enum class LogicalJoin {
    AND, OR
}

@Serializable
data class SmartRuleCondition(
    val field: SmartRuleField,
    val operator: SmartRuleOperator,
    val value: String
)

@Serializable
data class SmartCollectionDefinition(
    val join: LogicalJoin = LogicalJoin.AND,
    val conditions: List<SmartRuleCondition> = emptyList()
)

object SmartCollectionEngine {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeDefinition(def: SmartCollectionDefinition): String = json.encodeToString(def)

    fun decodeDefinition(rawJson: String?): SmartCollectionDefinition {
        if (rawJson.isNullOrBlank()) return SmartCollectionDefinition()
        return try {
            json.decodeFromString(rawJson)
        } catch (_: Exception) {
            SmartCollectionDefinition()
        }
    }

    fun evaluate(
        definition: SmartCollectionDefinition,
        games: List<CanonicalGameWithDetails>,
        sourcesById: Map<String, GameSourceEntity>
    ): List<CanonicalGameWithDetails> {
        if (definition.conditions.isEmpty()) return emptyList()
        return games.filter { item ->
            val results = definition.conditions.map { cond ->
                evaluateCondition(cond, item, sourcesById)
            }
            when (definition.join) {
                LogicalJoin.AND -> results.all { it }
                LogicalJoin.OR -> results.any { it }
            }
        }
    }

    private fun evaluateCondition(
        condition: SmartRuleCondition,
        item: CanonicalGameWithDetails,
        sourcesById: Map<String, GameSourceEntity>
    ): Boolean {
        val game = item.game
        val target = condition.value.trim().lowercase(Locale.ROOT)
        val targetList = target.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        return when (condition.field) {
            SmartRuleField.SYSTEM -> matchString(game.systemId.lowercase(Locale.ROOT), condition.operator, target, targetList)
            SmartRuleField.GENRE -> matchString(game.genre.lowercase(Locale.ROOT), condition.operator, target, targetList)
            SmartRuleField.DEVELOPER -> matchString(game.developer.lowercase(Locale.ROOT), condition.operator, target, targetList)
            SmartRuleField.PUBLISHER -> matchString(game.publisher.lowercase(Locale.ROOT), condition.operator, target, targetList)
            SmartRuleField.PLAYERS -> matchString(game.players.lowercase(Locale.ROOT), condition.operator, target, targetList)
            SmartRuleField.YEAR -> matchNumber(game.releaseYear?.toDouble(), condition.operator, target.toDoubleOrNull())
            SmartRuleField.RATING -> matchNumber(game.rating?.toDouble(), condition.operator, target.toDoubleOrNull())
            SmartRuleField.PLAY_COUNT -> matchNumber(game.playCount.toDouble(), condition.operator, target.toDoubleOrNull())
            SmartRuleField.FAVORITE -> {
                val expected = target == "true" || target == "1" || target == "yes"
                game.favorite == expected
            }
            SmartRuleField.REGION -> {
                item.variants.any { v ->
                    matchString(v.region.lowercase(Locale.ROOT), condition.operator, target, targetList)
                }
            }
            SmartRuleField.LANGUAGE -> {
                item.variants.any { v ->
                    matchString(v.languages.lowercase(Locale.ROOT), condition.operator, target, targetList)
                }
            }
            SmartRuleField.PROVIDER -> {
                item.variants.any { v ->
                    val providerName = sourcesById[v.sourceId]?.provider?.name?.lowercase(Locale.ROOT) ?: ""
                    matchString(providerName, condition.operator, target, targetList)
                }
            }
        }
    }

    private fun matchString(
        actual: String,
        operator: SmartRuleOperator,
        target: String,
        targetList: List<String>
    ): Boolean {
        return when (operator) {
            SmartRuleOperator.EQUALS -> actual == target
            SmartRuleOperator.CONTAINS -> actual.contains(target)
            SmartRuleOperator.IN -> targetList.any { actual == it || actual.contains(it) }
            SmartRuleOperator.NOT_IN -> targetList.none { actual == it || actual.contains(it) }
            SmartRuleOperator.GREATER -> actual > target
            SmartRuleOperator.LESS -> actual < target
        }
    }

    private fun matchNumber(
        actual: Double?,
        operator: SmartRuleOperator,
        target: Double?
    ): Boolean {
        if (actual == null || target == null) return false
        return when (operator) {
            SmartRuleOperator.EQUALS -> kotlin.math.abs(actual - target) < 0.001
            SmartRuleOperator.GREATER -> actual > target
            SmartRuleOperator.LESS -> actual < target
            SmartRuleOperator.CONTAINS -> actual.toString().contains(target.toInt().toString())
            SmartRuleOperator.IN -> kotlin.math.abs(actual - target) < 0.001
            SmartRuleOperator.NOT_IN -> kotlin.math.abs(actual - target) >= 0.001
        }
    }
}
