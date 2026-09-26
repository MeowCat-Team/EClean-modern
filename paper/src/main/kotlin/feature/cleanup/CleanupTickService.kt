package top.e404.eclean.feature.cleanup

import top.e404.eclean.app.MessageService
import top.e404.eclean.common.api.ServerInfo
import top.e404.eclean.config.Config
import top.e404.eclean.config.ConfigBundle
import top.e404.eclean.platform.Schedulers
import top.e404.eclean.service.StatusSnapshotService
import java.time.ZonedDateTime

/** Compatibility facade for Paper call sites; scheduling policy lives in common. */
class CleanupTickService(
    @Suppress("UNUSED_PARAMETER") messages: MessageService,
    private val coordinator: CleanupCoordinator,
    private val announcements: CleanupAnnouncementService,
    private val snapshots: StatusSnapshotService,
    private val serverInfo: ServerInfo,
    private val now: () -> ZonedDateTime = ZonedDateTime::now,
) {
    private val ticker: CleanupTicker by lazy {
        CleanupTicker(
            scheduler = Schedulers.backend(),
            serverInfo = serverInfo,
            snapshots = snapshots,
            config = { Config.current },
            onDue = coordinator::cleanScheduled,
            onCountdown = announcements::announceCountdown,
            now = now,
        )
    }

    val elapsedSeconds: Long get() = snapshots.current().cleanup.elapsedSeconds
    fun start(bundle: ConfigBundle = Config.current) { ticker.start(bundle) }
    fun reset() { ticker.reset() }
    fun stop() { ticker.stop() }
}
