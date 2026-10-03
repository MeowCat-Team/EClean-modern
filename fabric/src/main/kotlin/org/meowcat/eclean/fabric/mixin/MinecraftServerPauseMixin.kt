package org.meowcat.eclean.fabric.mixin

import net.minecraft.server.MinecraftServer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.ModifyVariable
import org.meowcat.eclean.fabric.ECleanFabric

@Mixin(MinecraftServer::class)
abstract class MinecraftServerPauseMixin {
    /** Both supported versions store pauseWhenEmptySeconds * 20 as the first int local. */
    @ModifyVariable(method = ["tickServer"], at = [At(value = "STORE", ordinal = 0)], ordinal = 0)
    private fun ecleanKeepRequiredTicks(pauseTicks: Int): Int {
        val target: Any = this
        return if (ECleanFabric.shouldKeepTicking(target as MinecraftServer)) 0 else pauseTicks
    }
}
