package adapt

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import plugin
import setupMockBukkit
import org.meowcat.eclean.common.api.PlatformType
import org.meowcat.eclean.common.api.TeleportService
import org.meowcat.eclean.feature.cleanup.chunk.DenseShowService
import org.meowcat.eclean.feature.stats.StatsMenuService
import org.meowcat.eclean.feature.trashcan.TrashcanService
import org.meowcat.eclean.feature.stats.WorldStatsProvider
import org.meowcat.eclean.feature.stats.WorldStatsResult
import org.meowcat.eclean.feature.stats.ChunkTotal
import org.meowcat.eclean.paper.adapt.PaperPlatform
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PaperPlatformTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpClass() {
            setupMockBukkit()
        }
    }

    private val fakeTeleport = object : TeleportService {
        override fun teleport(
            player: org.meowcat.eclean.common.api.CommonPlayer,
            target: org.meowcat.eclean.common.api.CommonLocation,
        ) = java.util.concurrent.CompletableFuture.completedFuture(true)
    }

    private val fakeTrashcan = object : TrashcanService {
        override val enabled: Boolean = true
        override fun open(player: org.meowcat.eclean.common.api.CommonPlayer) = Unit
        override fun entries(): List<org.meowcat.eclean.feature.trashcan.TrashcanEntryView> = emptyList()
    }

    private val fakeWorldStats = object : WorldStatsProvider {
        override fun worldExists(worldName: String) = false
        override fun collectWorldStats(worldName: String, onComplete: (WorldStatsResult?) -> Unit) =
            onComplete(null)

        override fun collectAllWorldStats(onComplete: (List<Pair<String, WorldStatsResult>>?) -> Unit) =
            onComplete(emptyList())

        override fun collectChunkTotals(worldName: String?, onComplete: (List<ChunkTotal>?) -> Unit) =
            onComplete(emptyList())

        override fun collectEntityStats(
            worldName: String,
            type: String,
            minCount: Int,
            onComplete: (List<org.meowcat.eclean.feature.stats.ChunkEntityCount>?) -> Unit,
        ) = onComplete(emptyList())

        override fun collectChunkEntities(
            worldName: String,
            type: String,
            chunkX: Int,
            chunkZ: Int,
            onComplete: (List<org.meowcat.eclean.feature.stats.EntityLocationDetail>?) -> Unit,
        ) = onComplete(emptyList())

        override fun isValidEntityType(type: String): Boolean = false
    }

    private val fakeDenseShow = object : DenseShowService {
        override fun show(player: org.meowcat.eclean.common.api.CommonPlayer) = Unit
    }

    private val fakeStatsMenu = object : StatsMenuService {
        override fun openStatsGui(player: org.meowcat.eclean.common.api.CommonPlayer, worldName: String) = Unit
    }

    @Test
    fun `paper platform exposes all common adapters`() {
        val platform = PaperPlatform(
            plugin,
            fakeTeleport,
            trashcanService = fakeTrashcan,
            worldStatsProvider = fakeWorldStats,
            denseShowService = fakeDenseShow,
            statsMenuService = fakeStatsMenu,
            playerProvider = plugin.services.commonPlatform.playerProvider,
            cleanupCommandService = plugin.services.commonPlatform.cleanupCommandService,
        )

        assertEquals(PlatformType.PAPER, platform.type)
        assertNotNull(platform.scheduler)
        assertNotNull(platform.messageSender)
        assertNotNull(platform.permissionService)
    }
}
