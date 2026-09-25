package top.e404.eclean.paper.adapt

import org.bukkit.Bukkit
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.common.api.PlayerProvider
import top.e404.eclean.command.toCommonPlayer

class PaperPlayerProvider : PlayerProvider {
    override fun onlinePlayers(): List<CommonPlayer> =
        top.e404.eclean.PL.services.playerSnapshots.players()
}
