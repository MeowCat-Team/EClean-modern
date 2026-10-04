package org.meowcat.eclean.neoforge

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permissions as VanillaPermissions
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent
import net.neoforged.neoforge.server.permission.PermissionAPI
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext
import net.neoforged.neoforge.server.permission.nodes.PermissionNode as NativePermissionNode
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes
import org.meowcat.eclean.mod.ModLoaderHooks
import org.meowcat.eclean.mod.ECleanMod
import org.meowcat.eclean.neoforge.mixin.ItemEntityAgeAccessor
import java.nio.file.Path
import java.util.UUID

/** Native nodes are registered before NeoForge initializes the selected permission handler. */
class NeoForgeLoaderHooks(container: ModContainer) : ModLoaderHooks {
    override val loaderName: String = "NeoForge"
    override val configDirectory: Path = FMLPaths.CONFIGDIR.get()
    override val version: String = container.modInfo.version.toString()
    override val resolvesPermissionHierarchy: Boolean = true
    @Volatile private var chatInput: ((ServerPlayer, String) -> Boolean)? = null

    override fun registerChatInput(handler: (ServerPlayer, String) -> Boolean) {
        check(chatInput == null) { "EClean chat input is already registered" }
        chatInput = handler
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onChat(event: ServerChatEvent) {
        // Architectury RECEIVED exposes the decorated component; search needs the original text.
        if (chatInput?.invoke(event.player, event.rawText) == false) event.isCanceled = true
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onItemExpire(event: ItemExpireEvent) {
        val item = event.entity
        if (item.isRemoved || item.item.isEmpty || item.level().isClientSide()) return
        // NeoForge invokes this before applying other listeners' extensions. Respect an actual
        // extension, including custom item lifespans, rather than assuming vanilla's 6000 ticks.
        if (NeoForgeExpiryPolicy.isExtended(item.age, item.lifespan, event.extraLife)) return
        if (!ECleanMod.tryRecoverDespawn(item) || item.isRemoved) return
        val retry = NeoForgeExpiryPolicy.retryNextTick(item.age, item.lifespan)
        if (retry.age != item.age) (item as ItemEntityAgeAccessor).ecleanSetAge(retry.age)
        event.extraLife = retry.extraLife
    }

    private val nodes: Map<String, NativePermissionNode<Boolean>> =
        NeoForgePermissionDefinitions.inherited.keys.associateWith { name ->
            NativePermissionNode(
                "eclean", name.removePrefix("eclean."), PermissionTypes.BOOLEAN,
                NativePermissionNode.PermissionResolver<Boolean> { player, id, contexts ->
                    defaultPermission(name, player, id, contexts)
                },
            ).apply {
                setInformation(Component.literal(name), Component.literal("EClean command permission"))
            }
        }

    @SubscribeEvent
    fun gatherPermissions(event: PermissionGatherEvent.Nodes) {
        event.addNodes(nodes.values)
    }

    override fun permission(source: CommandSourceStack, node: String): Boolean? {
        val player = source.player ?: return null
        val registered = nodes[NeoForgePermissionDefinitions.normalize(node)] ?: return null
        if (PermissionAPI.getActivePermissionHandler() == null) return null
        // A provider's explicit false is final; shared aliases and OP defaults must not override it.
        if (registered !in PermissionAPI.getRegisteredNodes()) return false
        return PermissionAPI.getPermission(player, registered)
    }

    private fun defaultPermission(
        node: String,
        player: ServerPlayer?,
        playerId: UUID,
        contexts: Array<out PermissionDynamicContext<*>>,
    ): Boolean = NeoForgePermissionDefinitions.resolveDefault(
        node,
        player?.createCommandSourceStack()?.permissions()
            ?.hasPermission(VanillaPermissions.COMMANDS_GAMEMASTER) == true,
    ) { inherited ->
        val permission = checkNotNull(nodes[inherited]) { "Unknown inherited EClean permission: $inherited" }
        if (player != null) PermissionAPI.getPermission(player, permission, *contexts)
        else PermissionAPI.getOfflinePermission(playerId, permission, *contexts)
    }
}
