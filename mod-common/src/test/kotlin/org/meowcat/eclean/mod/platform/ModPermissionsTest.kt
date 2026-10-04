package org.meowcat.eclean.mod.platform

import org.meowcat.eclean.command.PermissionNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModPermissionsTest {
    private fun allowed(node: String, values: Map<String, Boolean?>, operator: Boolean = false): Boolean =
        modPermissionAllowed(node, { operator }) { values[it] }

    @Test
    fun `explicit leaf or legacy denial beats family admin and operator grants`() {
        val leaf = PermissionNode.CLEAN_DROP
        assertFalse(allowed(leaf.node, mapOf(leaf.node to false, "eclean.admin" to true), true))
        assertFalse(allowed(leaf.node, mapOf("eclean.clean.drop" to false,
            "eclean.command.clean" to true), true))
    }

    @Test
    fun `family and legacy grants work for nonoperators`() {
        assertTrue(allowed(PermissionNode.CLEAN_DROP.node, mapOf("eclean.command.clean" to true)))
        assertTrue(allowed(PermissionNode.TRASH_OPEN.node, mapOf("eclean.trash" to true)))
        assertTrue(allowed(PermissionNode.CONFIG.node, mapOf("eclean.admin" to true)))
    }

    @Test
    fun `undefined permissions use operator defaults and aliases resolve canonically`() {
        assertFalse(allowed(PermissionNode.CONFIG.node, emptyMap()))
        assertTrue(allowed(PermissionNode.CONFIG.node, emptyMap(), true))
        var fallbackNode: String? = null
        assertTrue(modPermissionAllowed("eclean.tp", { fallbackNode = it; true }) { null })
        assertEquals(PermissionNode.TELEPORT.node, fallbackNode)
    }

    @Test
    fun `native ids keep canonical and legacy nodes in the eclean namespace`() {
        assertEquals("eclean:command.clean.drop", ModPermissionService.identifier("eclean.command.clean.drop").toString())
        assertEquals("eclean:trash", ModPermissionService.identifier("eclean.trash").toString())
        assertEquals("eclean:command.reload", ModPermissionService.identifier("ECLEAN.COMMAND.RELOAD").toString())
    }

    @Test
    fun `resolved canonical denial is final even for operators`() {
        assertFalse(modPermissionAllowed(PermissionNode.CLEAN_DROP.node, { true }, true) { false })
    }

    @Test
    fun `resolved backend queries only canonical node and ignores undefined alias denials`() {
        val queries = mutableListOf<String>()
        assertTrue(modPermissionAllowed("eclean.tp", { false }, true) { key ->
            queries += key
            if (key == PermissionNode.TELEPORT.node) true else false
        })
        assertEquals(listOf(PermissionNode.TELEPORT.node), queries)
    }
}
