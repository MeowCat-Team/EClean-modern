package org.meowcat.eclean.fabric.platform

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.meowcat.eclean.common.api.CommonLocation
import org.meowcat.eclean.common.api.CommonPlayer
import org.meowcat.eclean.common.api.TeleportService
import java.util.UUID
import java.util.concurrent.CompletableFuture

class FabricTeleportService(private val server: MinecraftServer, private val scheduler: FabricScheduler) : TeleportService {
    override fun teleport(player: CommonPlayer, target: CommonLocation): CompletableFuture<Boolean> {
        val result = CompletableFuture<Boolean>()
        scheduler.submitGlobal {
            val id = runCatching { UUID.fromString(player.uniqueId) }.getOrNull()
            val native = id?.let(server.playerList::getPlayer)
            if (native == null) result.complete(false)
            else teleportNow(native, target, native.yRot, native.xRot, result)
        }.whenComplete { _, failure -> if (failure != null) result.completeExceptionally(failure) }
        return result
    }

    fun teleport(
        player: ServerPlayer,
        target: CommonLocation,
        yaw: Float = player.yRot,
        pitch: Float = player.xRot,
    ): CompletableFuture<Boolean> {
        val result = CompletableFuture<Boolean>()
        scheduler.submitGlobal { teleportNow(player, target, yaw, pitch, result) }
            .whenComplete { _, failure -> if (failure != null) result.completeExceptionally(failure) }
        return result
    }

    private fun teleportNow(
        player: ServerPlayer,
        target: CommonLocation,
        yaw: Float,
        pitch: Float,
        result: CompletableFuture<Boolean>,
    ) {
        if (player.isRemoved || player.hasDisconnected() || !target.x.isFinite() || !target.y.isFinite() ||
            !target.z.isFinite() || !yaw.isFinite() || !pitch.isFinite()) {
            result.complete(false)
            return
        }
        val destination = server.allLevels.firstOrNull { FabricWorldAccess.worldName(it) == target.worldName }
        if (destination == null) { result.complete(false); return }
        result.complete(player.teleportTo(destination, target.x, target.y, target.z, emptySet(), yaw, pitch, true))
    }
}
