package top.e404.eclean.paper.adapt

import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.common.api.PlayerProvider

class PaperPlayerProvider(private val snapshots: () -> List<CommonPlayer>) : PlayerProvider {
    override fun onlinePlayers(): List<CommonPlayer> = snapshots()
}
