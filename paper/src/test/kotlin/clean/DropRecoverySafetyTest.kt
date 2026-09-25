package clean

import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Item
import org.bukkit.event.entity.ItemDespawnEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import plugin
import removeNonPlayerEntities
import resetConfig
import server
import setupMockBukkit
import top.e404.eclean.app.MessageService
import top.e404.eclean.feature.cleanup.drop.DropCleanupResult
import top.e404.eclean.feature.cleanup.drop.DropCleanupService
import top.e404.eclean.feature.trashcan.TrashcanItemStore
import top.e404.eclean.feature.trashcan.TrashcanManager
import top.e404.eclean.paper.adapt.PaperCommonItem
import top.e404.eclean.listener.DespawnListener
import updateDropConfig
import updateTrashcanConfig
import world
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DropRecoverySafetyTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpClass() = setupMockBukkit()
    }

    private lateinit var store: TrashcanItemStore
    private lateinit var manager: TrashcanManager

    @BeforeEach
    fun setUp() {
        resetConfig()
        world.getChunkAt(0, 0).load()
        updateDropConfig { it.copy(enabled = true, blacklistMode = true, matchers = listOf(Regex(".*"))) }
        store = TrashcanItemStore(lifetimeSeconds = { null })
        manager = TrashcanManager(store, MessageService())
    }

    @AfterEach
    fun tearDown() = removeNonPlayerEntities()

    private fun drop(stack: ItemStack = ItemStack(Material.DIAMOND, 7)): Item =
        world.dropItem(Location(world, 8.0, 64.0, 8.0), stack)

    private fun clean(dryRun: Boolean = false, target: TrashcanManager = manager): DropCleanupResult {
        var result: DropCleanupResult? = null
        DropCleanupService { target }.cleanWorld(world.name, dryRun) { result = it }
        server.scheduler.performTicks(5)
        return assertNotNull(result, "Drop cleanup must complete even when recovery fails")
    }

    @Test
    fun `cleanup recovers the complete stack and preserves translated looking lore`() {
        val key = NamespacedKey(plugin, "recovery_test")
        val stack = ItemStack(Material.DIAMOND, 7).apply {
            itemMeta = itemMeta!!.apply {
                displayName(Component.text("Original name"))
                lore(listOf("Custom lore", "Total: 7", "共7个", "Expires in 5m", "剩余 5m").map(Component::text))
                addEnchant(Enchantment.UNBREAKING, 3, true)
                persistentDataContainer.set(key, PersistentDataType.STRING, "original-value")
            }
        }
        val source = drop(stack)

        assertEquals(1, clean().cleaned)

        assertFalse(source.isValid)
        val recovered = store.getEntries().single()
        assertEquals(7L, recovered.count)
        assertTrue(recovered.prototype.isSimilar(stack), "All original item metadata must survive")
        assertEquals(stack.itemMeta!!.lore(), recovered.prototype.itemMeta!!.lore())
        assertEquals("original-value", recovered.prototype.itemMeta!!.persistentDataContainer.get(key, PersistentDataType.STRING))
        assertEquals(3, recovered.prototype.getEnchantmentLevel(Enchantment.UNBREAKING))
        assertEquals(7, stack.amount, "Collecting must not modify the caller's stack")
    }

    @Test
    fun `both trashcan and collection switches independently disable recovery`() {
        for ((enabled, collect) in listOf(false to true, true to false, false to false)) {
            updateTrashcanConfig { it.copy(enabled = enabled, collectFromDropCleanup = collect) }
            val source = drop()
            assertEquals(1, clean().cleaned)
            assertFalse(source.isValid)
            assertTrue(store.isEmpty(), "Recovery must require both switches")
        }
    }

    @Test
    fun `preview and protected items are neither removed nor recovered`() {
        val source = drop(ItemStack(Material.DIAMOND).apply {
            itemMeta = itemMeta!!.apply { addEnchant(Enchantment.UNBREAKING, 1, true) }
        })
        assertEquals(1, clean(dryRun = true).cleaned)
        assertTrue(source.isValid)
        assertTrue(store.isEmpty())

        updateDropConfig { it.copy(protectEnchanted = true) }
        assertEquals(0, clean().cleaned)
        assertTrue(source.isValid)
        assertTrue(store.isEmpty())
    }

    @Test
    fun `storage preparation failure retains source and completes cleanup with zero removals`() {
        val failingStore = TrashcanItemStore(lifetimeSeconds = { error("Storage unavailable") })
        val failingManager = TrashcanManager(failingStore, MessageService())
        val source = drop()

        assertEquals(0, clean(target = failingManager).cleaned)
        assertTrue(source.isValid)
        assertTrue(failingStore.isEmpty())
    }

    @Test
    fun `failed source removal rolls back only the new contribution`() {
        store.addItem(ItemStack(Material.DIAMOND, 3))
        val source = drop()
        val failingSource = object : Item by source {
            override fun remove() = throw IllegalStateException("Removal failed")
        }

        assertFailsWith<IllegalStateException> {
            PaperCommonItem(failingSource).transferTo(manager::transferFrom)
        }

        assertTrue(source.isValid)
        assertEquals(3L, store.totalCount())
        assertEquals(1, store.size)
    }

    @Test
    fun `source removal with no effect rolls back a newly allocated entry`() {
        val source = drop()
        val stubbornSource = object : Item by source {
            override fun remove() = Unit
        }

        assertFalse(PaperCommonItem(stubbornSource).transferTo(manager::transferFrom))
        assertTrue(source.isValid)
        assertTrue(store.isEmpty())
    }

    @Test
    fun `exception after effective removal still commits recovery exactly once`() {
        val source = drop()
        val lateFailure = object : Item by source {
            override fun remove() {
                source.remove()
                throw IllegalStateException("Failure after removal")
            }
        }

        assertTrue(PaperCommonItem(lateFailure).transferTo(manager::transferFrom))
        assertFalse(source.isValid)
        assertEquals(7L, store.totalCount())
        assertFalse(PaperCommonItem(lateFailure).transferTo(manager::transferFrom))
        assertEquals(7L, store.totalCount(), "Revisiting a removed entity must not duplicate it")
    }

    @Test
    fun `despawn recovery failure cancels natural removal and leaves no extra store entry`() {
        updateTrashcanConfig {
            it.copy(despawnRecovery = it.despawnRecovery.copy(enabled = true, matchers = listOf(Regex(".*"))))
        }
        val countBefore = plugin.services.trashcanStore.totalCount()
        val source = drop()
        val failingSource = object : Item by source {
            override fun remove() = throw IllegalStateException("Removal failed during despawn")
        }
        val event = ItemDespawnEvent(failingSource, source.location)

        DespawnListener.run { event.onEvent() }

        assertTrue(event.isCancelled)
        assertTrue(source.isValid)
        assertEquals(countBefore, plugin.services.trashcanStore.totalCount())
    }
}
