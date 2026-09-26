package top.e404.eclean.ui

import top.e404.eclean.config.model.MenuAdvancedConfig

/** Bundled menus use gold for headings, gray for detail, and yellow for actions. */
fun menuText(text: String, theme: MenuAdvancedConfig): String {
    val colors = mapOf("gold" to theme.primaryColor, "gray" to theme.secondaryColor, "yellow" to theme.accentColor)
    return Regex("<(/?)(gold|gray|yellow)>").replace(text) { match ->
        val tag = colors.getValue(match.groupValues[2]).removeSurrounding("<", ">")
        "<${match.groupValues[1]}$tag>"
    }
}
