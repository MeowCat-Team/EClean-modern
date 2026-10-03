package top.e404.eclean.fabric.mixin

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import top.e404.eclean.fabric.ECleanFabric

@Mixin(ItemEntity::class)
abstract class ItemEntityDespawnMixin {
    /** Ordinal 1 is the expiry branch; ordinal 0 removes an empty stack. */
    @Inject(
        method = ["tick"],
        at = [At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1)],
        cancellable = true,
    )
    private fun ecleanRecoverExpiredItem(callback: CallbackInfo) {
        val target: Any = this
        val item = target as ItemEntity
        if (item.level() !is ServerLevel || item.age < 6000 || item.item.isEmpty) return
        if (ECleanFabric.tryRecoverDespawn(item)) callback.cancel()
    }
}
