package org.meowcat.eclean.fabric

import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.kyori.adventure.text.Component
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import org.meowcat.eclean.command.*
import org.meowcat.eclean.common.api.CommonCommandSender
import org.meowcat.eclean.fabric.platform.FabricCommandSender
import org.meowcat.eclean.fabric.platform.FabricPlayer
import org.meowcat.eclean.fabric.platform.FabricWorldAccess
import org.meowcat.eclean.lang.LanguageManager
import org.meowcat.eclean.util.RichText
import org.meowcat.eclean.util.miniMessage
import java.util.concurrent.CompletableFuture
import java.util.Locale

internal fun registryId(type: String): Identifier? = Identifier.tryParse(type.lowercase(Locale.ROOT))
internal fun registryType(id: Identifier): String = FabricWorldAccess.registryType(id)

class FabricMessageProvider(private val language: LanguageManager) : MessageProvider {
    override fun entityName(type: String): Any = registryId(type)
        ?.takeIf(BuiltInRegistries.ENTITY_TYPE::containsKey)
        ?.let { BuiltInRegistries.ENTITY_TYPE.getValue(it) }
        ?.let { RichText(miniMessage.serialize(Component.translatable(it.descriptionId).append(Component.text(" ($type)")))) }
        ?: type
    override fun itemName(type: String): Any = registryId(type)
        ?.takeIf(BuiltInRegistries.ITEM::containsKey)
        ?.let { BuiltInRegistries.ITEM.getValue(it) }
        ?.let { RichText(miniMessage.serialize(Component.translatable(it.descriptionId).append(Component.text(" ($type)")))) }
        ?: type
    override fun get(key: String, vararg args: Pair<String, Any>): String = language.get(key, *args)
}

class FabricCommands(private val runtime: FabricRuntime) {
    private val dispatcher = EcleanCommandDispatcher()
    private val provider = runtime.messageProvider
    private val handlers: Map<String, (CommonCommandSender, Array<out String>) -> Boolean> = mapOf(
        "debug" to debugCommandHandler(provider, { runtime.configuration.current },
            { value -> runtime.configuration.update { value } },
            { id -> if (runtime.debuggers.remove(id)) false else { runtime.debuggers.add(id); true } }),
        "reload" to { sender, _ ->
            if (sender.hasPermission(Permissions.RELOAD)) runtime.reload(sender)
            else runtime.send(sender, runtime.language["command.no_permission"])
            true
        },
        "clean" to cleanCommandHandler(provider, runtime.cleanupCommands),
        "stats" to statsCommandHandler(provider, runtime.statistics, runtime.menus),
        "status" to statusCommandHandler(provider, runtime.statistics),
        "entity" to { sender, args ->
            val canonical = Array(args.size) { index ->
                if (index == 1) registryId(args[index])?.let(::registryType) ?: args[index] else args[index]
            }
            entityCommandHandler(provider, runtime.statistics)(sender, canonical)
        },
        "trash" to trashCommandHandler(provider, runtime.trashcan),
        "players" to playersCommandHandler(provider, runtime.players),
        "show" to showCommandHandler(provider, runtime.menus),
        "history" to historyCommandHandler(provider, runtime.history),
        "top" to topCommandHandler(provider, runtime.statistics),
        "tp" to teleportCommandHandler(provider, runtime.worlds, runtime.teleports),
        "config" to { sender, args ->
            ConfigCommandHandler(
                runtime.configuration, { runtime.worlds.findWorld(it) != null },
                { runtime.directory.resolve("config/normal/config.yml").toString() },
                { runtime.directory.resolve("config/dev").toString() },
                { runtime.inspect(sender, it) }, { runtime.reload(sender, it) },
                { key, values -> runtime.send(sender, runtime.language.get(key, *values.toTypedArray())) },
            ).handle(sender, args)
            true
        },
    )

    private fun sender(source: CommandSourceStack): CommonCommandSender =
        source.player?.let { FabricPlayer(it, runtime.permissions) } ?: FabricCommandSender(source, runtime.permissions)

    fun execute(source: CommandSourceStack, arguments: String): Int {
        val sender = sender(source)
        val args = arguments.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.toTypedArray() ?: emptyArray()
        when (val route = dispatcher.route(sender, args, source.player != null)) {
            is CommandRoute.Execute -> handlers[route.name]?.invoke(sender, args)
            is CommandRoute.Usage -> route.keys.forEach { runtime.send(sender, runtime.language[it]) }
        }
        return 1
    }

    fun suggest(source: CommandSourceStack, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val remaining = builder.remaining
        val args = remaining.trimStart().split(Regex("\\s+")).toTypedArray()
        val offset = Regex("\\s+").findAll(remaining).lastOrNull()?.range?.last?.plus(1) ?: 0
        val target = builder.createOffset(builder.start + offset)
        dispatcher.complete(sender(source), args, source.player != null, runtime.worlds.worldNames(),
            BuiltInRegistries.ENTITY_TYPE.keySet().map(::registryType))
            .forEach(target::suggest)
        return target.buildFuture()
    }
}
