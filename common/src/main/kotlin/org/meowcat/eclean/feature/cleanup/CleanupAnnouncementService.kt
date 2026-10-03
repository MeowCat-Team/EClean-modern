package org.meowcat.eclean.feature.cleanup

import org.meowcat.eclean.common.api.MessageSender
import org.meowcat.eclean.common.api.ServerInfo
import org.meowcat.eclean.util.miniMessage

/**
 * Broadcasts cleanup countdown and finish messages.
 *
 * This is platform-agnostic: all configuration values are supplied as
 * functions by the loader so common does not depend on a paper-specific
 * config singleton.
 */
class CleanupAnnouncementService(
    private val messageSender: MessageSender,
    private val serverInfo: ServerInfo,
    private val prefixProvider: () -> String,
    private val countdownMessageProvider: (Long) -> String?,
    private val shouldBroadcastWhenNoPlayers: () -> Boolean,
) {
    fun announceCountdown(remainingSeconds: Long) {
        val message = countdownMessageProvider(remainingSeconds) ?: return
        if (!serverInfo.hasOnlinePlayers && !shouldBroadcastWhenNoPlayers()) return
        messageSender.broadcast(miniMessage.deserialize("${prefixProvider()} $message"))
    }

    fun announceFinish(message: String) {
        if (message.isBlank()) return
        if (!serverInfo.hasOnlinePlayers && !shouldBroadcastWhenNoPlayers()) return
        messageSender.broadcast(miniMessage.deserialize("${prefixProvider()} $message"))
    }
}
