package top.e404.eclean.config

import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.config.model.ConfigProfile

object Config {
    private val backend get() = PL.services.configuration

    val current: ConfigBundle
        get() = backend.current

    val profile: ConfigProfile
        get() = backend.currentProfile

    fun load(sender: CommandSender? = null) {
        backend.loadAll()
    }

    fun reload(sender: CommandSender? = null) {
        backend.reloadAll()
    }

    fun switchProfile(profile: ConfigProfile): ConfigProfile =
        backend.switchProfile(profile)

    fun replaceForTest(bundle: ConfigBundle) {
        backend.replaceSnapshotForTest(bundle)
    }

    fun update(transform: (ConfigBundle) -> ConfigBundle) {
        backend.update(transform)
    }
}
