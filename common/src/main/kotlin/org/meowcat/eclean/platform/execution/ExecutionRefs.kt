package org.meowcat.eclean.platform.execution

import java.util.UUID

data class ChunkRef(
    val world: String,
    val x: Int,
    val z: Int,
)

data class EntityRef(
    val uuid: UUID,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
)

data class PlayerRef(
    val uuid: UUID,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
)

fun ChunkRef.info(): String =
    "x: ${x * 16}..${x * 16 + 15}, z: ${z * 16}..${z * 16 + 15}"
