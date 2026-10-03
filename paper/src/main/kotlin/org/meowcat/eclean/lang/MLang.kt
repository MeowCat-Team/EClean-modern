package org.meowcat.eclean.lang

import org.bukkit.command.CommandSender
import org.meowcat.eclean.config.Config

/**
 * Paper-side thin delegate to the common [LanguageManager].
 *
 * All language loading, migration, merging and caching now lives in common;
 * this object only keeps existing call sites working.
 */
object MLang {
    @Volatile
    private var backend: LanguageManager? = null

    fun duration(seconds: Long): String {
        val value = seconds.coerceAtLeast(0)
        return get("common.duration", "hours" to value / 3600, "minutes" to value % 3600 / 60, "seconds" to value % 60)
    }

    fun bind(manager: LanguageManager) {
        backend = manager
    }

    operator fun get(key: String, vararg placeholder: Pair<String, Any?>): String =
        backend?.get(key, *placeholder) ?: key

    /** Returns null instead of the key itself when the key is missing. */
    fun getOrNull(key: String, vararg placeholder: Pair<String, Any?>): String? =
        backend?.getOrNull(key, *placeholder)

    fun load(sender: CommandSender? = null) {
        val language = Config.current.global.language.ifBlank { "zh_cn" }
        backend?.load(language)
    }

    fun reload(sender: CommandSender? = null) = load(sender)

    fun put(key: String, value: String) {
        backend?.put(key, value)
    }
}