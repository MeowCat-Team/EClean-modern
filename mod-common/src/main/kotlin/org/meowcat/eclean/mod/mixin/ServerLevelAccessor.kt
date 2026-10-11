package org.meowcat.eclean.mod.mixin

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.entity.LevelEntityGetter
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(ServerLevel::class)
interface ServerLevelAccessor {
    /** Virtual dispatch retains the active entity backend, including Moonrise's replacement. */
    @Invoker("getEntities")
    fun ecleanEntityGetter(): LevelEntityGetter<Entity>
}
