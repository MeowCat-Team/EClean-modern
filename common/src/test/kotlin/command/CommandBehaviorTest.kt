package command

import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.test.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import top.e404.eclean.command.*
import top.e404.eclean.common.api.*
import top.e404.eclean.feature.cleanup.CleanupCommandService
import top.e404.eclean.feature.stats.*
import top.e404.eclean.feature.trashcan.*
import top.e404.eclean.lang.LanguageManager
import top.e404.eclean.platform.execution.ChunkRef
import top.e404.eclean.util.placeholder

class CommandBehaviorTest {
    private val templates = LanguageManager(Path.of(".")).bundledSnapshot("en_us").templates
    private val messages = object : MessageProvider {
        override fun get(key: String, vararg args: Pair<String, Any>) = templates.getValue(key).placeholder(*args)
    }
    private class Player : CommonPlayer {
        override val name = "tester"
        override val uniqueId = "test"
        override val worldName = "world"
        override val location = CommonLocation(worldName, 0.0, 64.0, 0.0)
        var permissions: Set<String>? = null
        val messages = mutableListOf<String>()
        override fun hasPermission(node: String) = permissions?.contains(node) ?: true
        override fun sendMessage(component: Component) { messages += PlainTextComponentSerializer.plainText().serialize(component) }
    }
    private class Worlds : WorldAccess, WorldStatsProvider {
        var queried: String? = null
        override fun worldNames() = listOf("world", "123")
        override fun worldExists(worldName: String) = worldName in worldNames()
        override fun getLoadedChunkRefs(worldName: String) = emptyList<ChunkRef>()
        override fun getChunk(worldName: String, ref: ChunkRef): CommonChunk? = null
        override fun collectWorldStats(worldName: String, onComplete: (WorldStatsResult?) -> Unit) {
            queried = worldName; onComplete(null)
        }
        override fun collectAllWorldStats(onComplete: (List<Pair<String, WorldStatsResult>>?) -> Unit) = onComplete(emptyList())
        override fun collectChunkTotals(worldName: String?, onComplete: (List<ChunkTotal>?) -> Unit) { queried = worldName; onComplete(emptyList()) }
        override fun collectEntityStats(worldName: String, type: String, minCount: Int, onComplete: (List<ChunkEntityCount>?) -> Unit) { queried = worldName; onComplete(emptyList()) }
        override fun collectChunkEntities(worldName: String, type: String, chunkX: Int, chunkZ: Int, onComplete: (List<EntityLocationDetail>?) -> Unit) = onComplete(emptyList())
        override fun isValidEntityType(type: String) = type == "COW"
    }
    private class Cleanup : CleanupCommandService {
        val calls = mutableListOf<String>()
        override fun worldExists(world: String) = world == "world"
        override fun cleanAll(sender: CommonCommandSender, dryRun: Boolean) { calls += "all/*/$dryRun" }
        override fun cleanAllInWorld(sender: CommonCommandSender, world: String, dryRun: Boolean) { calls += "all/$world/$dryRun" }
        override fun cleanEntity(sender: CommonCommandSender, world: String?, dryRun: Boolean) { calls += "entity/$world/$dryRun" }
        override fun cleanDrop(sender: CommonCommandSender, world: String?, dryRun: Boolean) { calls += "drop/$world/$dryRun" }
        override fun cleanChunk(sender: CommonCommandSender, world: String?, dryRun: Boolean) { calls += "chunk/$world/$dryRun" }
        override fun cleanTrash(sender: CommonCommandSender, dryRun: Boolean) { calls += "trash/$dryRun" }
    }

    @Test fun `teleport reports only the actual asynchronous outcome`() {
        for (outcome in listOf("success", "cancelled", "exception")) {
            val player = Player()
            val result = CompletableFuture<Boolean>()
            val service = object : TeleportService {
                override fun teleport(player: CommonPlayer, target: CommonLocation) = result
            }
            teleportCommandHandler(messages, Worlds(), service)(player, arrayOf("tp", "world", "1", "64", "2"))
            assertTrue(player.messages.isEmpty())
            when (outcome) {
                "success" -> result.complete(true)
                "cancelled" -> result.complete(false)
                else -> result.completeExceptionally(IllegalStateException("cancelled by plugin"))
            }
            assertEquals(1, player.messages.size)
            assertTrue(player.messages.single().contains(if (outcome == "success") "complete" else "failed", true))
        }
    }

