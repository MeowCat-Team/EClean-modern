package top.e404.eclean.app

import net.kyori.adventure.audience.Audience
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import top.e404.eclean.command.PermissionNode
import top.e404.eclean.command.hasPermission
import top.e404.eclean.lang.MLang
import top.e404.eclean.util.miniMessage
import java.util.logging.Level

class MessageService {
    val debuggers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val miniMessageTagRegex = Regex("<[^>]+>")
    private var lastDebugText: String? = null
    private var lastDebugTime: Long = 0
    private var suppressedDebugCount: Int = 0

    val debugPrefix: String get() = MLang["debug_prefix"]
    val prefix: String get() = MLang["prefix"]

    fun send(sender: CommandSender, message: String) {
        val full = "$prefix $message"
        try {
            val component = miniMessage.deserialize(full)
            (sender as? Audience)?.sendMessage(component) ?: sender.sendMessage(stripMiniMessage(full))
        } catch (_: Exception) {
            sender.sendMessage(stripMiniMessage(full))
        }
    }

    fun broadcast(message: String) {
        val full = "$prefix $message"
        try {
            val component = miniMessage.deserialize(full)
            Bukkit.getServer().sendMessage(component)
        } catch (_: Exception) {
            Bukkit.getServer().broadcast(miniMessage.deserialize(stripMiniMessage(full)))
        }
    }

    fun debug(msg: () -> String) {
        if (!hasDebuggers && !plugin.debug) return
        emitDebug(msg())
    }

    fun buildDebug(block: StringBuilder.() -> Unit) {
        if (!hasDebuggers && !plugin.debug) return
        emitDebug(buildString(block))
    }

    fun info(message: String) {
        plugin.logger.info(message)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        if (throwable == null) plugin.logger.log(Level.WARNING, message)
        else plugin.logger.log(Level.WARNING, message, throwable)
    }

    private val hasDebuggers: Boolean
        get() = debuggers.isNotEmpty()

    fun toggleDebugger(playerId: String): Boolean {
        return if (playerId in debuggers) {
            debuggers.remove(playerId)
            false
        } else {
            debuggers.add(playerId)
            true
        }
    }

    @Synchronized private fun emitDebug(text: String) {
        val now = System.currentTimeMillis()
        val cooldown = top.e404.eclean.config.Config.current.global.debugCooldownMillis
        if (text == lastDebugText && now - lastDebugTime < cooldown) {
            suppressedDebugCount++
            return
        }
        val display = if (suppressedDebugCount > 0) "$text (x${suppressedDebugCount + 1})" else text
        if (plugin.debug) {
            plugin.logger.info(stripMiniMessage("$debugPrefix $display"))
        }
        debuggers.forEach { name ->
            val player = Bukkit.getPlayer(name)
            if (player == null) debuggers.remove(name)
            else top.e404.eclean.platform.Schedulers.runForEntity(player) {
                if (player.hasPermission(PermissionNode.DEBUG)) player.sendMessage("$debugPrefix $display")
                else debuggers.remove(name)
            }
        }
        lastDebugText = text
        lastDebugTime = now
        suppressedDebugCount = 0
    }

    private fun stripMiniMessage(text: String): String =
        text.replace(miniMessageTagRegex, "")

    private object plugin {
        val debug: Boolean get() = top.e404.eclean.config.Config.current.global.debug
        val logger get() = top.e404.eclean.PL.logger
    }
}
