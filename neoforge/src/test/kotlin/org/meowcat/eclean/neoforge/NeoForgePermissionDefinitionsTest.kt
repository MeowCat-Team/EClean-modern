package org.meowcat.eclean.neoforge

import org.meowcat.eclean.command.PermissionNode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeoForgePermissionDefinitionsTest {
    @Test
    fun `every canonical parent legacy alias and admin node is registered without cycles`() {
        val definitions = NeoForgePermissionDefinitions.inherited
        val expected = PermissionNode.entries.flatMap { listOf(it.node) + it.parents + it.aliases } + "eclean.admin"
        assertTrue(definitions.keys.containsAll(expected))
        fun visit(node: String, ancestry: Set<String>) {
            assertFalse(node in ancestry, "Permission inheritance cycle: $ancestry -> $node")
            definitions.getValue(node).forEach { visit(it, ancestry + node) }
        }
        definitions.keys.forEach { visit(it, emptySet()) }
    }

    @Test
    fun `a granted aggregate or legacy alias supplies the canonical default`() {
        val target = PermissionNode.CLEAN_ENTITY.node
        assertTrue(NeoForgePermissionDefinitions.resolveDefault(target, false) { it == "eclean.command.clean" })
        assertTrue(NeoForgePermissionDefinitions.resolveDefault(target, false) { it == "eclean.clean.entity" })
        assertFalse(NeoForgePermissionDefinitions.resolveDefault(target, false) { it == "eclean.command.reload" })
    }

    @Test
    fun `admin grants defaults and ordinary ungranted users are denied`() {
        assertTrue(NeoForgePermissionDefinitions.resolveDefault(PermissionNode.TRASH_OPEN.node, false) {
            it == "eclean.admin"
        })
        assertFalse(NeoForgePermissionDefinitions.resolveDefault(PermissionNode.TRASH_OPEN.node, false) { false })
        assertFalse(NeoForgePermissionDefinitions.resolveDefault("eclean.admin", false) { true })
    }

    @Test
    fun `operator defaults do not query the handler recursively`() {
        assertTrue(NeoForgePermissionDefinitions.resolveDefault(PermissionNode.SHOW_TELEPORT.node, true) {
            error("Operator fallback should not query inherited defaults")
        })
    }
}
