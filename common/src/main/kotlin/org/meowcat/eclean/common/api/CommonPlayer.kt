package org.meowcat.eclean.common.api

interface CommonCommandSender {
    val name: String
    fun hasPermission(node: String): Boolean
    fun sendMessage(component: net.kyori.adventure.text.Component)
}

interface CommonPlayer : org.meowcat.eclean.common.api.CommonCommandSender {
    val uniqueId: String
    val worldName: String
    val location: org.meowcat.eclean.common.api.CommonLocation
}
