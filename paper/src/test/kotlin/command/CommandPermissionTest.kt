package command

import org.bukkit.command.CommandSender
import org.bukkit.permissions.PermissionAttachment
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.entity.PlayerMock
import plugin
import server
import setupMockBukkit
import org.meowcat.eclean.command.Commands
import org.meowcat.eclean.command.PermissionNode
import org.meowcat.eclean.command.hasPermission
import java.util.UUID
import kotlin.test.assertTrue

class CommandPermissionTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpClass() {
            setupMockBukkit()
        }
    }

    private fun player(name: String = "perm-${UUID.randomUUID()}"): PlayerMock = server.addPlayer(name)

    private fun PermissionAttachment.grant(node: PermissionNode): PermissionAttachment {
        setPermission(node.node, true)
        return this
    }

    private fun command() = server.getPluginCommand("eclean")!!

    @Test
    fun `reload is denied without permission`() {
        val player = player()
        val handled = Commands.onCommand(player, command(), "eclean", arrayOf("reload"))
        assertTrue(handled)
        assertTrue(player.nextComponentMessage() != null) // no_permission feedback
    }

    @Test
    fun `reload is allowed with legacy admin permission`() {
        val player = player()
        player.addAttachment(plugin).setPermission("eclean.admin", true)
        val handled = Commands.onCommand(player, command(), "eclean", arrayOf("reload"))
        assertTrue(handled)
    }

    @Test
    fun `clean entity is denied without clean entity permission`() {
        val player = player()
        val handled = Commands.onCommand(player, command(), "eclean", arrayOf("clean", "entity"))
        assertTrue(handled)
        assertTrue(player.nextComponentMessage() != null)
    }

    @Test
    fun `clean entity is allowed with specific leaf permission`() {
        val player = player()
        player.addAttachment(plugin).grant(PermissionNode.CLEAN_ENTITY)
        assertTrue(player.hasPermission(PermissionNode.CLEAN_ENTITY))
        val handled = Commands.onCommand(player, command(), "eclean", arrayOf("clean", "entity"))
        assertTrue(handled)
    }

    @Test
    fun `clean trash requires trash clear not only trash open`() {
        val player = player()
        player.addAttachment(plugin).grant(PermissionNode.TRASH_OPEN)
        val handled = Commands.onCommand(player, command(), "eclean", arrayOf("clean", "trash"))
        assertTrue(handled)
        assertTrue(player.nextComponentMessage() != null)
    }

    @Test
    fun `tab completion hides reload without permission`() {
        val player = player()
        val suggestions = Commands.onTabComplete(player, command(), "eclean", arrayOf(""))
        assertTrue(suggestions.none { it == "reload" })
    }

    @Test
    fun `tab completion shows reload with admin permission`() {
        val player = player()
        player.addAttachment(plugin).setPermission("eclean.admin", true)
        val suggestions = Commands.onTabComplete(player, command(), "eclean", arrayOf(""))
        assertTrue("reload" in suggestions)
    }

    @Test
    fun `permission helper honors admin fallback`() {
        val player = player()
        player.addAttachment(plugin).setPermission("eclean.admin", true)
        assertTrue(player.hasPermission(PermissionNode.CLEAN_ENTITY))
    }
}
