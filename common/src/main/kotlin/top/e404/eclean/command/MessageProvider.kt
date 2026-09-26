package top.e404.eclean.command

interface MessageProvider {
    fun entityName(type: String): Any = type
    fun itemName(type: String): Any = type
    fun get(key: String, vararg args: Pair<String, Any>): String
}
fun MessageProvider.duration(seconds: Long): String {
    val value = seconds.coerceAtLeast(0)
    return get("common.duration", "hours" to value / 3600, "minutes" to value % 3600 / 60, "seconds" to value % 60)
}
