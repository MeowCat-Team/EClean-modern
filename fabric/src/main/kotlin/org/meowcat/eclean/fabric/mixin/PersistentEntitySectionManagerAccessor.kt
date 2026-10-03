package org.meowcat.eclean.fabric.mixin

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.entity.EntitySectionStorage
import net.minecraft.world.level.entity.PersistentEntitySectionManager
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Accessor

@Mixin(PersistentEntitySectionManager::class)
interface PersistentEntitySectionManagerAccessor {
    @Accessor("sectionStorage")
    fun ecleanEntitySections(): EntitySectionStorage<Entity>
}
