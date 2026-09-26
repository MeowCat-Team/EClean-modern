package top.e404.eclean.feature.cleanup

import top.e404.eclean.util.RichText

fun formatCleanupSummary(
    result: CleanupSummary,
    worlds: List<String>,
    dryRun: Boolean = false,
    translate: (String, List<Pair<String, Any?>>) -> String,
): String {
    fun text(key: String, vararg args: Pair<String, Any?>) = translate(key, args.toList())
    val scope: Any = if (worlds.size == 1) worlds.single()
        else RichText(text("cleanup.scope.worlds", "count" to worlds.size))
    val message = text(if (dryRun) "cleanup.summary.preview" else "cleanup.summary.done",
        "scope" to scope, "drop" to result.drops, "living" to result.living, "dense" to result.dense)
    return if (result.incomplete || result.failed > 0 || result.skipped > 0) {
        message + "\n" + text("cleanup.summary.partial", "failed" to result.failed, "skipped" to result.skipped)
    } else message
}
