package update

import top.e404.eclean.update.AvailableUpdate
import top.e404.eclean.update.ReleaseCandidate
import top.e404.eclean.update.ReleaseFeed
import top.e404.eclean.update.ReleaseRateLimitedException
import top.e404.eclean.update.UpdateChecker
import top.e404.eclean.update.UpdateFailure
import top.e404.eclean.update.parseReleaseCandidates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UpdateCheckerTest {
    @Test fun `release feed parses valid entries and ignores incomplete entries`() {
        val releases = parseReleaseCandidates("""[
            {"tag_name":"v1.1.0","draft":false,"prerelease":false},
            {"tag_name":"v2.0.0","draft":true},
            {"name":"missing tag"}, null, {"tag_name":42}
        ]""")
        assertEquals(listOf(ReleaseCandidate("v1.1.0"), ReleaseCandidate("v2.0.0", draft = true)), releases)
        assertFailsWith<IllegalStateException> { parseReleaseCandidates("{}") }
    }

    @Test fun `checker deduplicates notices and ignores stopped schedules`() {
        val notices = mutableListOf<AvailableUpdate>()
        var releases = listOf(ReleaseCandidate("v1.1.0"))
        var fetches = 0
        val checker = UpdateChecker(
            currentVersion = { "1.0.0" },
            onAvailable = notices::add,
            onFailure = { error("Unexpected failure: $it") },
            feed = ReleaseFeed { agent ->
                assertEquals("EClean-Modern/1.0.0", agent)
                fetches++
                releases
            },
        )
        val first = checker.start()
        checker.check(first)
        checker.check(first)
        assertEquals(listOf("v1.1.0"), notices.map { it.latest })
        checker.stop()
        checker.check(first)
        assertEquals(2, fetches)
        releases = listOf(ReleaseCandidate("v1.2.0"))
        checker.check(checker.start())
        assertEquals(listOf("v1.1.0", "v1.2.0"), notices.map { it.latest })
    }

    @Test fun `failures are throttled for an hour and rate limits retain their type`() {
        val failures = mutableListOf<UpdateFailure>()
        var now = 0L
        val checker = UpdateChecker(
            currentVersion = { "1.0.0" },
            onAvailable = { error("Unexpected update") },
            onFailure = failures::add,
            feed = ReleaseFeed { throw ReleaseRateLimitedException() },
            nowMillis = { now },
        )
        val token = checker.start()
        checker.check(token)
        checker.check(token)
        assertEquals(listOf<UpdateFailure>(UpdateFailure.RateLimited), failures)
        now = 3_600_000L
        checker.check(token)
        assertEquals(listOf<UpdateFailure>(UpdateFailure.RateLimited, UpdateFailure.RateLimited), failures)
    }
}
