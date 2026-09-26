package top.e404.eclean.config

import top.e404.eclean.common.api.Scheduler
import top.e404.eclean.config.model.ConfigProfile
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

sealed interface ConfigurationReloadResult {
    data class Applied(val profile: ConfigProfile?) : ConfigurationReloadResult
    data class AlreadyActive(val profile: ConfigProfile) : ConfigurationReloadResult
    data class Failed(val error: Throwable) : ConfigurationReloadResult
}

data class ConfigurationSectionChange(val section: ConfigSection, val before: String, val after: String)
data class ConfigurationInspection(
    val profile: ConfigProfile,
    val changes: List<String>,
    val sections: List<ConfigurationSectionChange>,
)

/** Serializes disk work and commits on the loader's global scheduler. */
class ConfigurationJobs(
    private val configuration: ConfigurationManager,
    private val scheduler: Scheduler,
) {
    @Volatile private var stopped = false
    private val commitLock = Any()
    private val executor = Executors.newSingleThreadExecutor { work ->
        Thread(work, "EClean-config").apply { isDaemon = true }
    }

    fun reload(profile: ConfigProfile? = null, onComplete: (ConfigurationReloadResult) -> Unit) {
        submit {
            val outcome = try {
                if (profile != null && profile == configuration.currentProfile && configuration.ready) {
                    ConfigurationReloadResult.AlreadyActive(profile)
                } else {
                    val candidate = configuration.prepare(profile)
                    scheduler.submitGlobal {
                        synchronized(commitLock) {
                            if (!stopped) configuration.commit(candidate, persistProfile = profile != null)
                        }
                    }.join()
                    ConfigurationReloadResult.Applied(profile)
                }
            } catch (failure: Exception) {
                ConfigurationReloadResult.Failed(failure)
            }
            if (!stopped) onComplete(outcome)
        }
    }

    fun inspect(diff: Boolean, onComplete: (Result<ConfigurationInspection>) -> Unit) {
        submit {
            val result = runCatching {
                val candidate = configuration.inspect()
                if (!diff) ConfigurationInspection(candidate.profile, emptyList(), emptyList())
                else {
                    val previous = configuration.current
                    val changed = previous.diff(candidate.bundle).map { it.displayName }.toMutableList()
                    if (candidate.profile != configuration.currentProfile) changed.add("profile")
                    if (candidate.language != configuration.currentLanguage) changed.add("language")
                    val before = previous.sections()
                    val after = candidate.bundle.sections()
                    val sections = previous.diff(candidate.bundle).map { section ->
                        ConfigurationSectionChange(section, before.getValue(section), after.getValue(section))
                    }
                    ConfigurationInspection(candidate.profile, changed, sections)
                }
            }
            if (!stopped) onComplete(result)
        }
    }

    fun shutdown() {
        synchronized(commitLock) { stopped = true }
        executor.shutdownNow()
    }

    private fun submit(action: () -> Unit) {
        if (stopped) return
        try { executor.execute { if (!stopped) action() } }
        catch (_: RejectedExecutionException) { /* Shutdown won the race. */ }
    }
}
