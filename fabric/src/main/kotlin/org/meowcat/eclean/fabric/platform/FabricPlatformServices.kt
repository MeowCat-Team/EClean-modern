package org.meowcat.eclean.fabric.platform

import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner
import net.fabricmc.fabric.api.util.TriState
import net.kyori.adventure.text.Component
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions as VanillaPermissions
import org.meowcat.eclean.command.PermissionNode
import org.meowcat.eclean.common.api.CommonCommandSender
import org.meowcat.eclean.common.api.CommonLocation
import org.meowcat.eclean.common.api.CommonPlayer
import org.meowcat.eclean.common.api.MessageSender
import org.meowcat.eclean.common.api.PermissionService
import org.meowcat.eclean.common.api.PlayerProvider
import org.meowcat.eclean.common.api.ServerInfo
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FabricPermissionService(private val server: MinecraftServer) : PermissionService {
    private val defaults = ConcurrentHashMap<String, Boolean>()

    override fun hasPermission(playerId: String, node: String): Boolean =
        player(playerId)?.let { hasPermission(it, node) } ?: false

    fun hasPermission(player: ServerPlayer, node: String): Boolean =
        hasPermission(player.createCommandSourceStack(), node)

    fun hasPermission(source: CommandSourceStack, node: String): Boolean {
        return fabricPermissionAllowed(node, { key ->
            defaults.getOrDefault(key.lowercase(Locale.ROOT), true) &&
                source.permissions().hasPermission(VanillaPermissions.COMMANDS_GAMEMASTER)
        }) { permissionValue(source, it) }
    }

    private fun permissionValue(source: CommandSourceStack, node: String): TriState {
        val owner = source as PermissionContextOwner
        return owner.checkPermission(identifier(node))
    }

    override fun registerPermission(node: String, default: Boolean, description: String) {
        defaults.putIfAbsent(node.lowercase(Locale.ROOT), default)
    }

    fun player(id: String): ServerPlayer? =
        runCatching { UUID.fromString(id) }.getOrNull()?.let(server.playerList::getPlayer)

    companion object {
        /** Fabric permissions are resource identifiers; eclean.command.foo becomes eclean:command.foo. */
        fun identifier(node: String): Identifier {
            val key = node.lowercase(Locale.ROOT)
            return if (':' in key) Identifier.parse(key)
            else Identifier.fromNamespaceAndPath(key.substringBefore('.'), key.substringAfter('.', "use"))
        }
    }
}

/** Native permission tri-states are explicit grants/denials; undefined values use operator defaults. */
internal fun fabricPermissionAllowed(
    node: String,
    default: (String) -> Boolean,
    lookup: (String) -> TriState,
): Boolean {
    val canonical = PermissionNode.entries.firstOrNull { permission ->
        permission.node.equals(node, ignoreCase = true) || permission.aliases.any { it.equals(node, ignoreCase = true) }
    }
    val candidates = canonical?.let { listOf(it.node) + it.parents + it.aliases } ?: listOf(node)
    val grants = candidates.map(lookup)
    if (grants.any { it == TriState.FALSE }) return false
    if (grants.any { it == TriState.TRUE } || lookup("eclean.admin") == TriState.TRUE) return true
    return default(canonical?.node ?: node)
}

class FabricCommandSender(val native: CommandSourceStack, private val permissions: FabricPermissionService) : CommonCommandSender {
    override val name: String get() = native.textName
    override fun hasPermission(node: String): Boolean = permissions.hasPermission(native, node)
    override fun sendMessage(component: Component) = native.server.onThread {
        native.sendSystemMessage(FabricText.native(component, native.server))
    }
}

class FabricPlayer(val native: ServerPlayer, private val permissions: FabricPermissionService) : CommonPlayer {
    override val name: String get() = native.name.string
    override val uniqueId: String get() = native.uuid.toString()
    override val worldName: String get() = FabricWorldAccess.worldName(native.level())
    override val location: CommonLocation get() = native.commonLocation()
    override fun hasPermission(node: String): Boolean = permissions.hasPermission(native, node)
    override fun sendMessage(component: Component) = native.level().server.onThread {
        if (!native.isRemoved && !native.hasDisconnected()) {
            native.sendSystemMessage(FabricText.native(component, native.level().server))
        }
    }
}

class FabricPlayerProvider(
    private val server: MinecraftServer,
    private val permissions: FabricPermissionService = FabricPermissionService(server),
) : PlayerProvider {
    override fun onlinePlayers(): List<CommonPlayer> = server.playerList.players.map { FabricPlayer(it, permissions) }
}

class FabricServerInfo(private val server: MinecraftServer) : ServerInfo {
    override val worldNames: List<String> get() = server.allLevels.map(FabricWorldAccess::worldName)
    override val onlinePlayerIds: List<String> get() = server.playerList.players.map { it.uuid.toString() }
    override val onlinePlayerCount: Int get() = server.playerList.playerCount
}

class FabricMessageSender(private val server: MinecraftServer) : MessageSender {
    override fun sendPlayer(playerId: String, component: Component) = server.onThread {
        val id = runCatching { UUID.fromString(playerId) }.getOrNull() ?: return@onThread
        server.playerList.getPlayer(id)?.sendSystemMessage(FabricText.native(component, server))
    }

    override fun sendConsole(component: Component) = server.onThread {
        server.sendSystemMessage(FabricText.native(component, server))
    }

    override fun sendCommandSender(senderId: String, component: Component) {
        if (runCatching { UUID.fromString(senderId) }.isSuccess) sendPlayer(senderId, component)
        else sendConsole(component)
    }

    override fun broadcast(component: Component) = server.onThread {
        val message = FabricText.native(component, server)
        server.sendSystemMessage(message)
        server.playerList.players.forEach { it.sendSystemMessage(message) }
    }
}

private fun MinecraftServer.onThread(work: () -> Unit) {
    if (isSameThread) work() else execute(work)
}
