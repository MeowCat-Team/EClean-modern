package feature.trashcan

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import setupMockBukkit
import top.e404.eclean.app.MessageService
import top.e404.eclean.feature.trashcan.TrashcanItemStore
import top.e404.eclean.feature.trashcan.TrashcanManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrashcanManagerTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpClass() {
            setupMockBukkit()
        }
    }

    private fun loreItem(vararg lines: String): ItemStack {
        val item = ItemStack(Material.DIAMOND, 1)
        val meta = item.itemMeta!!
        meta.lore(lines.map { Component.text(it) })
        item.itemMeta = meta
        return item
    }

    private fun manager(): TrashcanManager {
        val store = TrashcanItemStore(
            lifetimeSeconds = { null },
            stackingEnabled = { true },
        )
        return TrashcanManager(store, MessageService())
    }

    @Test
    fun `real lore resembling translated UI text is preserved`() {
        val manager = manager()
        manager.addItem(
            loreItem(
                "Keep this line",
                "共1个",
                "剩余 5m",
                "Total: 1",
                "Expires in 5m",
            )
        )

        val stored = manager.stats().single().prototype
        val lore = stored.itemMeta?.lore()?.map { net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it) }.orEmpty()
        assertEquals(listOf("Keep this line", "共1个", "剩余 5m", "Total: 1", "Expires in 5m"), lore)
    }

    @Test
    fun `normal lore is preserved`() {
        val manager = manager()
        val item = loreItem("Custom lore")
        manager.addItem(item)

        val lore = manager.stats().single().prototype.itemMeta?.lore()
            ?.map { net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it) }
            .orEmpty()
        assertTrue(lore.contains("Custom lore"))
    }
}
