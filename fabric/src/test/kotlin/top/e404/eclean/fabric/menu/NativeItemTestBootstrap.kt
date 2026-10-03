package top.e404.eclean.fabric.menu

import net.minecraft.SharedConstants
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.server.Bootstrap

/** In 26.x registry item components are bound after vanilla registry bootstrap. */
internal object NativeItemTestBootstrap {
    init {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val registries = VanillaRegistries.createLookup()
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach { it.apply() }
    }

    fun initialize() = Unit
}
