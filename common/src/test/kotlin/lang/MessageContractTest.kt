package lang

import java.nio.file.Files
import kotlin.test.*
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.meowcat.eclean.lang.LanguageManager
import org.meowcat.eclean.util.*

class MessageContractTest {
    private val parameters = Regex("\\{([A-Za-z0-9_]+)\\}")

    @Test fun `bundled translations have identical parameter contracts and render without unresolved tokens`() {
        val manager = LanguageManager(java.nio.file.Path.of("."))
        val en = manager.bundledSnapshot("en_us").templates
        val zh = manager.bundledSnapshot("zh_cn").templates
        for (key in en.keys) {
            val expected = parameters.findAll(en.getValue(key)).map { it.groupValues[1] }.toSet()
            assertEquals(expected, parameters.findAll(zh.getValue(key)).map { it.groupValues[1] }.toSet(), key)
            for (template in listOf(en.getValue(key), zh.getValue(key))) {
                val rendered = template.placeholder(expected.associateWith { "<click:run_command:/op attacker>test</click>" })
                assertFalse(parameters.containsMatchIn(rendered), key)
                val component = miniMessage.deserialize(rendered)
                fun inspect(node: net.kyori.adventure.text.Component) {
                    assertNull(node.clickEvent(), "Injected action in $key")
                    node.children().forEach(::inspect)
                }
                inspect(component)
            }
        }
    }

    @Test fun `substitution is single pass and markup requires explicit trusted wrapper`() {
        val template = "<green>{a} {b}</green>"
        val value = template.placeholder("a" to "{b}", "b" to "<red>untrusted</red>")
        val plain = PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(value))
        assertEquals("{b} <red>untrusted</red>", plain)
        assertEquals("<green><red>trusted</red> x</green>", template.placeholder("a" to RichText("<red>trusted</red>"), "b" to "x"))
    }

    @Test fun `quotes in command and hover text cannot create extra MiniMessage events`() {
        val command = "/eclean entity COW world' weird"
        val linked = miniMessage.deserialize(commandLink("<white>Cow</white>", command, "<gray>View 'world'</gray>"))
        assertEquals(command, linked.clickEvent()?.value())
        assertEquals("Cow", PlainTextComponentSerializer.plainText().serialize(linked))
    }

    @Test fun `read only language validation does not create files or publish state`() {
        val dir = Files.createTempDirectory("eclean-language-inspect")
        val manager = LanguageManager(dir)
        manager.prepare("en_us", persistMissing = false)
        assertFalse(Files.exists(dir.resolve("lang")))
        assertEquals("zh_cn", manager.currentLanguage)
    }

    @Test fun `old custom placeholder contracts fall back per key without discarding custom translations`() {
        val dir = Files.createTempDirectory("eclean-language-contract")
        Files.createDirectories(dir.resolve("lang"))
        Files.writeString(dir.resolve("lang/en_us.yml"), "command.invalid.entity_type: 'invalid'\ncommand.no_permission: 'custom denial'\n")
        val warnings = mutableListOf<String>()
        val manager = LanguageManager(dir, logger = { warnings += it })
        manager.load("en_us")
        assertEquals("custom denial", manager["command.no_permission"])
        assertTrue(manager["command.invalid.entity_type", "type" to "bad"].contains("bad"))
        assertEquals(1, warnings.size)
    }

    @Test fun `old default menu and countdown wording upgrade while custom messages remain intact`() {
        val dir = Files.createTempDirectory("eclean-default-message-upgrade")
        Files.createDirectories(dir.resolve("lang"))
        val custom = "<aqua>Our server starts cleanup soon</aqua>"
        Files.writeString(dir.resolve("lang/en_us.yml"),
            "menu.trashcan.title: '<gold>Shared trash · Entries expire · Lost on restart</gold>'\n" +
            "cleanup.countdown.0: '<white>Cleaning in progress</white>'\n" +
            "cleanup.countdown.10: '$custom'\n")
        val manager = LanguageManager(dir)
        manager.load("en_us")
        val bundled = manager.bundledSnapshot("en_us").templates
        assertEquals(bundled["menu.trashcan.title"], manager["menu.trashcan.title"])
        assertEquals(bundled["cleanup.countdown.0"], manager["cleanup.countdown.0"])
        assertEquals(custom, manager["cleanup.countdown.10"])
    }

    @Test fun `previous defaults in both languages upgrade on reload without rewriting custom language files`() {
        for (language in listOf("zh_cn", "en_us")) {
            val dir = Files.createTempDirectory("eclean-$language-wording-upgrade")
            Files.createDirectories(dir.resolve("lang"))
            val previous = javaClass.getResourceAsStream("/lang/previous/$language.yml")!!
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val custom = "<green>Custom reload notice</green>"
            val target = dir.resolve("lang/$language.yml")
            val defaults = org.yaml.snakeyaml.Yaml().load<Map<String, String>>(previous)
            Files.writeString(target, org.yaml.snakeyaml.Yaml().dump(defaults + ("command.reload_done" to custom)))
            val original = Files.readString(target)
            val manager = LanguageManager(dir)
            manager.load(language)
            val bundled = manager.bundledSnapshot(language).templates
            assertTrue(defaults.isNotEmpty())
            defaults.keys.filter { it != "command.reload_done" }.forEach { key ->
                assertEquals(bundled.getValue(key), manager[key], "$language: $key")
            }
            assertEquals(custom, manager["command.reload_done"])
            assertEquals(original, Files.readString(target))
            manager.reload(language)
            assertEquals(bundled["command.config.semantics"], manager["command.config.semantics"])
            assertEquals(custom, manager["command.reload_done"])
        }
    }

    @Test fun `localized command help advertises the same commands arguments and switches`() {
        val manager = LanguageManager(java.nio.file.Path.of("."))
        val en = manager.bundledSnapshot("en_us").templates
        val zh = manager.bundledSnapshot("zh_cn").templates
        val translatedArguments = mapOf(
            "世界名" to "world", "实体类型" to "type", "最小数量" to "min-count",
            "区块 X" to "chunk-x", "区块 Z" to "chunk-z", "数量" to "amount",
        )
        val commands = Regex("<green>(/eclean.*?)</green>")
        val arguments = Regex("\\[([^]]+)]|<([^>]+)>")
        val switches = Regex("--[a-z]+(?:-[a-z]+)*")
        fun commandSignatures(template: String): List<String> = commands.findAll(template).map { command ->
            arguments.replace(command.groupValues[1]) { argument ->
                val name = argument.groups[1]?.value ?: argument.groupValues[2]
                val normalized = translatedArguments[name] ?: name
                if (argument.value.startsWith("[")) "[$normalized]" else "<$normalized>"
            }
        }.toList()
        en.keys.filter { it.startsWith("command.usage.") }.forEach { key ->
            assertTrue(commandSignatures(en.getValue(key)).isNotEmpty(), key)
            assertEquals(commandSignatures(en.getValue(key)), commandSignatures(zh.getValue(key)), key)
            assertEquals(
                switches.findAll(en.getValue(key)).map { it.value }.toSet(),
                switches.findAll(zh.getValue(key)).map { it.value }.toSet(), key,
            )
        }
    }

}
