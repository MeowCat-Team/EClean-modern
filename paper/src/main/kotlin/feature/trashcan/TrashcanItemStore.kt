package top.e404.eclean.feature.trashcan

import org.bukkit.inventory.ItemStack
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * 垃圾桶聚合条目
 *
 * @param id        条目唯一 id，用于按具体条目扣减（尤其是 stacking=false 时）
 * @param prototype 该条目的 isSimilar 键(amount 恒为 1), 用于合并/匹配
 * @param count     聚合后的总数量, 可超过 maxStackSize
 * @param deadline  条目到期时间戳(epoch millis), Long.MAX_VALUE 表示永不过期
 */
data class TrashcanEntry(
    val id: Long,
    val prototype: ItemStack,
    var count: Long,
    var deadline: Long,
)

/**
 * 垃圾桶物品存储: 按 isSimilar 聚合成条目, 每个条目数量不限
 *
 * @param lifetimeSeconds 条目存活秒数(读取时求值), null 表示永不过期
 * @param stackingEnabled 是否启用聚合, false 时每次放入都新建条目
 */
class TrashcanItemStore(
    private val lifetimeSeconds: () -> Long?,
    private val stackingEnabled: () -> Boolean = { true },
) {
    private val lock = ReentrantReadWriteLock()
    private val entries = mutableListOf<TrashcanEntry>()
    private val idCounter = AtomicLong(0)

    val size: Int
        get() {
            lock.readLock().lock()
            try {
                return entries.size
            } finally {
                lock.readLock().unlock()
            }
        }

    fun isEmpty(): Boolean {
        lock.readLock().lock()
        try {
            return entries.isEmpty()
        } finally {
            lock.readLock().unlock()
        }
    }

    fun totalCount(): Long {
        lock.readLock().lock()
        try {
            return entries.sumOf { it.count }
        } finally {
            lock.readLock().unlock()
        }
    }

    /** 放入物品: 合并到相似条目(不重置其到期时间), 否则新建条目。 */
    fun addItem(item: ItemStack): Boolean = transferItem(item) { true }

    /**
     * Transfer ownership while holding the store write lock. Preparation happens before removing
     * the source; readers cannot withdraw or expire the pending entry. A false result or exception
     * rolls back only this deposit. The callback must return true iff its source no longer exists.
     * This is an in-memory transaction; it does not provide durability across server crashes.
     */
    fun transferItem(item: ItemStack, removeSource: () -> Boolean): Boolean {
        val snapshot = item.clone()
        val amount = snapshot.amount.toLong()
        if (amount <= 0 || snapshot.type.isAir) return false
        val stacking = stackingEnabled()
        val lifetime = lifetimeSeconds()
        lock.writeLock().lock()
        try {
            val now = System.currentTimeMillis()
            entries.removeAll { it.deadline <= now }
            val existing = if (stacking) entries.firstOrNull {
                it.deadline > now && it.prototype.isSimilar(snapshot)
            } else null
            val previousCount = existing?.count ?: 0L
            val pending = existing ?: TrashcanEntry(
                id = idCounter.incrementAndGet(),
                prototype = snapshot.apply { this.amount = 1 },
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
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun addAll(stacks: Collection<ItemStack>): Int {
        var added = 0
        for (stack in stacks) {
            if (addItem(stack)) added++
        }
        return added
    }

    /** 取出物品: 从相似条目中扣除数量, 扣完的条目移除; 返回实际取出的数量 */
    fun removeItem(prototype: ItemStack, amount: Int): Int =
        removeItem(prototype, amount, null)

    /** 取出物品: 可指定只从某个条目扣除（stacking=false 时按被点击条目扣减） */
    fun removeItem(prototype: ItemStack, amount: Int, entryId: Long?): Int {
        lock.writeLock().lock()
        try {
            var remaining = amount.toLong()
            val now = System.currentTimeMillis()
            val iterator = entries.listIterator()
            while (iterator.hasNext() && remaining > 0) {
                val entry = iterator.next()
                if (entry.deadline <= now) {
                    iterator.remove()
                    continue
                }
                if (!entry.prototype.isSimilar(prototype)) continue
                if (entryId != null && entry.id != entryId) continue
                val toRemove = minOf(remaining, entry.count)
                val newCount = entry.count - toRemove
                if (newCount <= 0) {
                    iterator.remove()
                } else {
                    entry.count = newCount
                }
                remaining -= toRemove
            }
            return (amount - remaining).toInt()
        } finally {
            lock.writeLock().unlock()
        }
    }

    /** 移除所有已到期的条目, 返回移除的条目数 */
    fun expireEntries(nowMillis: Long): Int {
        lock.writeLock().lock()
        try {
            val before = entries.size
            entries.removeAll { it.deadline <= nowMillis }
            return before - entries.size
        } finally {
            lock.writeLock().unlock()
        }
    }

    /** 条目快照(插入顺序), 每个条目为独立拷贝 */
    fun getEntries(): List<TrashcanEntry> {
        lock.readLock().lock()
        try {
            return entries.map { it.copy(prototype = it.prototype.clone()) }
        } finally {
            lock.readLock().unlock()
        }
    }

    /** 最早到期条目的时间戳; 没有条目或全部永不过期时返回 null */
    fun earliestDeadline(): Long? {
        lock.readLock().lock()
        try {
            return entries.asSequence()
                .map { it.deadline }
                .filter { it != Long.MAX_VALUE }
                .minOrNull()
        } finally {
            lock.readLock().unlock()
        }
    }

    fun clear(): Long {
        lock.writeLock().lock()
        try {
            val removed = entries.sumOf { it.count }
            entries.clear()
            return removed
        } finally {
            lock.writeLock().unlock()
        }
    }
}
