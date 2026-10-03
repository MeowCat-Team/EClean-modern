package org.meowcat.eclean.feature.trashcan

data class TrashcanEntryView(
    val type: String,
    val count: Long,
    val deadline: Long,
)