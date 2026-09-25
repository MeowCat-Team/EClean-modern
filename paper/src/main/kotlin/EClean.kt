package top.e404.eclean

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import top.e404.eclean.app.RuntimeServices
import top.e404.eclean.command.Commands
import top.e404.eclean.config.Config
import top.e404.eclean.lang.MLang
import top.e404.eclean.listener.DespawnListener
import top.e404.eclean.menu.MenuManager
import top.e404.eclean.update.Update

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
