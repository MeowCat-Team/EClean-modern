package org.meowcat.eclean.common.util

import org.meowcat.eclean.common.api.Platform

/**
 * Common utilities for checking online players.
 * These delegate to the platform's [ServerInfo].
 */
fun Platform.hasOnlinePlayers(): Boolean =
    serverInfo.hasOnlinePlayers
