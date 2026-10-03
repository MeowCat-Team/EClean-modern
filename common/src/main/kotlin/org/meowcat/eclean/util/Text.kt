package org.meowcat.eclean.util

import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.Locale

/** Only internally constructed, trusted MiniMessage may opt into formatted substitution. */
data class RichText(val markup: String)
fun String.richText() = RichText(this)

fun commandLink(content: String, command: String, hover: String): String = miniMessage.serialize(
    miniMessage.deserialize(content)
        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
        .hoverEvent(miniMessage.deserialize(hover))
)

val miniMessage: MiniMessage = MiniMessage.miniMessage()

private val constRegex = Regex("[.\\s\\-_]+")

/** Native registry identifiers retain legal dots/hyphens instead of turning into a different type. */
fun String.formatAsConst(): String =
    (if (':' in this) trim() else replace(constRegex, "_")).uppercase(Locale.ROOT)

fun String.placeholder(vararg placeholder: Pair<String, Any?>): String =
    placeholder(mapOf(*placeholder))

fun String.placeholder(placeholder: Map<String, Any?>): String {
    // Single pass: an argument containing {other} must never become another placeholder.
    return Regex("\\{([A-Za-z0-9_]+)\\}").replace(this) { match ->
        val key = match.groupValues[1]
        if (!placeholder.containsKey(key)) match.value else when (val value = placeholder[key]) {
            is RichText -> value.markup
            else -> miniMessage.escapeTags(value.toString())
        }
    }
}
