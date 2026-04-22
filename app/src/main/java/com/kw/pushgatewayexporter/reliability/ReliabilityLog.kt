package com.kw.pushgatewayexporter.reliability

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small in-process ring buffer of reliability-related events, persisted to SharedPreferences
 * as a single JSON-ish line-delimited string.
 *
 * Why not logcat alone? Users need a copy-paste-able diagnostics report that survives app
 * restarts and a device with no USB debugging access.
 *
 * Thread-safe via synchronized writes; reads return a defensive copy.
 */
class ReliabilityLog(context: Context) {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timestampMillis: Long,
        val level: Level,
        val tag: String,
        val message: String
    )

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lock = Any()

    fun d(tag: String, msg: String) = log(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(Level.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(Level.WARN, tag, msg)
    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        val full = if (throwable != null) "$msg :: ${throwable.javaClass.simpleName}: ${throwable.message}" else msg
        log(Level.ERROR, tag, full)
    }

    fun log(level: Level, tag: String, message: String) {
        // Mirror to logcat so developers with adb also see it.
        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO -> Log.i(tag, message)
            Level.WARN -> Log.w(tag, message)
            Level.ERROR -> Log.e(tag, message)
        }
        val entry = Entry(System.currentTimeMillis(), level, tag, sanitize(message))
        synchronized(lock) {
            val current = readEntriesLocked().toMutableList()
            current += entry
            if (current.size > MAX_ENTRIES) {
                current.subList(0, current.size - MAX_ENTRIES).clear()
            }
            writeEntriesLocked(current)
        }
    }

    fun entries(): List<Entry> = synchronized(lock) { readEntriesLocked() }

    fun clear() {
        synchronized(lock) { prefs.edit().remove(KEY_BUFFER).apply() }
    }

    /** Render entries as a single copy-paste-friendly string. */
    fun renderText(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
        return entries().joinToString("\n") { e ->
            "${fmt.format(Date(e.timestampMillis))} [${e.level}] ${e.tag}: ${e.message}"
        }
    }

    private fun readEntriesLocked(): List<Entry> {
        val raw = prefs.getString(KEY_BUFFER, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        val result = ArrayList<Entry>()
        for (line in raw.split('\n')) {
            if (line.isBlank()) continue
            // Format: ts|level|tag|message (message may contain \| escaped)
            val parts = line.split('|', limit = 4)
            if (parts.size != 4) continue
            val ts = parts[0].toLongOrNull() ?: continue
            val level = runCatching { Level.valueOf(parts[1]) }.getOrNull() ?: continue
            val tag = parts[2]
            val msg = parts[3].replace("\\n", "\n").replace("\\p", "|")
            result += Entry(ts, level, tag, msg)
        }
        return result
    }

    private fun writeEntriesLocked(entries: List<Entry>) {
        val sb = StringBuilder(entries.size * 64)
        for (e in entries) {
            sb.append(e.timestampMillis).append('|')
                .append(e.level.name).append('|')
                .append(e.tag).append('|')
                .append(e.message.replace("|", "\\p").replace("\n", "\\n"))
                .append('\n')
        }
        prefs.edit().putString(KEY_BUFFER, sb.toString()).apply()
    }

    private fun sanitize(msg: String): String =
        if (msg.length > MAX_MESSAGE_CHARS) msg.substring(0, MAX_MESSAGE_CHARS) + "…" else msg

    companion object {
        private const val PREFS_NAME = "pushgateway_exporter_reliability_log"
        private const val KEY_BUFFER = "entries"
        private const val MAX_ENTRIES = 500
        private const val MAX_MESSAGE_CHARS = 1000
    }
}
