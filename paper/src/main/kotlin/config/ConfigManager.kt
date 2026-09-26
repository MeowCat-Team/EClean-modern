package top.e404.eclean.config

import org.bukkit.command.CommandSender
import top.e404.eclean.PL
import top.e404.eclean.config.model.ConfigProfile
import top.e404.eclean.lang.LanguageSnapshot

/** Compatibility facade; all state and transactions belong to the current runtime instance. */
object ConfigManager {
    private val backend get() = PL.services.configuration
    val current get() = backend.current
    val currentProfile get() = backend.currentProfile
    val currentLanguage get() = backend.currentLanguage
    val revision get() = backend.revision
    val ready get() = backend.ready
    fun inspect() = backend.inspect()
    fun prepare(profile: ConfigProfile? = null) = backend.prepare(profile)
    fun commit(candidate: RuntimeConfiguration, persistProfile: Boolean = false) = backend.commit(candidate, persistProfile)
    fun loadAll(sender: CommandSender? = null) = backend.loadAll()
    fun reloadAll(sender: CommandSender? = null) = backend.reloadAll()
    fun switchProfile(profile: ConfigProfile) = backend.switchProfile(profile)
    fun replaceSnapshotForTest(bundle: ConfigBundle) = backend.replaceSnapshotForTest(bundle)
    fun update(transform: (ConfigBundle) -> ConfigBundle) = backend.update(transform)
    fun updateLanguage(language: LanguageSnapshot) = backend.updateLanguage(language)
}
