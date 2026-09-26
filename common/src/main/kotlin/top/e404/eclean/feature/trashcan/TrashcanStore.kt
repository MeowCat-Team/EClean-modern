package top.e404.eclean.feature.trashcan

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock

/** Native items stay with the loader; copies must preserve metadata and similarity must ignore amount. */
interface StoredItemAdapter<T> {
    fun copy(item: T): T
    fun amount(item: T): Int
    fun withAmount(item: T, amount: Int): T
    fun isEmpty(item: T): Boolean
    fun isSimilar(first: T, second: T): Boolean
}

data class StoredTrashcanEntry<T>(
    val id: Long,
    val prototype: T,
    var count: Long,
    var deadline: Long,
)

class TrashcanStore<T>(
    private val adapter: StoredItemAdapter<T>,
    private val lifetimeSeconds: () -> Long?,
    private val stackingEnabled: () -> Boolean = { true },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = ReentrantReadWriteLock()
    private val entries = mutableListOf<StoredTrashcanEntry<T>>()
    private val idCounter = AtomicLong()

    val size: Int
        get() {
            lock.readLock().lock()
            try { return entries.size } finally { lock.readLock().unlock() }
        }

    fun isEmpty(): Boolean {
        lock.readLock().lock()
        try { return entries.isEmpty() } finally { lock.readLock().unlock() }
    }

    fun totalCount(): Long {
        lock.readLock().lock()
        try { return entries.sumOf { it.count } } finally { lock.readLock().unlock() }
    }

    fun addItem(item: T): Boolean = transferItem(item) { true }

    /**
     * Source removal runs under the write lock so a pending deposit is never visible.
     * The callback returns true only after its source is gone; false or an exception rolls back
     * this deposit. Like the original Paper store, this transaction is not durable across crashes.
     */
    fun transferItem(item: T, removeSource: () -> Boolean): Boolean {
        val snapshot = adapter.copy(item)
        val amount = adapter.amount(snapshot).toLong()
        if (amount <= 0 || adapter.isEmpty(snapshot)) return false
        val stacking = stackingEnabled()
        val lifetime = lifetimeSeconds()
        lock.writeLock().lock()
        try {
            val now = nowMillis()
            entries.removeAll { it.deadline <= now }
            val existing = if (stacking) entries.firstOrNull {
                it.deadline > now && adapter.isSimilar(it.prototype, snapshot)
            } else null
            val previousCount = existing?.count ?: 0L
            val pending = existing ?: StoredTrashcanEntry(
                id = idCounter.incrementAndGet(),
                prototype = adapter.withAmount(snapshot, 1),
                count = amount,
                deadline = if (lifetime == null) Long.MAX_VALUE else
                    Math.addExact(now, Math.multiplyExact(lifetime, 1000L)),
            )
            if (existing == null) entries.add(pending)
            else existing.count = Math.addExact(previousCount, amount)
            var transferred = false
            try {
                transferred = removeSource()
                return transferred
            } finally {
                if (!transferred) {
                    if (existing == null) entries.remove(pending)
                    else existing.count = previousCount
                }
            }
        } finally { lock.writeLock().unlock() }
    }

    fun addAll(items: Collection<T>): Int = items.count(::addItem)

    fun removeItem(prototype: T, amount: Int, entryId: Long? = null): Int {
        lock.writeLock().lock()
        try {
            var remaining = amount.toLong()
            val now = nowMillis()
            val iterator = entries.listIterator()
            while (iterator.hasNext() && remaining > 0) {
                val entry = iterator.next()
                if (entry.deadline <= now) { iterator.remove(); continue }
                if (!adapter.isSimilar(entry.prototype, prototype) || (entryId != null && entry.id != entryId)) continue
                val taken = minOf(remaining, entry.count)
                entry.count -= taken
                if (entry.count <= 0) iterator.remove()
                remaining -= taken
            }
            return (amount - remaining).toInt()
        } finally { lock.writeLock().unlock() }
    }

    fun expireEntries(nowMillis: Long): Int {
        lock.writeLock().lock()
        try {
            val before = entries.size
            entries.removeAll { it.deadline <= nowMillis }
            return before - entries.size
        } finally { lock.writeLock().unlock() }
    }

    fun getEntries(): List<StoredTrashcanEntry<T>> {
        lock.readLock().lock()
        try { return entries.map { it.copy(prototype = adapter.copy(it.prototype)) } }
        finally { lock.readLock().unlock() }
    }

    fun earliestDeadline(): Long? {
        lock.readLock().lock()
        try { return entries.asSequence().map { it.deadline }.filter { it != Long.MAX_VALUE }.minOrNull() }
        finally { lock.readLock().unlock() }
    }

    fun clear(): Long {
        lock.writeLock().lock()
        try {
            val removed = entries.sumOf { it.count }
            entries.clear()
            return removed
        } finally { lock.writeLock().unlock() }
    }
}
