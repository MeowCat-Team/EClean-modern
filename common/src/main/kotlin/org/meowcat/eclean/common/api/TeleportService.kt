package org.meowcat.eclean.common.api

/**
 * Platform-agnostic player teleport service.
 */
interface TeleportService {
    fun teleport(player: org.meowcat.eclean.common.api.CommonPlayer, target: org.meowcat.eclean.common.api.CommonLocation): java.util.concurrent.CompletableFuture<Boolean>
}
