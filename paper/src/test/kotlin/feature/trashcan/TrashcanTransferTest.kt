package feature.trashcan

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import server
import setupMockBukkit
import top.e404.eclean.app.MessageService
import top.e404.eclean.feature.trashcan.TrashcanItemStore
import top.e404.eclean.feature.trashcan.TrashcanManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrashcanTransferTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpClass() = setupMockBukkit()
    }

    @Test
    fun `pending deposit cannot be withdrawn before source removal completes`() {
        val store = TrashcanItemStore(lifetimeSeconds = { null })
        val removalEntered = CountDownLatch(1)
        val releaseRemoval = CountDownLatch(1)
        val withdrawalStarted = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val transfer = pool.submit<Boolean> {
                store.transferItem(ItemStack(Material.DIAMOND, 7)) {
                    removalEntered.countDown()
                    check(releaseRemoval.await(5, TimeUnit.SECONDS))
                    false
                }
            }
            assertTrue(removalEntered.await(5, TimeUnit.SECONDS))
            val withdrawal = pool.submit<Int> {
                withdrawalStarted.countDown()
                store.removeItem(ItemStack(Material.DIAMOND), 7)
            }
            assertTrue(withdrawalStarted.await(5, TimeUnit.SECONDS))
            assertFalse(withdrawal.isDone)
            releaseRemoval.countDown()
            assertFalse(transfer.get(5, TimeUnit.SECONDS))
            assertEquals(0, withdrawal.get(5, TimeUnit.SECONDS))
            assertTrue(store.isEmpty())
        } finally {
            releaseRemoval.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `invalid empty deposits cannot consume their source`() {
        val store = TrashcanItemStore(lifetimeSeconds = { null })
        var removalCalled = false
        assertFalse(store.transferItem(ItemStack(Material.AIR)) { removalCalled = true; true })
        assertFalse(removalCalled)
        assertTrue(store.isEmpty())
    }

    @Test
    fun `many deposits coalesce into one deferred menu refresh`() {
        val store = TrashcanItemStore(lifetimeSeconds = { null })
        val scheduled = mutableListOf<() -> Unit>()
        val manager = TrashcanManager(store, MessageService()) { task -> scheduled.add(task) }
        val viewer = server.addPlayer("recovery-refresh-viewer")
        manager.open(viewer)
        try {
            repeat(50) { assertTrue(manager.transferFrom(ItemStack(Material.DIAMOND)) { true }) }
            assertEquals(50L, store.totalCount())
            assertEquals(1, scheduled.size)

            scheduled.single().invoke()
            assertTrue(manager.addItem(ItemStack(Material.DIAMOND)))
            assertEquals(2, scheduled.size, "Later deposits must be able to request a fresh update")
        } finally {
            viewer.closeInventory()
        }
    }

    @Test
    fun `rejected and throwing schedulers do not undo deposits or leave refresh pending`() {
        for (throws in listOf(false, true)) {
            val store = TrashcanItemStore(lifetimeSeconds = { null })
            var attempts = 0
            val manager = TrashcanManager(store, MessageService()) {
                attempts++
                if (throws) error("Scheduler stopped")
                false
            }
            val viewer = server.addPlayer("stopped-refresh-$throws")
            manager.open(viewer)
            try {
                repeat(2) { assertTrue(manager.transferFrom(ItemStack(Material.DIAMOND)) { true }) }
                assertEquals(2L, store.totalCount())
                assertEquals(2, attempts, "A failed scheduling attempt must clear its pending flag")
            } finally {
                viewer.closeInventory()
            }
        }
    }
}
