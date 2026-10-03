package feature.trashcan

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.entity.PlayerMock
import plugin
import server
import setupMockBukkit
import org.meowcat.eclean.app.MessageService
import org.meowcat.eclean.feature.trashcan.TrashcanItemStore
import org.meowcat.eclean.feature.trashcan.TrashcanManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrashcanMenuGhostItemTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpClass() {
            setupMockBukkit()
        }
    }

    private fun store(): TrashcanItemStore = TrashcanItemStore(
        lifetimeSeconds = { null },
        stackingEnabled = { true },
    ).also { it.addItem(ItemStack(Material.DIAMOND, 10)) }

    private fun grantedPlayer(name: String = "trash-ghost"): PlayerMock {
        val player = server.addPlayer(name)
        player.addAttachment(plugin).setPermission("eclean.admin", true)
        player.inventory.clear()
        return player
    }

    @Test
    fun `opening trash menu does not inject trash items into player inventory`() {
        val s = store()
        val manager = TrashcanManager(s, MessageService())
        val player = grantedPlayer()

        manager.open(player)

        val diamonds = player.inventory.contents.filterNotNull().filter { it.type == Material.DIAMOND }
        assertTrue(diamonds.isEmpty(), "Opening the menu must not place trash items in the player inventory")
        assertEquals(10, s.totalCount())
    }

    @Test
    fun `taking one item persists in inventory after menu closes`() {
        val s = store()
        val manager = TrashcanManager(s, MessageService())
        val player = grantedPlayer()

        manager.open(player)
        player.simulateInventoryClick(0)

        assertEquals(9, s.totalCount())
        val diamonds = player.inventory.contents.filterNotNull().filter { it.type == Material.DIAMOND }
        assertTrue(diamonds.isNotEmpty(), "Clicking a trash item should give the item to the player")
        assertEquals(1, diamonds.sumOf { it.amount })

        player.closeInventory()
        val afterClose = player.inventory.contents.filterNotNull().filter { it.type == Material.DIAMOND }
        assertTrue(afterClose.isNotEmpty(), "Given items must not disappear after closing the menu")
        assertEquals(1, afterClose.sumOf { it.amount })
    }
}
