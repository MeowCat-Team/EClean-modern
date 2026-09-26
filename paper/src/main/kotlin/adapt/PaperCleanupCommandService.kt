package top.e404.eclean.paper.adapt

import org.bukkit.Bukkit
import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.feature.cleanup.CleanupCommandService
import top.e404.eclean.feature.cleanup.CleanupContext
import top.e404.eclean.feature.cleanup.AuditedDenseCleanup
import top.e404.eclean.feature.cleanup.AuditedLivingCleanup
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import top.e404.eclean.lang.MLang
import top.e404.eclean.config.Config
import top.e404.eclean.util.miniMessage

class PaperCleanupCommandService(
    private val environment: top.e404.eclean.feature.cleanup.CleanupEnvironment,
    private val coordinator: () -> top.e404.eclean.feature.cleanup.CleanupCoordinator,
) : CleanupCommandService {
    override fun worldExists(world: String) = Bukkit.getWorld(world) != null
    private fun context(sender: CommonCommandSender) = CleanupContext("command", sender.name)
    private fun send(sender: CommonCommandSender, key: String, vararg args: Pair<String, Any>) =
        sender.sendMessage(miniMessage.deserialize("${MLang["prefix"]} ${MLang.get(key, *args)}"))

    override fun cleanAll(sender: CommonCommandSender, dryRun: Boolean) {
        val worlds = Bukkit.getWorlds().map { it.name }
        coordinator().cleanNow(dryRun = dryRun, context = context(sender)) { result ->
            if (dryRun || (!Config.current.cleanup.broadcastWhenNoPlayers && Bukkit.getOnlinePlayers().isEmpty())) sender.sendMessage(miniMessage.deserialize("${MLang["prefix"]} " +
                top.e404.eclean.feature.cleanup.cleanupSummaryMessage(result, worlds, dryRun)))
        }
    }
    override fun cleanAllInWorld(sender: CommonCommandSender, world: String, dryRun: Boolean) {
        coordinator().cleanNow(dryRun = dryRun, worldName = world, context = context(sender)) { result ->
            if (dryRun || (!Config.current.cleanup.broadcastWhenNoPlayers && Bukkit.getOnlinePlayers().isEmpty())) sender.sendMessage(miniMessage.deserialize("${MLang["prefix"]} " +
                top.e404.eclean.feature.cleanup.cleanupSummaryMessage(result, listOf(world), dryRun)))
        }
    }
    private fun result(sender: CommonCommandSender, dryRun: Boolean, cleaned: Int, failed: Int, skipped: Int, incomplete: Boolean) {
        send(sender, if (dryRun) "command.clean_preview_result" else "command.clean_result",
            "cleaned" to cleaned, "failed" to failed, "skipped" to skipped,
            "status" to top.e404.eclean.util.RichText(MLang[if (incomplete) "common.incomplete" else "common.complete"]))
    }
    override fun cleanEntity(sender: CommonCommandSender, world: String?, dryRun: Boolean) {
        val service = AuditedLivingCleanup(context(sender), environment.common())
        if (world == null) service.cleanAllWorlds(dryRun) { entries ->
            result(sender, dryRun, entries.sumOf { it.cleaned }, entries.sumOf { it.failed }, entries.sumOf { it.skippedChunks }, entries.any { it.incomplete })
        } else service.cleanWorld(world, dryRun) { result(sender, dryRun, it.cleaned, it.failed, it.skippedChunks, it.incomplete) }
    }
    override fun cleanDrop(sender: CommonCommandSender, world: String?, dryRun: Boolean) {
        val service = DropCleanupService(context(sender), environment = environment)
        if (world == null) service.cleanAllWorlds(dryRun) { entries ->
            result(sender, dryRun, entries.sumOf { it.cleaned }, entries.sumOf { it.failed }, entries.sumOf { it.skippedChunks }, entries.any { it.incomplete })
        } else service.cleanWorld(world, dryRun) { result(sender, dryRun, it.cleaned, it.failed, it.skippedChunks, it.incomplete) }
    }
    override fun cleanChunk(sender: CommonCommandSender, world: String?, dryRun: Boolean) {
        val service = AuditedDenseCleanup(context(sender), environment.common())
        if (world == null) service.cleanAllWorlds(dryRun) { result(sender, dryRun, it.cleaned, it.failed, it.skippedChunks, it.incomplete) }
        else service.cleanWorld(world, dryRun = dryRun) { result(sender, dryRun, it.cleaned, it.failed, it.skippedChunks, it.incomplete) }
    }
    override fun cleanTrash(sender: CommonCommandSender, dryRun: Boolean) {
        if (dryRun) send(sender, "command.trash_preview", "count" to environment.trashStore.totalCount())
        else {
            val started = context(sender)
            val count = environment.trashcan.clearAll()
            environment.audit.publish(top.e404.eclean.feature.cleanup.CleanupRecord(
                System.currentTimeMillis(), null, 0, 0, 0, kind = "trash", context = started,
                configRevision = environment.config().revision, trashItems = count,
            ))
            send(sender, "command.trash_cleared", "count" to count)
        }
    }
}
