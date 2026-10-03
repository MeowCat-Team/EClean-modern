package org.meowcat.eclean.feature.cleanup.chunk

import org.meowcat.eclean.common.api.MessageSender
import org.meowcat.eclean.common.api.PermissionService
import org.meowcat.eclean.common.api.ServerInfo
import org.meowcat.eclean.util.miniMessage
import org.meowcat.eclean.util.placeholder

/**
 * Platform-agnostic dense-entity alert broadcaster.
 */
class ChunkAlertService(
    private val messageSender: MessageSender,
    private val serverInfo: ServerInfo,
    private val permissionService: PermissionService,
    private val prefixProvider: () -> String,
    private val alertFormatProvider: () -> String?,
) {
    fun alert(entries: List<ChunkDensityEntry>) {
        val format = alertFormatProvider() ?: return
        if (format.isBlank()) return
        val receivers = serverInfo.onlinePlayerIds
            .filter { permissionService.hasPermission(it, "eclean.alerts") }
        entries.forEach { entry ->
            val message = format.placeholder(
                "chunk" to "x: ${entry.chunk.x * 16}..${entry.chunk.x * 16 + 15}, z: ${entry.chunk.z * 16}..${entry.chunk.z * 16 + 15}",
                "entity" to entry.entityType,
                "count" to entry.amount,
            )
            val component = miniMessage.deserialize("${prefixProvider()} $message")
            receivers.forEach { messageSender.sendPlayer(it, component) }
        }
    }
}
