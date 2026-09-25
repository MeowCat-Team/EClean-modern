package top.e404.eclean.config

import top.e404.eclean.PL

object ConfigRuntimeApplier {
    fun apply(bundle: ConfigBundle, changes: Set<ConfigSection> = ConfigSection.entries.toSet()) {
        if (changes.isEmpty()) return
        PL.services.cleanupTickService.start(bundle)
        PL.services.trashcanTicker.start(bundle)
        PL.services.statsAlertService.start(bundle)
        if (bundle.advanced.papi.enabled) PL.services.worldStatsService.startCache()
        else PL.services.worldStatsService.stopCache()
        PL.services.integrations.configure(bundle)
    }
}
