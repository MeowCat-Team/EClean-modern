package feature.trashcan

import top.e404.eclean.feature.trashcan.StoredItemAdapter
import top.e404.eclean.feature.trashcan.TrashcanStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrashcanStoreTest {
    private data class Item(val id: String, val amount: Int, val metadata: String = "")

    private object Adapter : StoredItemAdapter<Item> {
        override fun copy(item: Item) = item.copy()
        override fun amount(item: Item) = item.amount
        override fun withAmount(item: Item, amount: Int) = item.copy(amount = amount)
        override fun isEmpty(item: Item) = item.id == "air"
        override fun isSimilar(first: Item, second: Item) = first.id == second.id && first.metadata == second.metadata
    }

    @Test fun `native item adapter controls similarity while quantities aggregate`() {
        val store = TrashcanStore(Adapter, { null })
        assertTrue(store.addItem(Item("sword", 2, "new")))
        assertTrue(store.addItem(Item("sword", 3, "used")))
        assertTrue(store.addItem(Item("sword", 4, "new")))
        assertEquals(2, store.size)
        assertEquals(6, store.getEntries().first().count)
        assertEquals(1, store.getEntries().first().prototype.amount)
        assertEquals(9, store.totalCount())
    }

    @Test fun `rejected source removal rolls back only its own deposit`() {
        val store = TrashcanStore(Adapter, { null })
        store.addItem(Item("stone", 5))
        assertFalse(store.transferItem(Item("stone", 2)) { false })
        assertEquals(5, store.totalCount())
        assertFalse(store.transferItem(Item("air", 1)) { error("Must not remove an invalid source") })
        assertEquals(5, store.removeItem(Item("stone", 1), 10))
        assertTrue(store.isEmpty())
    }

    @Test fun `entry ids and expiry are stable across merges`() {
        var now = 1_000L
        val store = TrashcanStore(Adapter, { 10L }, nowMillis = { now })
        store.addItem(Item("stone", 2))
        val first = store.getEntries().single()
        now += 5_000L
        store.addItem(Item("stone", 3))
        assertEquals(first.id, store.getEntries().single().id)
        assertEquals(first.deadline, store.getEntries().single().deadline)
        assertEquals(0, store.expireEntries(first.deadline - 1))
        assertEquals(1, store.expireEntries(first.deadline))
    }
}
