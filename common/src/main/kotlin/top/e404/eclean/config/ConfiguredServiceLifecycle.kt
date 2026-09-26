package top.e404.eclean.config

data class ManagedConfiguredService(
    val start: (ConfigBundle) -> Unit,
    val stop: () -> Unit,
)

/** Starts and stops configured features in one place for every loader. */
class ConfiguredServiceLifecycle(private val services: List<ManagedConfiguredService>) {
    fun activate(bundle: ConfigBundle) { services.forEach { it.start(bundle) } }
    fun stop() { services.forEach { it.stop() } }
}
