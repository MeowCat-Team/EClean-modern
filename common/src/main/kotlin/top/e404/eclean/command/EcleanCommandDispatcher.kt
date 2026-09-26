package top.e404.eclean.command

import top.e404.eclean.common.api.CommonCommandSender

sealed interface CommandRoute {
    data class Execute(val name: String) : CommandRoute
    data class Usage(val keys: List<String>) : CommandRoute
}

/** Loader-independent root routing; native command APIs only register and adapt senders. */
class EcleanCommandDispatcher {
    fun route(sender: CommonCommandSender, args: Array<out String>, player: Boolean): CommandRoute {
        if (args.isEmpty()) return CommandRoute.Usage(usageKeys(sender, player))
        val spec = EcleanCommandCatalog.find(args[0])
            ?: return CommandRoute.Usage(usageKeys(sender, player))
        if (spec.maxArgs?.let { args.size > it } == true) return CommandRoute.Usage(listOf(spec.usageKey))
        return CommandRoute.Execute(spec.name)
    }

    fun usageKeys(sender: CommonCommandSender, player: Boolean): List<String> =
        EcleanCommandCatalog.entries.filter { spec ->
            spec.visible { sender.hasPermission(it.node) } && (!spec.playerOnly || player)
        }.map { it.usageKey }

    fun complete(
        sender: CommonCommandSender,
        args: Array<out String>,
        player: Boolean,
        worlds: List<String>,
        entityTypes: List<String>,
    ): List<String> = EcleanCommandCatalog.complete(
        args.toList(), { sender.hasPermission(it.node) }, player, worlds, entityTypes,
    )
}
