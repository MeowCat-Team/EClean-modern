package config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import top.e404.eclean.config.*
import top.e404.eclean.config.model.ConfigProfile

class ConfigFailureSafetyTest {
    private val directory: Path = Files.createTempDirectory("eclean-config-safety")
    private fun loader() = ConfigLoader(directory = { directory.toFile() }, resource = { javaClass.classLoader.getResourceAsStream(it) })

    @Test fun `diagnostic profile read cannot migrate legacy files`() {
        val root = "debug: true\nlanguage: en_us"
        Files.writeString(directory.resolve("config.yml"), root)
        Files.writeString(directory.resolve("drop.yml"), "enabled: false")
        assertFails { loader().readProfile(migrate = false) }
        assertEquals(root, Files.readString(directory.resolve("config.yml")))
        assertEquals(setOf("config.yml", "drop.yml"), directory.toFile().listFiles()!!.map { it.name }.toSet())
    }

    @Test fun `both bundled profiles pass the same validation as user files`() {
        val loader = loader()
        loader.initializeFreshInstall()
        loader.loadAll(ConfigProfile.NORMAL)
        loader.ensureDefaults(ConfigProfile.DEV)
        loader.loadAll(ConfigProfile.DEV)
        assertNull(loader.loadNormalFromText("cleanup:\n  cron: ''").cleanup.cron)
    }

    @Test fun `invalid YAML and unknown fields never become enabled defaults`() {
        listOf("drop: [", "drop:\n  enabled: false\n  protectLroe: true", "", "drop:\n  disabledWorlds: ['[']").forEach {
            assertFailsWith<IllegalArgumentException> { loader().loadNormalFromText(it) }
        }
    }

    @Test fun `failed candidate preserves the selector and existing files`() {
        val loader = loader()
        loader.initializeFreshInstall()
        loader.ensureDefaults(ConfigProfile.DEV)
        val selector = Files.readString(directory.resolve("config.yml"))
        val drop = directory.resolve("config/dev/drop.yml")
        Files.writeString(drop, "enabled: false\nunknownSetting: true")
        assertFailsWith<IllegalArgumentException> { loader.loadAll(ConfigProfile.DEV) }
        assertEquals(selector, Files.readString(directory.resolve("config.yml")))
        assertEquals("enabled: false\nunknownSetting: true", Files.readString(drop))
    }

    @Test fun `missing existing safety file is not recreated on restart or profile activation`() {
        val loader = loader()
        loader.initializeFreshInstall()
        val normal = directory.resolve("config/normal/config.yml")
        Files.delete(normal)
        loader.initializeFreshInstall()
        assertFails { loader.loadAll(ConfigProfile.NORMAL) }
        assertFails { loader.ensureDefaults(ConfigProfile.NORMAL) }
        assertFalse(Files.exists(normal))
    }

    @Test fun `unknown profile is rejected without changing selector`() {
        listOf("profile: prodution", "{}").forEach { content ->
            Files.writeString(directory.resolve("config.yml"), content)
            assertFailsWith<IllegalArgumentException> { loader().readProfile() }
            assertEquals(content, Files.readString(directory.resolve("config.yml")))
        }
    }

    @Test fun `legacy migration retains safety rules and original files`() {
        val root = "debug: true\nlanguage: en_us"
        Files.writeString(directory.resolve("config.yml"), root)
        Files.writeString(directory.resolve("drop.yml"), "enabled: false\nprotectLore: true\ndisabledWorlds: ['safe_.*']")
        Files.writeString(directory.resolve("per-world.yml"), "worlds:\n  creative:\n    enabled: false")
        val loader = loader()
        assertEquals(ConfigProfile.NORMAL, loader.readProfile())
        val bundle = loader.loadAll(ConfigProfile.NORMAL)
        assertFalse(bundle.drop.enabled)
        assertTrue(bundle.drop.protectLore)
        assertEquals("safe_.*", bundle.drop.disabledWorlds.single().pattern)
        assertEquals(false, bundle.perWorld.worlds["creative"]?.enabled)
        assertFalse(bundle.living.enabled, "Absent legacy sections must not enable deletion")
        assertTrue(Files.exists(directory.resolve("drop.yml")))
        val backup = directory.toFile().listFiles()!!.single { it.name.startsWith("config-backup-") }
        assertEquals(root, backup.resolve("config.yml").readText())
        assertEquals(ConfigProfile.NORMAL, loader.readProfile(), "Retained originals must not trigger migration twice")
    }

    @Test fun `invalid legacy configuration remains untouched`() {
        Files.writeString(directory.resolve("config.yml"), "debug: false")
        Files.writeString(directory.resolve("drop.yml"), "disabledWorlds: ['[']")
        assertFails { loader().readProfile() }
        assertEquals("debug: false", Files.readString(directory.resolve("config.yml")))
        assertEquals("disabledWorlds: ['[']", Files.readString(directory.resolve("drop.yml")))
        assertFalse(Files.exists(directory.resolve("config/normal/config.yml")))
    }

    @Test fun `normal profile exposes optional integration controls`() {
        val bundle = loader().loadNormalFromText("advanced:\n  bStats:\n    enabled: false\n  papi:\n    enabled: false")
        assertFalse(bundle.advanced.bStats.enabled)
        assertFalse(bundle.advanced.papi.enabled)
    }

    @Test fun `provisioning a profile never writes its selector ahead of validation`() {
        loader().ensureDefaults(ConfigProfile.DEV)
        assertFalse(Files.exists(directory.resolve("config.yml")))
    }
}
