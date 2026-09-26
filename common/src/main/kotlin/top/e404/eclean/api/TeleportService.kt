package top.e404.eclean.common.api

/**
 * Platform-agnostic player teleport service.
 */
interface TeleportService {
    fun teleport(player: top.e404.eclean.common.api.CommonPlayer, target: top.e404.eclean.common.api.CommonLocation): java.util.concurrent.CompletableFuture<Boolean>
}
