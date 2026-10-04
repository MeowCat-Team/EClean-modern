package org.meowcat.eclean.neoforge

import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import org.meowcat.eclean.mod.ECleanMod

/** All game behavior is shared; this entrypoint supplies NeoForge's loader services. */
@Mod(value = "eclean", dist = [Dist.DEDICATED_SERVER])
class ECleanNeoForge(container: ModContainer) {
    init {
        val hooks = NeoForgeLoaderHooks(container)
        NeoForge.EVENT_BUS.register(hooks)
        ECleanMod.initialize(hooks)
    }
}
