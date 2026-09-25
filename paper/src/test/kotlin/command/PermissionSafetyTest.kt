package command

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import plugin
import server
import setupMockBukkit
import top.e404.eclean.command.*
import kotlin.test.*

class PermissionSafetyTest {
    companion object {
        @JvmStatic @BeforeAll fun setup() { setupMockBukkit() }
    }

    @Test fun `explicit leaf deny beats alias parent and admin grants`() {
        for ((leaf, grant) in listOf(
            PermissionNode.SHOW_CLEAN to "eclean.clean.chunk",
            PermissionNode.CLEAN_DROP to "eclean.command.clean",
            PermissionNode.CLEAN_DROP to "eclean.admin",
        )) {
            val player = server.addPlayer()
            player.addAttachment(plugin).apply {
                setPermission(grant, true)
                setPermission(leaf.node, false)
            }
            assertFalse(player.hasPermission(leaf), "$grant must not bypass ${leaf.node}=false")
            assertFalse(player.toCommon().hasPermission(leaf.node))
            assertFalse(player.toCommonPlayer().hasPermission(leaf.node))
        }
    }

    @Test fun `legacy grants are consistent for menu command adapters and completion`() {
        val player = server.addPlayer()
        player.addAttachment(plugin).setPermission("eclean.reload", true)
        assertTrue(player.hasPermission(PermissionNode.RELOAD))
        assertTrue(player.toCommon().hasPermission(Permissions.RELOAD))
        assertTrue(player.toCommonPlayer().hasPermission(Permissions.RELOAD))
        val suggestions = Commands.onTabComplete(player, server.getPluginCommand("eclean")!!, "eclean", arrayOf(""))
        assertTrue("reload" in suggestions)
    }

    @Test fun `default denies do not prevent an explicit leaf grant`() {
        val player = server.addPlayer()
        player.addAttachment(plugin).setPermission(Permissions.CLEAN_DROP, true)
        assertTrue(player.hasPermission(PermissionNode.CLEAN_DROP))
        assertTrue(player.toCommonPlayer().hasPermission(Permissions.CLEAN_DROP))
        assertFalse(player.toCommonPlayer().hasPermission(Permissions.CLEAN_ENTITY))
    }
}
