package org.meowcat.eclean.common.api

/**
 * Provides online players in a platform-agnostic way.
 */
interface PlayerProvider {
    fun onlinePlayers(): List<org.meowcat.eclean.common.api.CommonPlayer>
}
