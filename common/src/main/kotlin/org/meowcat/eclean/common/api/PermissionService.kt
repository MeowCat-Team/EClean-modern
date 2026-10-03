package org.meowcat.eclean.common.api

interface PermissionService {
    fun hasPermission(playerId: String, node: String): Boolean
    fun registerPermission(node: String, default: Boolean, description: String)
}
