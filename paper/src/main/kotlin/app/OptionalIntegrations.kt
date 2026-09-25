package top.e404.eclean.app

import org.bukkit.Bukkit
import org.bstats.bukkit.Metrics
import top.e404.eclean.EClean
import top.e404.eclean.PL
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.update.Update

/** Every optional registration has an owner and a matching teardown. */
class OptionalIntegrations {
    private var metrics: Metrics? = null
    private var papi: Any? = null
    private var updateEnabled = false

    fun configure(config: ConfigBundle) {
        if (EClean.unit) return
        val wantPapi = config.advanced.papi.enabled && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
        if (!wantPapi) unregisterPapi()
        else if (papi == null) {
            val clazz = Class.forName("top.e404.eclean.feature.papi.native.ECleanPapiExpansion")
            val expansion = clazz.getDeclaredConstructor().newInstance()
            check(clazz.getMethod("register").invoke(expansion) == true) { "PlaceholderAPI registration failed" }
            papi = expansion
        }
        val wantUpdate = config.global.updateCheck && config.advanced.update.enabled
        if (wantUpdate != updateEnabled) {
            if (wantUpdate) Update.register() else Update.stop()
            updateEnabled = wantUpdate
        }
        if (!config.advanced.bStats.enabled) { metrics?.shutdown(); metrics = null }
        else if (metrics == null) metrics = Metrics(PL, 33735)
    }

    fun stop() {
        Update.stop()
        updateEnabled = false
        unregisterPapi()
        metrics?.shutdown()
        metrics = null
    }

    private fun unregisterPapi() {
        papi?.let { it.javaClass.getMethod("unregister").invoke(it) }
        papi = null
    }
}
