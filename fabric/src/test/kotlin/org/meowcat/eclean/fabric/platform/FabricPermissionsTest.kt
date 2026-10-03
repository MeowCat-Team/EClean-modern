package org.meowcat.eclean.fabric.platform

import net.fabricmc.fabric.api.util.TriState
import org.meowcat.eclean.command.PermissionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FabricPermissionsTest {
    private fun allowed(node: String, values: Map<String, TriState>, operator: Boolean = false): Boolean =
        fabricPermissionAllowed(node, { operator }) { values[it] ?: TriState.DEFAULT }

    @Test
    fun `explicit leaf or legacy denial beats family admin and operator grants`() {
        val leaf = PermissionNode.CLEAN_DROP
        assertFalse(allowed(leaf.node, mapOf(leaf.node to TriState.FALSE, "eclean.admin" to TriState.TRUE), true))
        assertFalse(allowed(leaf.node, mapOf("eclean.clean.drop" to TriState.FALSE,
            "eclean.command.clean" to TriState.TRUE), true))
    }

    @Test
    fun `family and legacy grants work for nonoperators`() {
        assertTrue(allowed(PermissionNode.CLEAN_DROP.node, mapOf("eclean.command.clean" to TriState.TRUE)))
        assertTrue(allowed(PermissionNode.TRASH_OPEN.node, mapOf("eclean.trash" to TriState.TRUE)))
        assertTrue(allowed(PermissionNode.CONFIG.node, mapOf("eclean.admin" to TriState.TRUE)))
    }

    @Test
    fun `undefined permissions use operator defaults and aliases resolve canonically`() {
        assertFalse(allowed(PermissionNode.CONFIG.node, emptyMap()))
        assertTrue(allowed(PermissionNode.CONFIG.node, emptyMap(), true))
        var fallbackNode: String? = null
        assertTrue(fabricPermissionAllowed("eclean.tp", { fallbackNode = it; true }) { TriState.DEFAULT })
        assertEquals(PermissionNode.TELEPORT.node, fallbackNode)
    }

    @Test
    fun `native ids keep canonical and legacy nodes in the eclean namespace`() {
        assertEquals("eclean:command.clean.drop", FabricPermissionService.identifier("eclean.command.clean.drop").toString())
        assertEquals("eclean:trash", FabricPermissionService.identifier("eclean.trash").toString())
        assertEquals("eclean:command.reload", FabricPermissionService.identifier("ECLEAN.COMMAND.RELOAD").toString())
    }
}
