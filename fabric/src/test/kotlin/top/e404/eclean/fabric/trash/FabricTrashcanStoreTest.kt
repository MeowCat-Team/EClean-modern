package top.e404.eclean.fabric.trash

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.model.StackingConfig
import top.e404.eclean.config.model.TrashcanConfig
import top.e404.eclean.fabric.menu.NativeItemTestBootstrap
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FabricTrashcanStoreTest {
    @BeforeTest fun bootstrap() {
        NativeItemTestBootstrap.initialize()
    }

    private fun config(stacking: Boolean = true) = ConfigBundle(trashcan = TrashcanConfig(
        clearIntervalSeconds = null, stacking = StackingConfig(enabled = stacking),
    ))

    private fun namedStack(name: String, count: Int): ItemStack = ItemStack(Items.DIAMOND, count).apply {
        set(DataComponents.CUSTOM_NAME, Component.literal(name))
        set(DataComponents.LORE, ItemLore(listOf(Component.literal("Original item lore"))))
        set(DataComponents.CUSTOM_DATA, CustomData.of(CompoundTag().apply { putString("owner", name) }))
    }

    @Test fun `native components survive aggregation and snapshots do not alias the source`() {
        val store = FabricTrashcanStore { config() }
        val source = namedStack("Alice", 40)
        assertTrue(store.addItem(source))
        assertTrue(store.addItem(source.copyWithCount(40)))
        assertTrue(store.addItem(namedStack("Bob", 40)))

        source.set(DataComponents.CUSTOM_NAME, Component.literal("Modified source"))
        val entries = store.getEntries()
        assertEquals(2, entries.size)
        assertEquals(80L, entries[0].count)
        assertEquals(40L, entries[1].count)
        assertEquals("Alice", entries[0].prototype.get(DataComponents.CUSTOM_NAME)?.string)
        assertEquals("Original item lore", entries[0].prototype.get(DataComponents.LORE)?.lines()?.single()?.string)
        assertEquals(CustomData.of(CompoundTag().apply { putString("owner", "Alice") }),
            entries[0].prototype.get(DataComponents.CUSTOM_DATA))
        assertEquals(1, entries[0].prototype.count)

        entries[0].prototype.set(DataComponents.CUSTOM_NAME, Component.literal("Modified snapshot"))
        entries[0].count = 0
        assertEquals(80L, store.getEntries()[0].count)
        assertEquals("Alice", store.getEntries()[0].prototype.get(DataComponents.CUSTOM_NAME)?.string)
    }

    @Test fun `failed source removal rolls back new and aggregated entries`() {
        val store = FabricTrashcanStore { config() }
        val stack = namedStack("Transaction", 64)
        assertFalse(store.transferItem(stack) { false })
        assertEquals(0, store.size)
        assertTrue(store.addItem(stack))
        assertFalse(store.transferItem(stack) { false })
        assertEquals(64L, store.totalCount())
        assertFailsWith<IllegalStateException> {
            store.transferItem(stack) { error("Source removal failed") }
        }
        assertEquals(64L, store.totalCount())
        assertEquals(1, store.size)
    }

    @Test fun `entry identity stops expired menu snapshots withdrawing from a replacement`() {
        var configuration = config().copy(trashcan = config().trashcan.copy(clearIntervalSeconds = 600))
        val store = FabricTrashcanStore { configuration }
        val stack = namedStack("Same item", 32)
        store.addItem(stack)
        val stale = store.getEntries().single()
        assertEquals(1, store.expireEntries(stale.deadline))
        configuration = config()
        store.addItem(stack)
        val replacement = store.getEntries().single()
        assertNotEquals(stale.id, replacement.id)
        assertEquals(0, store.removeItem(stale.prototype, 16, stale.id))
        assertEquals(32L, store.totalCount())
        assertEquals(32, store.removeItem(replacement.prototype, 64, replacement.id))
        assertTrue(store.isEmpty())
    }

    @Test fun `stacking respects metadata and exact quantities beyond a native stack`() {
        val store = FabricTrashcanStore { config() }
        repeat(70) { store.addItem(ItemStack(Items.STONE, 64)) }
        assertEquals(1, store.size)
        assertEquals(4_480L, store.totalCount())
        val entry = store.getEntries().single()
        assertEquals(64, store.removeItem(entry.prototype, 64, entry.id))
        assertEquals(4_416L, store.clear())
        assertTrue(store.isEmpty())

        val unstacked = FabricTrashcanStore { config(stacking = false) }
        repeat(3) { unstacked.addItem(ItemStack(Items.STONE, 64)) }
        assertEquals(3, unstacked.size)
        assertEquals(192L, unstacked.totalCount())
    }

    @Test fun `adding an existing item does not extend its lifetime after a configuration change`() {
        var configuration = config().copy(trashcan = config().trashcan.copy(clearIntervalSeconds = 600))
        val store = FabricTrashcanStore { configuration }
        store.addItem(ItemStack(Items.STONE, 64))
        val deadline = store.getEntries().single().deadline
        configuration = configuration.copy(trashcan = configuration.trashcan.copy(clearIntervalSeconds = 1200))
        store.addItem(ItemStack(Items.STONE, 64))
        assertEquals(deadline, store.getEntries().single().deadline)
        assertEquals(128L, store.totalCount())
        assertEquals(1, store.expireEntries(deadline))
        assertEquals(0L, store.totalCount())
    }
}
