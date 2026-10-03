package org.meowcat.eclean.common.api

import net.kyori.adventure.text.Component

/** Sends parsed Adventure components to players/command senders. */
interface MessageSender {
    fun sendPlayer(playerId: String, component: Component)
    fun sendConsole(component: Component)
    fun sendCommandSender(senderId: String, component: Component)
    fun broadcast(component: Component)
}
