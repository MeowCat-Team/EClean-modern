package lang

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.meowcat.eclean.lang.LanguageManager
import java.nio.file.Files

class LanguageManagerTest {

    @Test
    fun `loads bundled language files into cache`() {
        val dir = Files.createTempDirectory("eclean-lang-test")
        try {
            val manager = LanguageManager(dir)
            manager.load("zh_cn")

            val noPermission = manager["command.no_permission"]
            assertFalse(noPermission.isBlank())
            assertFalse(noPermission.startsWith("command."))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `missing key falls back to the key itself`() {
        val dir = Files.createTempDirectory("eclean-lang-test")
        try {
            val manager = LanguageManager(dir)
            manager.load("en_us")
            assertTrue(manager["command.does_not_exist"] == "command.does_not_exist")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}