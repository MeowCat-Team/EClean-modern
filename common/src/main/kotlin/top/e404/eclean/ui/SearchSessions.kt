package top.e404.eclean.ui

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** One private input session per actor; stale chat and timeout callbacks cannot claim a replacement. */
class SearchSessions<K : Any, V : Any>(private val nowMillis: () -> Long = System::currentTimeMillis) {
    class Session<V>(val value: V, val expiresAtMillis: Long) {
        private val claimed = AtomicBoolean()
        fun claim(): Boolean = claimed.compareAndSet(false, true)
    }

    private val sessions = ConcurrentHashMap<K, Session<V>>()

    fun begin(key: K, value: V, timeoutMillis: Long): Session<V> =
        Session(value, nowMillis() + timeoutMillis).also { sessions[key] = it }

    fun current(key: K): Session<V>? = sessions[key]
    fun isCurrent(key: K, session: Session<V>): Boolean = sessions[key] === session
    fun remove(key: K): Session<V>? = sessions.remove(key)
    fun remove(key: K, session: Session<V>): Boolean = sessions.remove(key, session)
    fun expired(session: Session<V>): Boolean = nowMillis() >= session.expiresAtMillis
    fun clear() = sessions.clear()
}
