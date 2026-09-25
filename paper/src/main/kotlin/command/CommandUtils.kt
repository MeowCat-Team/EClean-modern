package top.e404.eclean.command
import top.e404.eclean.PL

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import top.e404.eclean.feature.stats.WorldStatsService
import top.e404.eclean.lang.MLang
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.util.formatAsConst
import top.e404.eclean.util.parseSecondAsDuration
import java.util.concurrent.atomic.AtomicInteger

internal fun CommandSender.sendTrashStats() {
    val entries = PL.services.trashcanManager.stats()
    if (entries.isEmpty()) {
        PL.services.messages.send(this, MLang["command.trash_stats_empty"])
        return
    }
    PL.services.messages.send(this, MLang["command.trash_stats_header", "count" to entries.size])
    val now = System.currentTimeMillis()
    for (entry in entries) {
        val expire = entry.deadline.takeIf { it != Long.MAX_VALUE }
            ?.let { maxOf(0, (it - now) / 1000) }
            ?.parseSecondAsDuration()
            ?: MLang["command.trash_never_expire"]
        PL.services.messages.send(
            this,
            MLang["command.trash_stats_line", "item" to entry.prototype.type.name, "amount" to entry.count, "expire" to expire],
        )
    }
}

internal fun CommandSender.sendWorldStats(worldName: String) {
    val world = Bukkit.getWorld(worldName)
    if (world == null) {
        PL.services.messages.send(this, MLang["command.invalid.world", "world" to worldName])
        return
    }
    val service = WorldStatsService()
    service.collectWorldStats(worldName) { result ->
        if (result == null) {
            PL.services.messages.send(this, MLang["command.stats_collect_failed"])
            return@collectWorldStats
        }
        if (result.totalEntities == 0) {
            PL.services.messages.send(this, MLang["command.stats.empty"])
            return@collectWorldStats
        }
        val entity = result.sortedEntries().joinToString(MLang["command.stats.spacing"]) { (type, count) ->
            val command = "/eclean entity $type $worldName"
            val content = MLang["command.stats.content", "type" to type, "count" to count.withColor()]
            "<click:run_command:'$command'><hover:show_text:'${MLang["common.hover.view_distribution"]}'>$content</hover></click>"
        }
        PL.services.messages.send(
            this,
            MLang[
                "command.stats.world",
                "world" to worldName,
                "count" to result.loadedChunks,
                "force" to result.forceLoadedChunks,
                "entity" to entity
            ]
        )
    }
}

internal fun CommandSender.sendEntityStats(
    worldName: String,
    typeName: String,
    min: Int = 0,
    chunkX: Int? = null,
    chunkZ: Int? = null,
) {
    val world = Bukkit.getWorld(worldName)
    if (world == null) {
        PL.services.messages.send(this, MLang["command.invalid.world", "world" to worldName])
        return
    }
    val type = typeName.formatAsConst()
    try {
        EntityType.valueOf(type)
    } catch (t: Throwable) {
        PL.services.messages.send(this, MLang["command.invalid.entity_type"])
        return
    }
    val service = WorldStatsService()
    if (chunkX != null && chunkZ != null) {
        service.collectChunkEntities(worldName, type, chunkX, chunkZ) { details ->
            if (details.isEmpty()) {
                PL.services.messages.send(this, MLang["command.stats.empty"])
                return@collectChunkEntities
            }
            val entity = details.joinToString(MLang["command.stats.spacing"]) { detail ->
                val command = "/eclean tp $worldName ${detail.x} ${detail.y} ${detail.z}"
                "<click:run_command:'$command'><hover:show_text:'${MLang["common.hover.tp"]}'><white>${typeName} @ ${detail.x}, ${detail.y}, ${detail.z}</white></hover></click>"
            }
            PL.services.messages.send(
                this,
                MLang[
                    "command.stats.entity",
                    "type" to typeName,
                    "entity" to entity
                ]
            )
        }
        return
    }

    service.collectEntityStats(worldName, type, min) { entries ->
        if (entries.isEmpty()) {
            PL.services.messages.send(this, MLang["command.stats.empty"])
            return@collectEntityStats
        }
        val entity = entries.joinToString(MLang["command.stats.spacing"]) { entry ->
            val command = "/eclean entity $typeName $worldName ${entry.chunkX} ${entry.chunkZ}"
            val label = "x: ${entry.chunkX * 16}..${entry.chunkX * 16 + 15}, z: ${entry.chunkZ * 16}..${entry.chunkZ * 16 + 15}"
            val content = MLang["command.stats.content", "type" to label, "count" to entry.count.withColor()]
            "<click:run_command:'$command'><hover:show_text:'${MLang["common.hover.view_chunk"]}'>$content</hover></click>"
        }
        PL.services.messages.send(
            this,
            MLang[
                "command.stats.entity",
                "type" to typeName,
                "entity" to entity
            ]
        )
    }
}

internal fun CommandSender.sendPlayersStats() {
    val players = PL.services.commonPlatform.playerProvider.onlinePlayers()
    if (players.isEmpty()) {
        PL.services.messages.send(this, MLang["command.stats.empty"])
        return
    }
    val lines = players.groupBy { it.worldName }.mapValues { (_, list) ->
        list.map { player ->
            val loc = player.location
            MLang["command.player_location", "player" to player.name,
                "x" to kotlin.math.floor(loc.x).toInt(), "y" to kotlin.math.floor(loc.y).toInt(),
                "z" to kotlin.math.floor(loc.z).toInt()]
        }
    }
    sendPlayerResult(this, lines)
}

internal fun sendPlayerResult(sender: CommandSender, lines: Map<String, List<String>>) {
    Schedulers.runGlobal {
        lines.forEach { (worldName, worldLines) ->
            PL.services.messages.send(
                sender,
                MLang["command.players_header", "world" to worldName, "lines" to worldLines.joinToString("")],
            )
        }
    }
}

internal fun Int.withColor() = when {
    this > 60 -> "<red>$this</red>"
    this > 30 -> "<yellow>$this</yellow>"
    else -> "<green>$this</green>"
}
