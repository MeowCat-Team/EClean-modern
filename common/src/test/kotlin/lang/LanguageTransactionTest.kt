package lang

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*
import top.e404.eclean.lang.LanguageManager
import top.e404.eclean.lang.LegacyLangMigrator

class LanguageTransactionTest {
    @TempDir lateinit var directory: Path

    @Test fun `invalid candidate does not change active language or user file`() {
        val manager = LanguageManager(directory)
        manager.load("en_us")
        val previous = manager["command.no_permission"]
        val target = directory.resolve("lang/zh_cn.yml")
        Files.writeString(target, "prefix: [broken")
        assertFails { manager.load("zh_cn") }
        assertEquals("en_us", manager.currentLanguage)
        assertEquals(previous, manager["command.no_permission"])
        assertEquals("prefix: [broken", Files.readString(target))
    }

    @Test fun `candidate language is not visible until publication`() {
        val manager = LanguageManager(directory)
        manager.load("en_us")
        val candidate = manager.prepare("zh_cn")
        assertEquals("en_us", manager.currentLanguage)
        manager.publish(candidate)
        assertEquals("zh_cn", manager.currentLanguage)
    }

    @Test fun `partial custom language falls back per key and legacy values are preserved`() {
        Files.writeString(directory.resolve("lang.yml"), "prefix: '&aCustom \"quote\" C:\\world'")
        val manager = LanguageManager(directory)
        manager.load("en_us")
        assertTrue(manager["prefix"].contains("Custom \"quote\" C:\\world"))
        assertTrue(manager["prefix"].startsWith("<green>"))
        assertNotEquals("command.no_permission", manager["command.no_permission"])
        assertTrue(Files.exists(directory.resolve("lang.yml")))
    }

    @Test fun `duplicate keys and path traversal are rejected`() {
        val manager = LanguageManager(directory)
        assertFails { manager.prepare("../outside") }
        assertFails { manager.flattenToMap("prefix: one\nprefix: two") }
        assertFails { manager.flattenToMap("command.key: one\ncommand:\n  key: two") }
    }

    @Test fun `legacy conversion retains a parseable permanent backup`() {
        val file = directory.resolve("lang.yml")
        val source = "prefix: '&aQuoted \"text\" C:\\world'\nnested:\n  text: '&bHello'"
        Files.writeString(file, source)
        assertTrue(LegacyLangMigrator.migrateIfNeeded(file, source))
        val parsed = LanguageManager(directory).flattenToMap(Files.readString(file))
        assertEquals("<aqua>Hello</aqua>", parsed["nested.text"])
        val backup = directory.toFile().listFiles()!!.single { it.name.endsWith(".bak") }
        assertEquals(source, backup.readText())
    }
}
