package org.meowcat.eclean.neoforge.mixin

import net.minecraft.world.entity.item.ItemEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

/** A failed expiry transfer needs one tick of grace even at NeoForge's short lifespan limit. */
@Mixin(ItemEntity::class)
interface ItemEntityAgeAccessor {
    @Accessor("age")
    fun ecleanSetAge(age: Int)
}
