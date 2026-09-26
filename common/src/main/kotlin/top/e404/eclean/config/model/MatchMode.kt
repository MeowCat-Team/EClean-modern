package top.e404.eclean.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MatchMode {
    @SerialName("remove-matching") REMOVE_MATCHING,
    @SerialName("keep-matching") KEEP_MATCHING,
}
