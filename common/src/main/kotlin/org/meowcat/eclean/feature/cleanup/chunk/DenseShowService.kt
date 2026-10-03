package org.meowcat.eclean.feature.cleanup.chunk

import org.meowcat.eclean.common.api.CommonPlayer

/**
 * Opens the dense-entity viewer menu for a player.
 */
interface DenseShowService {
    fun show(player: CommonPlayer)
}
