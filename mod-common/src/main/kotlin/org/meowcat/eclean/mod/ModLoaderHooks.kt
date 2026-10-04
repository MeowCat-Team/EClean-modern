package org.meowcat.eclean.mod

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import java.nio.file.Path

/** Loader metadata and permission backends stay outside the shared Minecraft implementation. */
interface ModLoaderHooks {
    val loaderName: String
    val configDirectory: Path
    val version: String
    val resolvesPermissionHierarchy: Boolean get() = false

    /** Explicit grants/denials override the shared operator default; null means undefined. */
    fun permission(source: CommandSourceStack, node: String): Boolean? = null

    /** Pass undecorated input to menus; false consumes the message before public broadcast. */
    fun registerChatInput(handler: (ServerPlayer, String) -> Boolean)
}
