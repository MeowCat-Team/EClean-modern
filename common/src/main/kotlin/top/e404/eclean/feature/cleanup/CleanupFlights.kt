package top.e404.eclean.feature.cleanup

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Services are cheap facades; the gate is shared across their instances for one platform. */
object CleanupFlights {
    private data class Key(val owner: Any, val kind: String, val world: String, val preview: Boolean)
    private val active = ConcurrentHashMap<Key, CompletableFuture<Any>>()

    fun <R : Any> run(owner: Any, kind: String, world: String, preview: Boolean,
        empty: R, action: ((R) -> Unit) -> Unit, onComplete: (R) -> Unit) {
        val key = Key(owner, kind, world, preview)
        val created = CompletableFuture<Any>()
        val existing = active.putIfAbsent(key, created)
        (existing ?: created).whenComplete { result, _ ->
            @Suppress("UNCHECKED_CAST")
            onComplete(result as? R ?: empty)
        }
        if (existing != null) return
        try {
            action { result ->
                active.remove(key, created)
                created.complete(result)
            }
        } catch (error: Throwable) {
            active.remove(key, created)
            created.completeExceptionally(error)
        }
    }
}
