package org.meowcat.eclean.mod

import org.meowcat.eclean.config.ConfigBundle
import org.meowcat.eclean.config.updateChecksEnabled
import org.meowcat.eclean.update.UpdateChecker
import org.meowcat.eclean.update.UpdateFailure
import org.meowcat.eclean.util.miniMessage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Wall-clock update polling never performs HTTP work on the server tick. */
internal class ModUpdateService(private val runtime: ModRuntime) {
    private var executor: ScheduledExecutorService? = null
    private val checker = UpdateChecker(
        currentVersion = { runtime.loader.version },
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
            Thread(task, "EClean-Mod-Update").apply { isDaemon = true }
        }.also { it.scheduleAtFixedRate({ checker.check(token) }, 20, 6 * 60 * 60, TimeUnit.SECONDS) }
    }

    fun stop() { checker.stop(); executor?.shutdownNow(); executor = null }

    private fun announce(message: String) = runtime.messages.sendConsole(
        miniMessage.deserialize("${runtime.language["prefix"]} $message"))
}
