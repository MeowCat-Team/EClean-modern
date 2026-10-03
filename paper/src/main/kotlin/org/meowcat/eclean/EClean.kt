package org.meowcat.eclean

import org.bukkit.plugin.java.JavaPlugin
import org.meowcat.eclean.app.RuntimeServices
import org.meowcat.eclean.command.Commands
import org.meowcat.eclean.lang.MLang
import org.meowcat.eclean.listener.DespawnListener
import org.meowcat.eclean.menu.MenuManager

open class EClean : JavaPlugin {
    companion object {
        @Volatile
        var unit = false
    }

    val prefix get() = MLang["prefix"]

    lateinit var services: RuntimeServices
        private set

    @Suppress("UNUSED")
    constructor() : super()

    init {
        PL = this
    }

    override fun onEnable() {
        services = RuntimeServices()
        services.load()
        Commands.register()
        services.commonPlatform.eventBus.register(DespawnListener)
        services.commonPlatform.eventBus.register(MenuManager)
        services.messages.info("EClean-Modern enabled. Author: 404E")
    }

    override fun onDisable() {
        services.shutdown()
        MenuManager.shutdown()
        services.messages.info("EClean-Modern disabled")
    }
}

lateinit var PL: EClean
    private set
