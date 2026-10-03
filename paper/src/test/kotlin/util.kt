import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockbukkit.mockbukkit.world.WorldMock
import org.meowcat.eclean.EClean
import org.meowcat.eclean.config.Config
import org.meowcat.eclean.config.ConfigBundle
import org.meowcat.eclean.config.model.ChunkDensityConfig
import org.meowcat.eclean.config.model.CleanupConfig
import org.meowcat.eclean.config.model.DropConfig
import org.meowcat.eclean.config.model.GlobalConfig
import org.meowcat.eclean.config.model.LivingConfig
import org.meowcat.eclean.config.model.TrashcanConfig

lateinit var server: ServerMock
lateinit var plugin: EClean
lateinit var world: WorldMock
lateinit var player: PlayerMock

fun WorldMock.spawnEntities(
    location: Location,
    type: EntityType,
    count: Int,
    edit: (index: Int, spawned: Entity) -> Unit = { _, _ -> }
) = (0 until count).map { index ->
    val spawned = spawnEntity(location, type)
    edit(index, spawned)
    spawned
}

fun WorldMock.dropItems(
    location: Location,
    count: Int,
    generator: (index: Int) -> ItemStack
) = (0 until count).map { index ->
    val spawned = dropItem(location, generator(index))
    spawned
}

private val serializer = LegacyComponentSerializer.builder().build()
private val colorRegex = Regex("§[\\da-fk-or]")
val consoleOut
    get() = buildString {
        while (true) {
            val component = server.consoleSender.nextComponentMessage() ?: break
            appendLine(
                serializer.serialize(component)
                    .replace(colorRegex, "")
                    .replace("[ECleanDebug]", "[DEBUG]")
                    .replace("[EClean]", "[INFO ]")
            )
        }
    }

val enableDebug = System.getProperty("eclean.debug") != null

fun updateConfig(transform: (ConfigBundle) -> ConfigBundle) {
    Config.update(transform)
}

fun updateDropConfig(transform: (DropConfig) -> DropConfig) {
    updateConfig { it.copy(drop = transform(it.drop)) }
}

fun updateLivingConfig(transform: (LivingConfig) -> LivingConfig) {
    updateConfig { it.copy(living = transform(it.living)) }
}

fun updateChunkDensityConfig(transform: (ChunkDensityConfig) -> ChunkDensityConfig) {
    updateConfig { it.copy(chunkDensity = transform(it.chunkDensity)) }
}

fun updateTrashcanConfig(transform: (TrashcanConfig) -> TrashcanConfig) {
    updateConfig { it.copy(trashcan = transform(it.trashcan)) }
}

fun setupMockBukkit() {
    if (::server.isInitialized) return
    EClean.unit = true
    server = MockBukkit.mock()
    plugin = MockBukkit.load(EClean::class.java)
    world = server.addSimpleWorld("world")
    player = server.addPlayer("mock")
    consoleOut
}

fun removeNonPlayerEntities() {
    world.entities.filterNot { it is Player }.forEach(Entity::remove)
}

fun resetConfig() {
    removeNonPlayerEntities()
    Config.replaceForTest(
        ConfigBundle(
            global = GlobalConfig(
                debug = enableDebug,
                updateCheck = false,
            ),
            cleanup = CleanupConfig(
                intervalSeconds = Long.MAX_VALUE,
                cleanWhenNoPlayers = true,
                broadcastWhenNoPlayers = true,
            ),
            living = LivingConfig(enabled = false),
            drop = DropConfig(enabled = false),
            chunkDensity = ChunkDensityConfig(enabled = false),
            trashcan = TrashcanConfig(),
        )
    )
    // 清空控制台输出
    consoleOut
}
