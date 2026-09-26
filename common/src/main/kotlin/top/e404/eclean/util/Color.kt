package top.e404.eclean.util

/**
 * Renders an integer with a MiniMessage color tag based on magnitude.
 */
fun Int.withColor(): RichText = RichText(when {
    this > 60 -> "<red>$this</red>"
    this > 30 -> "<yellow>$this</yellow>"
    else -> "<green>$this</green>"
})
