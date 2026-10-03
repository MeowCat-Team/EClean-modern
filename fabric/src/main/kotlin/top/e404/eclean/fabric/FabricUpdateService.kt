package top.e404.eclean.fabric

import net.fabricmc.loader.api.FabricLoader
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.config.updateChecksEnabled
import top.e404.eclean.update.UpdateChecker
import top.e404.eclean.update.UpdateFailure
import top.e404.eclean.util.miniMessage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Wall-clock update polling never performs HTTP work on the server tick. */
internal class FabricUpdateService(private val runtime: FabricRuntime) {
    private var executor: ScheduledExecutorService? = null
    private val checker = UpdateChecker(
        currentVersion = { FabricLoader.getInstance().getModContainer("eclean").orElseThrow().metadata.version.friendlyString },
        onAvailable = { notice -> announce(runtime.language["update.available",
            "latest" to notice.latest, "current" to notice.current, "url" to notice.url]) },
        onFailure = { failure ->
            val reason = when (failure) {
                UpdateFailure.RateLimited -> runtime.language["update.rate_limited"]
                is UpdateFailure.Other -> failure.reason
            }
            announce(runtime.language["update.failed", "reason" to reason])
        },
    )

    fun start(config: ConfigBundle) {
        if (!config.updateChecksEnabled) { stop(); return }
        if (executor != null) return
        val token = checker.start()
        executor = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "EClean-Fabric-Update").apply { isDaemon = true }
        }.also { it.scheduleAtFixedRate({ checker.check(token) }, 20, 6 * 60 * 60, TimeUnit.SECONDS) }
    }

    fun stop() { checker.stop(); executor?.shutdownNow(); executor = null }

    private fun announce(message: String) = runtime.messages.sendConsole(
        miniMessage.deserialize("${runtime.language["prefix"]} $message"))
}
