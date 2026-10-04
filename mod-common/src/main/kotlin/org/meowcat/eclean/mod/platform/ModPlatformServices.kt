package org.meowcat.eclean.mod.platform

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

class ModPermissionService(
    private val server: MinecraftServer,
    private val hierarchyResolved: Boolean = false,
    private val lookup: (CommandSourceStack, String) -> Boolean? = { _, _ -> null },
) : PermissionService {
    private val defaults = ConcurrentHashMap<String, Boolean>()

    override fun hasPermission(playerId: String, node: String): Boolean =
        player(playerId)?.let { hasPermission(it, node) } ?: false

    fun hasPermission(player: ServerPlayer, node: String): Boolean =
        hasPermission(player.createCommandSourceStack(), node)

    fun hasPermission(source: CommandSourceStack, node: String): Boolean {
        return modPermissionAllowed(node, { key ->
            defaults.getOrDefault(key.lowercase(Locale.ROOT), true) &&
                source.permissions().hasPermission(VanillaPermissions.COMMANDS_GAMEMASTER)
        }, hierarchyResolved) { lookup(source, it) }
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
internal fun modPermissionAllowed(
    node: String,
    default: (String) -> Boolean,
    hierarchyResolved: Boolean = false,
    lookup: (String) -> Boolean?,
): Boolean {
    val canonical = PermissionNode.entries.firstOrNull { permission ->
        permission.node.equals(node, ignoreCase = true) || permission.aliases.any { it.equals(node, ignoreCase = true) }
    }
    // NeoForge Boolean handlers return a final canonical decision, including their inheritance.
    if (hierarchyResolved) {
        val key = canonical?.node ?: node
        return lookup(key) ?: default(key)
    }
    val candidates = canonical?.let { listOf(it.node) + it.parents + it.aliases } ?: listOf(node)
    val grants = candidates.map(lookup)
    if (grants.any { it == false }) return false
    if (grants.any { it == true } || lookup("eclean.admin") == true) return true
    return default(canonical?.node ?: node)
}

class ModCommandSender(val native: CommandSourceStack, private val permissions: ModPermissionService) : CommonCommandSender {
    override val name: String get() = native.textName
    override fun hasPermission(node: String): Boolean = permissions.hasPermission(native, node)
    override fun sendMessage(component: Component) = native.server.onThread {
        native.sendSystemMessage(ModText.native(component, native.server))
    }
}

class ModPlayer(val native: ServerPlayer, private val permissions: ModPermissionService) : CommonPlayer {
    override val name: String get() = native.name.string
    override val uniqueId: String get() = native.uuid.toString()
    override val worldName: String get() = ModWorldAccess.worldName(native.level())
    override val location: CommonLocation get() = native.commonLocation()
    override fun hasPermission(node: String): Boolean = permissions.hasPermission(native, node)
    override fun sendMessage(component: Component) = native.level().server.onThread {
        if (!native.isRemoved && !native.hasDisconnected()) {
            native.sendSystemMessage(ModText.native(component, native.level().server))
        }
    }
}

class ModPlayerProvider(
    private val server: MinecraftServer,
    private val permissions: ModPermissionService = ModPermissionService(server),
) : PlayerProvider {
    override fun onlinePlayers(): List<CommonPlayer> = server.playerList.players.map { ModPlayer(it, permissions) }
}

class ModServerInfo(private val server: MinecraftServer) : ServerInfo {
    override val worldNames: List<String> get() = server.allLevels.map(ModWorldAccess::worldName)
    override val onlinePlayerIds: List<String> get() = server.playerList.players.map { it.uuid.toString() }
    override val onlinePlayerCount: Int get() = server.playerList.playerCount
}

class ModMessageSender(private val server: MinecraftServer) : MessageSender {
    override fun sendPlayer(playerId: String, component: Component) = server.onThread {
        val id = runCatching { UUID.fromString(playerId) }.getOrNull() ?: return@onThread
        server.playerList.getPlayer(id)?.sendSystemMessage(ModText.native(component, server))
    }

    override fun sendConsole(component: Component) = server.onThread {
        server.sendSystemMessage(ModText.native(component, server))
    }

    override fun sendCommandSender(senderId: String, component: Component) {
        if (runCatching { UUID.fromString(senderId) }.isSuccess) sendPlayer(senderId, component)
        else sendConsole(component)
    }

    override fun broadcast(component: Component) = server.onThread {
        val message = ModText.native(component, server)
        server.sendSystemMessage(message)
        server.playerList.players.forEach { it.sendSystemMessage(message) }
    }
}

private fun MinecraftServer.onThread(work: () -> Unit) {
    if (isSameThread) work() else execute(work)
}
