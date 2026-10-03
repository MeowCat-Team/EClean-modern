package org.meowcat.eclean.fabric.platform

import com.mojang.serialization.JsonOps
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.core.HolderLookup
import net.minecraft.resources.RegistryOps
import net.minecraft.server.MinecraftServer

/** Preserve translations, style, click and hover events in vanilla client messages. */
object FabricText {
    fun native(component: Component, server: MinecraftServer): net.minecraft.network.chat.Component =
        native(component, server.registryAccess())

    fun native(component: Component, registries: HolderLookup.Provider): net.minecraft.network.chat.Component =
        ComponentSerialization.CODEC.parse(
            RegistryOps.create(JsonOps.INSTANCE, registries),
            GsonComponentSerializer.gson().serializeToTree(component),
        ).getOrThrow()
}
