package top.e404.eclean.update

import java.util.concurrent.TimeUnit

data class AvailableUpdate(val latest: String, val current: String, val url: String)

sealed interface UpdateFailure {
    data object RateLimited : UpdateFailure
    data class Other(val reason: String) : UpdateFailure
}

/** Runs on a loader-provided async timer; a generation token discards results after shutdown. */
class UpdateChecker(
    private val currentVersion: () -> String,
    private val onAvailable: (AvailableUpdate) -> Unit,
    private val onFailure: (UpdateFailure) -> Unit,
    private val feed: ReleaseFeed = GitHubReleaseFeed(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var generation = 0L
    private var lastNotified: String? = null
    private var lastFailureAt: Long? = null

    @Synchronized fun start(): Long = ++generation

    @Synchronized fun stop() { generation++ }

    fun check(token: Long) {
        if (token != generation) return
        try {
            val current = currentVersion()
            val latest = newerRelease(current, feed.fetch("EClean-Modern/$current")) ?: return
            synchronized(this) {
                if (token != generation || lastNotified == latest.tag) return
                onAvailable(AvailableUpdate(latest.tag, current, GitHubReleaseFeed.RELEASE_URL))
                lastNotified = latest.tag
            }
        } catch (failure: Exception) {
            synchronized(this) {
                val now = nowMillis()
                if (token != generation || lastFailureAt?.let { now - it < FAILURE_INTERVAL_MILLIS } == true) return
                lastFailureAt = now
                onFailure(when (failure) {
                    is ReleaseRateLimitedException -> UpdateFailure.RateLimited
                    else -> UpdateFailure.Other(failure.message ?: failure.javaClass.simpleName)
                })
            }
        }
    }

    companion object {
        private val FAILURE_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(1)
    }
}
