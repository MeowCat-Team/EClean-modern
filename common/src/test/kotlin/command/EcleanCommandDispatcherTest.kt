package command

import net.kyori.adventure.text.Component
import top.e404.eclean.command.CommandRoute
import top.e404.eclean.command.EcleanCommandDispatcher
import top.e404.eclean.common.api.CommonCommandSender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EcleanCommandDispatcherTest {
    private class Sender(private val grants: Set<String> = emptySet()) : CommonCommandSender {
        override val name = "tester"
        override fun hasPermission(node: String) = node in grants
        override fun sendMessage(component: Component) = Unit
    }

    @Test fun `aliases and excess arguments route consistently`() {
        val router = EcleanCommandDispatcher()
        val sender = Sender(setOf("eclean.command.reload"))
        assertEquals(CommandRoute.Execute("reload"), router.route(sender, arrayOf("r"), true))
        assertEquals(CommandRoute.Usage(listOf("command.usage.reload")),
            router.route(sender, arrayOf("reload", "extra"), true))
        assertEquals(CommandRoute.Usage(router.usageKeys(sender, true)), router.route(sender, emptyArray(), true))
    }

    @Test fun `completion and usage respect permissions and player context`() {
        val router = EcleanCommandDispatcher()
        val denied = Sender()
        val allowed = Sender(setOf("eclean.command.reload", "eclean.command.teleport"))
        assertFalse("reload" in router.complete(denied, arrayOf(""), true, emptyList(), emptyList()))
        assertTrue("reload" in router.complete(allowed, arrayOf(""), true, emptyList(), emptyList()))
        assertFalse("command.usage.teleport" in router.usageKeys(allowed, false))
        assertTrue("command.usage.teleport" in router.usageKeys(allowed, true))
    }
}
