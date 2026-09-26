package top.e404.eclean.feature.trashcan

import org.bukkit.inventory.ItemStack

typealias TrashcanEntry = StoredTrashcanEntry<ItemStack>

/** Paper's item semantics for the shared in-memory trash-can store. */
class TrashcanItemStore(
    lifetimeSeconds: () -> Long?,
    stackingEnabled: () -> Boolean = { true },
) {
    private val delegate = TrashcanStore(ItemStackAdapter, lifetimeSeconds, stackingEnabled)

    val size: Int get() = delegate.size
    fun isEmpty() = delegate.isEmpty()
    fun totalCount() = delegate.totalCount()
    fun addItem(item: ItemStack) = delegate.addItem(item)
    fun transferItem(item: ItemStack, removeSource: () -> Boolean) = delegate.transferItem(item, removeSource)
    fun addAll(stacks: Collection<ItemStack>) = delegate.addAll(stacks)
    fun removeItem(prototype: ItemStack, amount: Int) = delegate.removeItem(prototype, amount)
    fun removeItem(prototype: ItemStack, amount: Int, entryId: Long?) = delegate.removeItem(prototype, amount, entryId)
    fun expireEntries(nowMillis: Long) = delegate.expireEntries(nowMillis)
    fun getEntries(): List<TrashcanEntry> = delegate.getEntries()
    fun earliestDeadline() = delegate.earliestDeadline()
    fun clear() = delegate.clear()
}

private object ItemStackAdapter : StoredItemAdapter<ItemStack> {
    override fun copy(item: ItemStack): ItemStack = item.clone()
    override fun amount(item: ItemStack) = item.amount
    override fun withAmount(item: ItemStack, amount: Int): ItemStack = item.apply { this.amount = amount }
    override fun isEmpty(item: ItemStack) = item.type.isAir
    override fun isSimilar(first: ItemStack, second: ItemStack) = first.isSimilar(second)
}
