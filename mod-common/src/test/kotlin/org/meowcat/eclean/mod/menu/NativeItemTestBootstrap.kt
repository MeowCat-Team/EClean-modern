package org.meowcat.eclean.mod.menu

import net.minecraft.SharedConstants
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.data.registries.VanillaRegistries
import net.minecraft.server.Bootstrap

/** In 26.x registry item components are bound after vanilla registry bootstrap. */
internal object NativeItemTestBootstrap {
    init {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        // 26.3 splits createLookup into world and reloadable registries. Reflection keeps this
        // test-only bootstrap usable against all three native compile classpaths.
        val factory = VanillaRegistries::class.java
        val legacy = factory.methods.firstOrNull { it.name == "createLookup" && it.parameterCount == 0 }
        val registries = if (legacy != null) {
            legacy.invoke(null) as HolderLookup.Provider
        } else {
            val world = factory.getMethod("createWorldLookup").invoke(null) as HolderLookup.Provider
            factory.getMethod("createReloadableLookup", HolderLookup.Provider::class.java)
                .invoke(null, world) as HolderLookup.Provider
        }
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries).forEach { it.apply() }
    }

    fun initialize() = Unit
}
