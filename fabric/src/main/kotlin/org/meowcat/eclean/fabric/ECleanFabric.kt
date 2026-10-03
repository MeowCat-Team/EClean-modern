package org.meowcat.eclean.fabric

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.Commands
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.item.ItemEntity
import org.slf4j.LoggerFactory

/** One runtime per server; the registered callbacks survive an integrated server restart. */
class ECleanFabric : ModInitializer {
    override fun onInitialize() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val root = dispatcher.register(Commands.literal("eclean")
                .executes { active?.commands?.execute(it.source, "") ?: 0 }
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                    .suggests { context, builder ->
                        active?.commands?.suggest(context.source, builder) ?: builder.buildFuture()
                    }
                    .executes { active?.commands?.execute(it.source, StringArgumentType.getString(it, "arguments")) ?: 0 }))
            dispatcher.register(Commands.literal("ecl").redirect(root)
                .executes { active?.commands?.execute(it.source, "") ?: 0 })
        }
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            active = FabricRuntime(server, FabricLoader.getInstance().configDir.resolve("eclean"))
                .also { it.load() }
        }
        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            active?.takeIf { it.server === server }?.shutdown()
            active = null
        }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            active?.takeIf { it.server === server }?.tick()
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            active?.takeIf { it.server === server }?.temporaryReturns?.handleJoin(handler.player)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, server ->
            active?.takeIf { it.server === server }?.let {
                it.temporaryReturns.handleQuit(handler.player)
                it.menus.onDisconnect(handler.player.uuid)
                it.debuggers.remove(handler.player.uuid.toString())
            }
        }
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { message, player, _ ->
            active?.menus?.handleChat(player, message.signedContent()) ?: true
        }
    }

    companion object {
        internal val logger = LoggerFactory.getLogger("EClean")
        @Volatile internal var active: FabricRuntime? = null
            private set

        /** Called only at ItemEntity's natural expiry, on its server tick. */
        @JvmStatic
        fun tryRecoverDespawn(item: ItemEntity): Boolean = active?.recoverDespawn(item) ?: false

        /** Allow configured empty-server cleanup and foreground delayed work to keep ticking. */
        @JvmStatic
        fun shouldKeepTicking(server: MinecraftServer): Boolean =
            active?.takeIf { it.server === server }?.shouldKeepTicking() ?: false
    }
}
