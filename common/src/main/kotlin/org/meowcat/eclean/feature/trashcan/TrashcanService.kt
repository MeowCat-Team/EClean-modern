package org.meowcat.eclean.feature.trashcan

import org.meowcat.eclean.common.api.CommonPlayer

/**
 * Platform-agnostic trash-can service.
 */
interface TrashcanService {
    val enabled: Boolean
    fun open(player: CommonPlayer)
    fun entries(): List<TrashcanEntryView>
}