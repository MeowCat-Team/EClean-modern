package top.e404.eclean.config

enum class ConfigSection(val displayName: String) {
    GLOBAL("global"),
    CLEANUP("cleanup"),
    DROP("drop"),
    LIVING("living"),
    CHUNK_DENSITY("chunk-density"),
    TRASHCAN("trashcan"),
    PER_WORLD("per-world"),
    ADVANCED("advanced"),
}

fun ConfigBundle.diff(other: ConfigBundle): Set<ConfigSection> {
    val before = sections()
    val after = other.sections()
    return ConfigSection.entries.filterTo(linkedSetOf()) { before[it] != after[it] }
}
