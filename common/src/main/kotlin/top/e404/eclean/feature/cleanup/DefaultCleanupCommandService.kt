package top.e404.eclean.feature.cleanup

import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.common.api.ServerInfo
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.util.RichText
import top.e404.eclean.util.miniMessage

/** Command behavior shared by loaders; native drop recovery and batch dispatch are injected. */
class DefaultCleanupCommandService(
    private val environment: CleanupRuntimeEnvironment,
    private val serverInfo: ServerInfo,
    private val worldAccess: WorldAccess,
    private val worldExists: (String) -> Boolean,
    private val cleanBatch: (Boolean, String?, CleanupContext, (CleanupSummary) -> Unit) -> Unit,
    private val dropService: (CleanupContext) -> DropCleanupOperations,
    private val trashCount: () -> Long,
    private val clearTrash: () -> Long,
    private val translate: (String, List<Pair<String, Any?>>) -> String,
) : CleanupCommandService {
    override fun worldExists(world: String) = worldExists.invoke(world)

    private fun context(sender: CommonCommandSender) = CleanupContext("command", sender.name)
    private fun text(key: String, vararg args: Pair<String, Any?>) = translate(key, args.toList())
    private fun send(sender: CommonCommandSender, message: String) =
        sender.sendMessage(miniMessage.deserialize("${text("prefix")} $message"))
    private fun sendKey(sender: CommonCommandSender, key: String, vararg args: Pair<String, Any?>) =
        send(sender, text(key, *args))

    override fun cleanAll(sender: CommonCommandSender, dryRun: Boolean) {
        val worlds = worldAccess.worldNames()
        cleanBatch(dryRun, null, context(sender)) { result ->
            if (dryRun || (!environment.config().cleanup.broadcastWhenNoPlayers && !serverInfo.hasOnlinePlayers)) {
                send(sender, formatCleanupSummary(result, worlds, dryRun, translate))
            }
        }
    }

    override fun cleanAllInWorld(sender: CommonCommandSender, world: String, dryRun: Boolean) {
        cleanBatch(dryRun, world, context(sender)) { result ->
            if (dryRun || (!environment.config().cleanup.broadcastWhenNoPlayers && !serverInfo.hasOnlinePlayers)) {
                send(sender, formatCleanupSummary(result, listOf(world), dryRun, translate))
            }
        }
    }

    private fun result(sender: CommonCommandSender, dryRun: Boolean, cleaned: Int, failed: Int, skipped: Int, incomplete: Boolean) {
        sendKey(sender, if (dryRun) "command.clean_preview_result" else "command.clean_result",
            "cleaned" to cleaned, "failed" to failed, "skipped" to skipped,
            "status" to RichText(text(if (incomplete) "common.incomplete" else "common.complete")))
    }

    override fun cleanEntity(sender: CommonCommandSender, world: String?, dryRun: Boolean) {
        val service = AuditedLivingCleanup(context(sender), environment)
        if (world == null) service.cleanAllWorlds(dryRun) { entries ->
            result(sender, dryRun, entries.sumOf { it.cleaned }, entries.sumOf { it.failed },
                entries.sumOf { it.skippedChunks }, entries.any { it.incomplete })
        } else service.cleanWorld(world, dryRun) { result(sender, dryRun, it.cleaned, it.failed, it.skippedChunks, it.incomplete) }
    }

    override fun cleanDrop(sender: CommonCommandSender, world: String?, dryRun: Boolean) {
        val service = dropService(context(sender))
        if (world == null) service.cleanAllWorlds(dryRun) { entries ->
            result(sender, dryRun, entries.sumOf { it.cleaned }, entries.sumOf { it.failed },
                entries.sumOf { it.skippedChunks }, entries.any { it.incomplete })
        } else service.cleanWorld(world, dryRun) { result(sender, dryRun, it.cleaned, it.failed, it.skippedChunks, it.incomplete) }
    }

    override fun cleanChunk(sender: CommonCommandSender, world: String?, dryRun: Boolean) {
        val service = AuditedDenseCleanup(context(sender), environment)
        if (world == null) service.cleanAllWorlds(dryRun) { result(sender, dryRun, it.cleaned, it.failed, it.skippedChunks, it.incomplete) }
        else service.cleanWorld(world, dryRun) { result(sender, dryRun, it.cleaned, it.failed, it.skippedChunks, it.incomplete) }
    }

    override fun cleanTrash(sender: CommonCommandSender, dryRun: Boolean) {
        if (dryRun) sendKey(sender, "command.trash_preview", "count" to trashCount())
        else {
            val started = context(sender)
            val count = clearTrash()
            environment.audit.publish(CleanupRecord(
                System.currentTimeMillis(), null, 0, 0, 0, kind = "trash", context = started,
                configRevision = environment.config().revision, trashItems = count,
            ))
            sendKey(sender, "command.trash_cleared", "count" to count)
        }
    }
}
