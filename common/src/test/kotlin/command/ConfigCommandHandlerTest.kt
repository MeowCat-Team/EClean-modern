package command

import java.nio.file.Files
import net.kyori.adventure.text.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.e404.eclean.command.ConfigCommandHandler
import top.e404.eclean.common.api.CommonCommandSender
import top.e404.eclean.config.ConfigLoader
import top.e404.eclean.config.ConfigurationManager
import top.e404.eclean.config.model.ConfigProfile
import top.e404.eclean.lang.LanguageManager

class ConfigCommandHandlerTest {
    private class Sender(private val allowed: Boolean) : CommonCommandSender {
        override val name = "tester"
        override fun hasPermission(node: String) = allowed && node == "eclean.command.config"
        override fun sendMessage(component: Component) = Unit
    }

    @Test fun `permission and profile selection are handled without a loader API`() {
        val directory = Files.createTempDirectory("eclean-config-command")
        val configuration = ConfigurationManager(
            ConfigLoader(directory = { directory.toFile() }), LanguageManager(directory),
            activate = {}, deactivate = {}, onLoadFailure = { throw it },
        )
        val feedback = mutableListOf<String>()
        val reloads = mutableListOf<ConfigProfile>()
        val handler = ConfigCommandHandler(
            configuration = configuration,
            worldExists = { it == "world" },
            normalPath = { "normal-path" }, devPath = { "dev-path" },
            inspect = {}, reload = reloads::add,
            feedback = { key, _ -> feedback += key },
        )
        handler.handle(Sender(false), arrayOf("config", "profile", "dev"))
        assertEquals(listOf("command.no_permission"), feedback)
        assertTrue(reloads.isEmpty())
        feedback.clear()
        handler.handle(Sender(true), arrayOf("config", "profile", "dev"))
        assertEquals(listOf(ConfigProfile.DEV), reloads)
        handler.handle(Sender(true), arrayOf("config", "effective", "missing"))
        assertEquals("command.invalid.world", feedback.single())
    }
}
