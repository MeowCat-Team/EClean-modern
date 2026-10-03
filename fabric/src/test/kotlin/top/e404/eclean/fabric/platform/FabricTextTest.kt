package top.e404.eclean.fabric.platform

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.ClickEvent as NativeClickEvent
import net.minecraft.network.chat.HoverEvent as NativeHoverEvent
import net.minecraft.network.chat.contents.TranslatableContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FabricTextTest {
    @Test
    fun `native codec preserves command clicks hover text and styling`() {
        val source = Component.text("Inspect", NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/eclean stats"))
            .hoverEvent(HoverEvent.showText(Component.text("Show statistics", NamedTextColor.AQUA)))
        val result = FabricText.native(source, RegistryAccess.EMPTY)
        assertEquals("Inspect", result.string)
        assertEquals(NamedTextColor.GOLD.value(), result.style.color!!.value)
        assertTrue(result.style.isBold)
        assertEquals("/eclean stats", assertIs<NativeClickEvent.RunCommand>(result.style.clickEvent).command())
        assertEquals("Show statistics", assertIs<NativeHoverEvent.ShowText>(result.style.hoverEvent).value().string)
    }

    @Test
    fun `translations remain native translations with arguments and siblings`() {
        val source = Component.translatable("commands.give.success.single", Component.text("item"),
            Component.text("player", NamedTextColor.AQUA))
            .append(Component.text(" extra"))
        val result = FabricText.native(source, RegistryAccess.EMPTY)
        val contents = assertIs<TranslatableContents>(result.contents)
        assertEquals("commands.give.success.single", contents.key)
        assertEquals(2, contents.args.size)
        // Vanilla compacts a plain argument to a string while retaining styled component arguments.
        assertEquals("item", contents.args[0])
        val styledArgument = assertIs<net.minecraft.network.chat.Component>(contents.args[1])
        assertEquals("player", styledArgument.string)
        assertEquals(NamedTextColor.AQUA.value(), styledArgument.style.color!!.value)
        assertEquals(" extra", result.siblings.single().string)
    }
}
