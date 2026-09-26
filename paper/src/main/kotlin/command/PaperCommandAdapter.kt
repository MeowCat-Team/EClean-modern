package top.e404.eclean.command

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.common.api.CommonLocation
import top.e404.eclean.common.api.CommonPlayer
import top.e404.eclean.lang.MLang

fun CommandSender.toCommon(): CommonCommandSender = object : CommonCommandSender {
    override val name: String = this@toCommon.name
    override fun hasPermission(node: String): Boolean = this@toCommon.hasEcleanPermission(node)
    override fun sendMessage(component: Component) {
        (this@toCommon as? Audience)?.sendMessage(component)
            ?: this@toCommon.sendMessage(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component)
            )
    }
}

fun Player.toCommonPlayer(): CommonPlayer = object : CommonPlayer {
    override val name: String = this@toCommonPlayer.name
    override val uniqueId: String = this@toCommonPlayer.uniqueId.toString()
    override val worldName: String = this@toCommonPlayer.world.name
    override val location: CommonLocation = CommonLocation(
        worldName = this@toCommonPlayer.world.name,
        x = this@toCommonPlayer.location.x,
        y = this@toCommonPlayer.location.y,
        z = this@toCommonPlayer.location.z,
    )
    override fun hasPermission(node: String): Boolean = this@toCommonPlayer.hasEcleanPermission(node)
    override fun sendMessage(component: Component) {
        (this@toCommonPlayer as? Audience)?.sendMessage(component)
            ?: this@toCommonPlayer.sendMessage(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component)
            )
    }
}

class PaperMessageProvider : MessageProvider {
    override fun itemName(type: String): Any = runCatching {
        top.e404.eclean.util.RichText(top.e404.eclean.util.miniMessage.serialize(
            Component.translatable(org.bukkit.Material.valueOf(type).translationKey())
                .append(Component.text(" ($type)"))))
    }.getOrDefault(type)
    override fun entityName(type: String): Any = runCatching {
        top.e404.eclean.util.RichText(top.e404.eclean.util.miniMessage.serialize(
            Component.translatable(org.bukkit.entity.EntityType.valueOf(type).translationKey())
                .append(Component.text(" ($type)"))))
    }.getOrDefault(type)
    override fun get(key: String, vararg args: Pair<String, Any>): String = MLang.get(key, *args)
}
