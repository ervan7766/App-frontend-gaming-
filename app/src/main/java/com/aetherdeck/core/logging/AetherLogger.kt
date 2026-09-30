package com.aetherdeck.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogCategory {
    APP,
    SCAN,
    DATABASE,
    CATALOG,
    MATCH,
    SCRAPER,
    MEDIA,
    EMULATOR,
    RETROARCH,
    ARLEY4D,
    INPUT,
    UI
}

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val category: LogCategory,
    val level: LogLevel,
    val message: String,
    val details: String? = null
)

object AetherLogger {
    private const val MAX_ENTRIES = 500
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _lastScraperRequest = MutableStateFlow<String>("None")
    val lastScraperRequest: StateFlow<String> = _lastScraperRequest.asStateFlow()

    private val _lastProviderError = MutableStateFlow<String>("None")
    val lastProviderError: StateFlow<String> = _lastProviderError.asStateFlow()

    private val _lastLaunchSummary = MutableStateFlow<String>("None")
    val lastLaunchSummary: StateFlow<String> = _lastLaunchSummary.asStateFlow()

    fun info(category: LogCategory, message: String, details: String? = null) {
        record(category, LogLevel.INFO, message, details)
        try {
            Log.i("AetherDeck-${category.name}", message)
        } catch (_: Throwable) {
            // Safe fallback for unit test JVM
        }
    }

    fun warn(category: LogCategory, message: String, details: String? = null) {
        record(category, LogLevel.WARN, message, details)
        try {
            Log.w("AetherDeck-${category.name}", message)
        } catch (_: Throwable) {
        }
    }

    fun error(category: LogCategory, message: String, throwable: Throwable? = null) {
        val details = throwable?.let { "${it::class.simpleName}: ${it.message}" }
        record(category, LogLevel.ERROR, message, details)
        if (category == LogCategory.CATALOG || category == LogCategory.SCAN || category == LogCategory.SCRAPER) {
            _lastProviderError.value = "$message ${details ?: ""}".trim()
        }
        try {
            Log.e("AetherDeck-${category.name}", message, throwable)
        } catch (_: Throwable) {
        }
    }

    fun recordScraperRequest(summary: String) {
        _lastScraperRequest.value = redactSecrets(summary)
        info(LogCategory.SCRAPER, "Scraper request: ${redactSecrets(summary)}")
    }

    fun recordLaunch(summary: String) {
        _lastLaunchSummary.value = summary
        info(LogCategory.EMULATOR, "Launch event: $summary")
    }

    private fun record(category: LogCategory, level: LogLevel, message: String, details: String?) {
        val sanitizedMsg = redactSecrets(message)
        val sanitizedDetails = details?.let { redactSecrets(it) }
        val entry = LogEntry(
            category = category,
            level = level,
            message = sanitizedMsg,
            details = sanitizedDetails
        )
        val current = _entries.value
        _entries.value = (listOf(entry) + current).take(MAX_ENTRIES)
    }

    fun redactSecrets(input: String): String {
        return input
            .replace(Regex("(?i)(api[_-]?key|password|token|secret|devpassword)=([^&\\s]+)"), "$1=[REDACTED]")
            .replace(Regex("(?i)(Bearer\\s+)[A-Za-z0-9._-]+"), "$1[REDACTED]")
    }

    fun formatLogsForExport(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return _entries.value.reversed().joinToString("\n") { entry ->
            val time = formatter.format(Date(entry.timestamp))
            val extra = if (!entry.details.isNullOrBlank()) " | ${entry.details}" else ""
            "[$time] [${entry.level}] [${entry.category}] ${entry.message}$extra"
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