    @Test fun `non finite and unsupported coordinates never reach the platform`() {
        val service = object : TeleportService {
            override fun teleport(player: CommonPlayer, target: CommonLocation): CompletableFuture<Boolean> = error("must not teleport")
        }
        for (coordinate in listOf("NaN", "Infinity", "-Infinity", "30000001", "1e100", "bad")) {
            val player = Player()
            teleportCommandHandler(messages, Worlds(), service)(player, arrayOf("tp", "world", coordinate, "64", "0"))
            assertEquals(1, player.messages.size)
            assertTrue(player.messages.single().contains("number", true))
        }
    }

    @Test fun `clean all accepts a world and preview in every supported position`() {
        val service = Cleanup()
        val handler = cleanCommandHandler(messages, service)
        listOf(arrayOf("clean", "all", "world", "--preview"), arrayOf("clean", "--preview", "all", "world"),
            arrayOf("clean", "all", "--preview", "world")).forEach { handler(Player(), it) }
        assertEquals(List(3) { "all/world/true" }, service.calls)
    }

    @Test fun `unknown world and unsupported trash scope are rejected without cleanup`() {
        val service = Cleanup()
        cleanCommandHandler(messages, service)(Player(), arrayOf("clean", "drop", "typo"))
        cleanCommandHandler(messages, service)(Player(), arrayOf("clean", "trash", "world"))
        assertTrue(service.calls.isEmpty())
    }

    @Test fun `top positions distinguish a numeric world from the limit`() {
        val worlds = Worlds()
        topCommandHandler(messages, worlds)(Player(), arrayOf("top", "chunk", "10", "123"))
        assertEquals("123", worlds.queried)
        worlds.queried = null
        val player = Player()
        topCommandHandler(messages, worlds)(player, arrayOf("top", "chunk", "world"))
        assertNull(worlds.queried)
        assertTrue(player.messages.single().contains("number", true))
    }

    @Test fun `entity errors distinguish unknown worlds and invalid types and fill placeholders`() {
        val worlds = Worlds()
        val handler = entityCommandHandler(messages, worlds)
        val player = Player()
        handler(player, arrayOf("entity", "COW", "typo"))
        handler(player, arrayOf("entity", "NOT_AN_ENTITY", "world"))
        assertNull(worlds.queried)
        assertTrue(player.messages[0].contains("typo"))
        assertTrue(player.messages[1].contains("NOT_AN_ENTITY"))
        assertFalse(player.messages.any { it.contains("{type}") })
    }

    @Test fun `GUI needs world permission for another world and validates its existence`() {
        val opened = mutableListOf<String>()
        val service = object : StatsMenuService {
            override fun openStatsGui(player: CommonPlayer, worldName: String) { opened += worldName }
        }
        val player = Player().apply { permissions = setOf(Permissions.STATS_GUI) }
        val handler = statsCommandHandler(messages, Worlds(), service)
        handler(player, arrayOf("stats", "gui", "123"))
        assertTrue(opened.isEmpty())
        handler(player, arrayOf("stats", "gui", "world"))
        assertEquals(listOf("world"), opened)
        player.permissions = null
        handler(player, arrayOf("stats", "gui", "typo"))
        assertEquals(1, opened.size)
    }

    @Test fun `trash open completion has a matching parser route`() {
        var opened = 0
        val service = object : TrashcanService {
            override val enabled = true
            override fun entries() = emptyList<TrashcanEntryView>()
            override fun open(player: CommonPlayer) { opened++ }
        }
        trashCommandHandler(messages, service)(Player(), arrayOf("trash", "open"))
        assertEquals(1, opened)
    }

    @Test fun `catalog completion follows aliases positions and leaf permissions`() {
        fun complete(vararg args: String) = EcleanCommandCatalog.complete(args.toList(), { true }, true, listOf("world", "123"), listOf("COW"))
        assertTrue("world" in complete("clean", "all", ""))
        assertTrue("--preview" in complete("clean", "drop", "world", ""))
        assertTrue("world" in complete("clean", "--preview", "drop", ""))
        assertFalse("world" in complete("clean", "trash", ""))
        assertEquals(listOf("123"), complete("top", "chunk", "10", "1"))
        assertTrue("world" in complete("s", "gui", ""))
        assertTrue(EcleanCommandCatalog.complete(listOf(""), { false }, true, emptyList(), emptyList()).isEmpty())
        assertTrue(complete().isEmpty())
    }
}
