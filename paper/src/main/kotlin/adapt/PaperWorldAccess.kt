package top.e404.eclean.paper.adapt

import org.bukkit.Bukkit
import top.e404.eclean.common.api.CommonChunk
import top.e404.eclean.common.api.WorldAccess
import top.e404.eclean.platform.execution.ChunkRef

class PaperWorldAccess : WorldAccess {
    override fun worldNames(): List<String> =
        Bukkit.getWorlds().map { it.name }

    override fun getLoadedChunkRefs(worldName: String): List<ChunkRef> {
        val world = Bukkit.getWorld(worldName) ?: return emptyList()
        return world.loadedChunks.map { ChunkRef(worldName, it.x, it.z) }
    }

    override fun getChunk(worldName: String, ref: ChunkRef): CommonChunk? {
        val world = Bukkit.getWorld(worldName) ?: return null
        if (!world.isChunkLoaded(ref.x, ref.z)) return null
        val chunk = world.getChunkAt(ref.x, ref.z)
        return PaperCommonChunk(ref, chunk)
    }
}
