package org.meowcat.eclean.mod

import com.mojang.brigadier.arguments.StringArgumentType
import dev.architectury.event.events.common.CommandRegistrationEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.event.events.common.PlayerEvent
import dev.architectury.event.events.common.TickEvent
import net.minecraft.commands.Commands
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.item.ItemEntity
import org.slf4j.LoggerFactory

/** Both loader entrypoints register the same events and own one runtime per running server. */
object ECleanMod {
    val logger = LoggerFactory.getLogger("EClean")
    @Volatile var active: ModRuntime? = null
        private set
    private var initialized = false

    @Synchronized
    fun initialize(hooks: ModLoaderHooks) {
        if (initialized) return
        initialized = true
        CommandRegistrationEvent.EVENT.register { dispatcher, _, _ ->
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
        LifecycleEvent.SERVER_STARTED.register { server ->
            check(active == null) { "EClean already owns an active server runtime" }
            active = ModRuntime(server, hooks.configDirectory.resolve("eclean"), hooks).also { it.load() }
        }
        LifecycleEvent.SERVER_STOPPING.register { server ->
            active?.takeIf { it.server === server }?.let { runtime ->
                try { runtime.shutdown() }
                finally { active = null }
            }
        }
        TickEvent.SERVER_POST.register { server ->
            active?.takeIf { it.server === server }?.tick()
        }
        PlayerEvent.PLAYER_JOIN.register { player ->
            active?.takeIf { it.server === player.level().server }?.temporaryReturns?.handleJoin(player)
        }
        PlayerEvent.PLAYER_QUIT.register { player ->
            active?.takeIf { it.server === player.level().server }?.let { runtime ->
                runtime.temporaryReturns.handleQuit(player)
                runtime.menus.onDisconnect(player.uuid)
                runtime.debuggers.remove(player.uuid.toString())
            }
        }
        hooks.registerChatInput { player, message ->
            val runtime = active?.takeIf { it.server === player.level().server }
            runtime?.menus?.handleChat(player, message) ?: true
        }
    }

    /** Called at natural item expiry on the owning server thread. */
    @JvmStatic
    fun tryRecoverDespawn(item: ItemEntity): Boolean = active?.recoverDespawn(item) ?: false

    /** Keep configured cleanup and foreground delayed work running on an empty server. */
    @JvmStatic
    fun shouldKeepTicking(server: MinecraftServer): Boolean =
        active?.takeIf { it.server === server }?.shouldKeepTicking() ?: false
}
