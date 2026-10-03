package org.meowcat.eclean.fabric.trash

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import org.meowcat.eclean.config.ConfigBundle
import org.meowcat.eclean.feature.trashcan.StoredItemAdapter
import org.meowcat.eclean.feature.trashcan.StoredTrashcanEntry
import org.meowcat.eclean.feature.trashcan.TrashcanStore
import org.meowcat.eclean.fabric.platform.FabricWorldAccess

typealias FabricTrashcanEntry = StoredTrashcanEntry<ItemStack>

/** Keeps complete native data components while the shared store manages counts and expiry. */
class FabricTrashcanStore(config: () -> ConfigBundle) {
    private val delegate = TrashcanStore(
        NativeItemAdapter,
        lifetimeSeconds = { config().trashcan.clearIntervalSeconds },
        stackingEnabled = { config().trashcan.stacking.enabled },
    )

    val size: Int get() = delegate.size
    fun isEmpty() = delegate.isEmpty()
    fun totalCount() = delegate.totalCount()
    fun addItem(item: ItemStack) = delegate.addItem(item)
    fun transferItem(item: ItemStack, removeSource: () -> Boolean) = delegate.transferItem(item, removeSource)
    fun addAll(items: Collection<ItemStack>) = delegate.addAll(items)
    fun removeItem(prototype: ItemStack, amount: Int, entryId: Long? = null) =
        delegate.removeItem(prototype, amount, entryId)
    fun expireEntries(nowMillis: Long) = delegate.expireEntries(nowMillis)
    fun getEntries(): List<FabricTrashcanEntry> = delegate.getEntries()
    fun earliestDeadline() = delegate.earliestDeadline()
    fun clear() = delegate.clear()
}

fun ItemStack.trashType(): String = FabricWorldAccess.registryType(BuiltInRegistries.ITEM.getKey(item))
fun ItemStack.trashId(): String = BuiltInRegistries.ITEM.getKey(item).toString()

private object NativeItemAdapter : StoredItemAdapter<ItemStack> {
    override fun copy(item: ItemStack): ItemStack = item.copy()
    override fun amount(item: ItemStack): Int = item.count
    override fun withAmount(item: ItemStack, amount: Int): ItemStack = item.copyWithCount(amount)
    override fun isEmpty(item: ItemStack): Boolean = item.isEmpty
    override fun isSimilar(first: ItemStack, second: ItemStack): Boolean =
        ItemStack.isSameItemSameComponents(first, second)
}
