package top.e404.eclean.lang

/** Upgrade exact older bundled wording while retaining server-customized translations. */
internal object DefaultMessageMigrations {
    private val previous = mapOf(
        "cleanup.countdown.60" to setOf("<white>Cleanup will start in 1 minute</white>", "<white>将在1分钟后进行清理</white>"),
        "cleanup.countdown.30" to setOf("<white>Cleanup will start in 30 seconds</white>", "<white>将在30秒后进行清理</white>"),
        "cleanup.countdown.10" to setOf("<white>Cleanup will start in 10 seconds</white>", "<white>将在10秒后进行清理</white>"),
        "cleanup.countdown.0" to setOf("<white>Cleaning in progress</white>", "<white>正在清理</white>"),
        "command.clean_result" to setOf("<white>Removed {cleaned} entities; failed removals: {failed}; skipped chunks: {skipped}; status: {status}.</white>", "<white>已删除 {cleaned} 个实体；删除失败 {failed} 个；跳过 {skipped} 个区块；状态：{status}。</white>"),
        "command.clean_preview_result" to setOf("<white>Preview selected {cleaned} entities; failures: {failed}; skipped chunks: {skipped}; status: {status}. Nothing was removed.</white>", "<white>预演选中 {cleaned} 个实体；失败 {failed}；跳过 {skipped} 个区块；状态：{status}。未执行删除。</white>"),
        "menu.trashcan.title" to setOf("<gold>Shared trash · Entries expire · Lost on restart</gold>", "<gold>共享垃圾桶 · 逐条过期 · 重启丢失</gold>"),
    )

    fun upgrade(key: String, value: String, fallback: String?): String =
        if (fallback != null && value in previous[key].orEmpty()) fallback else value
}
