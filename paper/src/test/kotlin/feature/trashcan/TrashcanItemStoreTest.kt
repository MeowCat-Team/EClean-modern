package feature.trashcan

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import setupMockBukkit
import org.meowcat.eclean.feature.trashcan.TrashcanItemStore
import org.meowcat.eclean.menu.trashcan.TrashcanDisplayItem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrashcanItemStoreTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpClass() {
            setupMockBukkit()
        }
    }

    private fun store(
        lifetime: Long? = 600L,
        stacking: Boolean = true,
    ) = TrashcanItemStore(
        lifetimeSeconds = { lifetime },
        stackingEnabled = { stacking },
    )

    @Test
    fun `addItem merges similar items beyond maxStackSize`() {
        val store = store()
        assertTrue(store.addItem(ItemStack(Material.DIAMOND, 64)))
        assertTrue(store.addItem(ItemStack(Material.DIAMOND, 64)))
        assertTrue(store.addItem(ItemStack(Material.DIAMOND, 10)))
        assertEquals(1, store.size)
        assertEquals(138, store.totalCount())
        assertEquals(138, store.getEntries().first().count)
    }

    @Test
    fun `addItem separates different materials`() {
        val store = store()
        store.addItem(ItemStack(Material.DIAMOND, 10))
        store.addItem(ItemStack(Material.IRON_INGOT, 5))
        assertEquals(2, store.size)
        assertEquals(15, store.totalCount())
    }

    @Test
    fun `addItem separates items with different durability`() {
        val store = store()
        val newSword = ItemStack(Material.DIAMOND_SWORD)
        val usedSword = ItemStack(Material.DIAMOND_SWORD)
        usedSword.itemMeta = (usedSword.itemMeta as Damageable).apply { damage = 1 }
        store.addItem(newSword)
        store.addItem(usedSword)
        assertEquals(2, store.size)
    }

    @Test
    fun `stacking disabled creates separate entries`() {
        val store = store(stacking = false)
        store.addItem(ItemStack(Material.DIAMOND, 10))
        store.addItem(ItemStack(Material.DIAMOND, 20))
        assertEquals(2, store.size)
    }

    @Test
    fun `new entry deadline is creation time plus lifetime`() {
        val store = store(lifetime = 600)
        val before = System.currentTimeMillis()
        store.addItem(ItemStack(Material.DIAMOND, 1))
        val deadline = store.getEntries().first().deadline
        val after = System.currentTimeMillis()
        assertTrue(deadline >= before + 600_000)
        assertTrue(deadline <= after + 600_000)
    }

    @Test
    fun `merging into existing entry does not reset deadline`() {
        val store = store(lifetime = 600)
        store.addItem(ItemStack(Material.DIAMOND, 1))
        val deadline = store.getEntries().first().deadline
        Thread.sleep(10)
        store.addItem(ItemStack(Material.DIAMOND, 1))
        assertEquals(deadline, store.getEntries().first().deadline)
    }

    @Test
    fun `null lifetime never expires`() {
        val store = store(lifetime = null)
        store.addItem(ItemStack(Material.DIAMOND, 1))
        val entry = store.getEntries().first()
        assertEquals(Long.MAX_VALUE, entry.deadline)
        assertEquals(0, store.expireEntries(Long.MAX_VALUE - 1))
        assertEquals(1, store.size)
        assertNull(store.earliestDeadline())
    }

    @Test
    fun `expireEntries removes only expired entries`() {
        val store = store(lifetime = 600)
        store.addItem(ItemStack(Material.DIAMOND, 1))
        val deadline = store.getEntries().first().deadline
        assertEquals(0, store.expireEntries(deadline - 1))
        assertEquals(1, store.expireEntries(deadline))
        assertTrue(store.isEmpty())
    }

    @Test
    fun `earliestDeadline returns minimum expiring deadline`() {
        val store = store(lifetime = 600)
        store.addItem(ItemStack(Material.DIAMOND, 1))
        val first = store.getEntries().first().deadline
        Thread.sleep(10)
        store.addItem(ItemStack(Material.IRON_INGOT, 1))
        val second = store.getEntries().last().deadline
        assertEquals(first, store.earliestDeadline())
        assertTrue(second > first)
    }

    @Test
    fun `removeItem subtracts amount and removes entry when exhausted`() {
        val store = store()
        store.addItem(ItemStack(Material.DIAMOND, 100))
        assertEquals(30, store.removeItem(ItemStack(Material.DIAMOND), 30))
        assertEquals(70, store.totalCount())
        assertEquals(70, store.removeItem(ItemStack(Material.DIAMOND), 100))
        assertTrue(store.isEmpty())
    }

    @Test
    fun `removeItem with other prototype does nothing`() {
        val store = store()
        store.addItem(ItemStack(Material.DIAMOND, 10))
        assertEquals(0, store.removeItem(ItemStack(Material.IRON_INGOT), 5))
        assertEquals(10, store.totalCount())
    }

    @Test
    fun `getEntries preserves insertion order`() {
        val store = store()
        store.addItem(ItemStack(Material.IRON_INGOT, 5))
        store.addItem(ItemStack(Material.DIAMOND, 10))
        val entries = store.getEntries()
        assertEquals(Material.IRON_INGOT, entries[0].prototype.type)
        assertEquals(Material.DIAMOND, entries[1].prototype.type)
    }

    @Test
    fun `addAll merges into existing entries`() {
        val store = store()
        store.addItem(ItemStack(Material.DIAMOND, 10))
        store.addAll(listOf(ItemStack(Material.DIAMOND, 5), ItemStack(Material.IRON_INGOT, 3)))
        assertEquals(2, store.size)
        assertEquals(15, store.getEntries().first { it.prototype.type == Material.DIAMOND }.count)
    }

    @Test
    fun `clear empties the store`() {
        val store = store()
        store.addItem(ItemStack(Material.DIAMOND, 1))
        store.addItem(ItemStack(Material.IRON_INGOT, 1))
        store.clear()
        assertTrue(store.isEmpty())
        assertEquals(0, store.size)
        assertEquals(0, store.totalCount())
    }

    @Test
    fun `getEntries returns isolated copies`() {
        val store = store()
        store.addItem(ItemStack(Material.DIAMOND, 10))
        val snapshot = store.getEntries()
        snapshot.first().count = 999
        assertEquals(10, store.getEntries().first().count)
        assertFalse(store.getEntries().first().prototype === snapshot.first().prototype)
    }

    @Test
    fun `removeItem never removes more than available`() {
        val store = store()
        store.addItem(ItemStack(Material.DIAMOND, 5))
        assertEquals(5, store.removeItem(ItemStack(Material.DIAMOND), 100))
        assertTrue(store.isEmpty())
    }

    @Test
    fun `display item lore does not pollute stored prototype`() {
        val store = store()
        store.addItem(ItemStack(Material.DIAMOND, 1))
        val entry = store.getEntries().single()
        val display = TrashcanDisplayItem(entry)
        display.update()

        val prototypeLore = display.prototype.itemMeta?.lore()?.joinToString("\n").orEmpty()
        val displayLore = display.item.itemMeta?.lore()?.joinToString("\n").orEmpty()

        assertTrue(displayLore.isNotBlank())
        assertFalse(prototypeLore.contains("共"))
        assertFalse(prototypeLore.contains("剩余"))
    }
}
