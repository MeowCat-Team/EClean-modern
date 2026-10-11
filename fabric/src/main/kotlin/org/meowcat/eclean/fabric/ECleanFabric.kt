package org.meowcat.eclean.fabric

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.permission.v1.PermissionContextOwner
import net.fabricmc.fabric.api.util.TriState
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import org.meowcat.eclean.mod.ECleanMod
import org.meowcat.eclean.mod.ModLoaderHooks
import org.meowcat.eclean.mod.platform.ModPermissionService

class ECleanFabric : ModInitializer {
    override fun onInitialize() {
        val hooks = object : ModLoaderHooks {
            override val loaderName = "Fabric"
            override val configDirectory = FabricLoader.getInstance().configDir
            override val version = FabricLoader.getInstance().getModContainer("eclean")
                .orElseThrow().metadata.version.friendlyString

            override fun registerChatInput(handler: (ServerPlayer, String) -> Boolean) {
                ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { message, player, _ ->
                    handler(player, message.signedContent())
                }
            }

            override fun permission(source: CommandSourceStack, node: String): Boolean? =
                when ((source as PermissionContextOwner).checkPermission(ModPermissionService.identifier(node))) {
                    TriState.TRUE -> true
                    TriState.FALSE -> false
                    TriState.DEFAULT -> null
                }
        }
        ECleanMod.initialize(hooks)
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> ECleanMod.registerCommands(dispatcher) }
        ServerLifecycleEvents.SERVER_STARTED.register { ECleanMod.serverStarted(it, hooks) }
        ServerLifecycleEvents.SERVER_STOPPING.register(ECleanMod::serverStopping)
        ServerTickEvents.END_SERVER_TICK.register(ECleanMod::serverPostTick)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> ECleanMod.playerJoined(handler.player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> ECleanMod.playerQuit(handler.player) }
    }
}
