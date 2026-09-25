package top.e404.eclean.config

/** Publication is one volatile write; activation failure restores the previous services. */
class ConfigurationStore<T>(initial: T) {
    @Volatile var current: T = initial
        private set

    @Synchronized fun commit(candidate: T, activate: (T) -> Unit, persist: () -> Unit = {}) {
        val previous = current
        try {
            activate(candidate)
            persist()
            current = candidate
        } catch (failure: Exception) {
            try { activate(previous) } catch (rollback: Exception) { failure.addSuppressed(rollback) }
            throw failure
        }
    }

    @Synchronized fun update(transform: (T) -> T) { current = transform(current) }
}
