package org.meowcat.eclean.app

import org.bukkit.Bukkit
import org.bstats.bukkit.Metrics
import org.meowcat.eclean.EClean
import org.meowcat.eclean.PL
import org.meowcat.eclean.config.updateChecksEnabled
import org.meowcat.eclean.config.ConfigBundle
import org.meowcat.eclean.lang.MLang
import org.meowcat.eclean.update.UpdateChecker
import org.meowcat.eclean.update.UpdateFailure
import java.util.concurrent.TimeUnit

/** Every optional registration has an owner and a matching teardown. */
class OptionalIntegrations {
    private var metrics: Metrics? = null
    private var papi: Any? = null
    private var updateEnabled = false
    private var updateTask: io.papermc.paper.threadedregions.scheduler.ScheduledTask? = null
    private val updateChecker = UpdateChecker(
        currentVersion = { PL.pluginMeta.version },
        onAvailable = { notice ->
            PL.services.messages.send(Bukkit.getConsoleSender(), MLang[
                "update.available", "latest" to notice.latest, "current" to notice.current, "url" to notice.url,
            ])
        },
        onFailure = { failure ->
            val reason = when (failure) {
                UpdateFailure.RateLimited -> MLang["update.rate_limited"]
                is UpdateFailure.Other -> failure.reason
            }
            PL.services.messages.send(Bukkit.getConsoleSender(), MLang["update.failed", "reason" to reason])
        },
    )

    fun configure(config: ConfigBundle) {
        if (EClean.unit) return
        val wantPapi = config.advanced.papi.enabled && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
        if (!wantPapi) unregisterPapi()
        else if (papi == null) {
            val clazz = Class.forName("org.meowcat.eclean.feature.papi.native.ECleanPapiExpansion")
            val expansion = clazz.getDeclaredConstructor().newInstance()
            check(clazz.getMethod("register").invoke(expansion) == true) { "PlaceholderAPI registration failed" }
            papi = expansion
        }
        val wantUpdate = config.updateChecksEnabled
        if (wantUpdate != updateEnabled) {
            if (wantUpdate) startUpdate() else stopUpdate()
            updateEnabled = wantUpdate
        }
        if (!config.advanced.bStats.enabled) { metrics?.shutdown(); metrics = null }
        else if (metrics == null) metrics = Metrics(PL, 33735)
    }

    fun stop() {
        stopUpdate()
        updateEnabled = false
        unregisterPapi()
        metrics?.shutdown()
        metrics = null
    }

    private fun unregisterPapi() {
        papi?.let { it.javaClass.getMethod("unregister").invoke(it) }
        papi = null
    }

    private fun startUpdate() {
        val token = updateChecker.start()
        updateTask = PL.server.asyncScheduler.runAtFixedRate(
            PL, { _ -> updateChecker.check(token) }, 20L, 6L * 60 * 60, TimeUnit.SECONDS,
        )
    }

    private fun stopUpdate() {
        updateChecker.stop()
        updateTask?.cancel()
        updateTask = null
    }
}
